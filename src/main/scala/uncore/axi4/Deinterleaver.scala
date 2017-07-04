// See LICENSE.SiFive for license details.

package uncore.axi4

import Chisel._
import chisel3.internal.sourceinfo.SourceInfo
import chisel3.util.IrrevocableIO
import config._
import diplomacy._
import scala.math.{min,max}
import uncore.tilelink2.{leftOR, rightOR, UIntToOH1, OH1ToOH}

class AXI4Deinterleaver(maxReadBytes: Int)(implicit p: Parameters) extends LazyModule
{
  require (maxReadBytes >= 1 && isPow2(maxReadBytes))

  val node = AXI4AdapterNode(
    masterFn = { mp => mp },
    slaveFn  = { sp => sp.copy(slaves = sp.slaves.map(s => s.copy(
      supportsRead = s.supportsRead.intersect(TransferSizes(1, maxReadBytes)),
      interleavedId = Some(0))))
  })

  lazy val module = new LazyModuleImp(this) {
    val io = new Bundle {
      val in  = node.bundleIn
      val out = node.bundleOut
    }

    ((io.in zip io.out) zip (node.edgesIn zip node.edgesOut)) foreach { case ((in, out), (edgeIn, edgeOut)) =>
      val endId = edgeOut.master.endId
      val beatBytes = edgeOut.slave.beatBytes
      val beats = (maxReadBytes+beatBytes-1) / beatBytes

      // This adapter leaves the control + write paths completely untouched
      out.ar <> in.ar
      out.aw <> in.aw
      out.w <> in.w
      in.b <> out.b

      if (beats <= 1) {
        // Nothing to do if only single-beat R
        in.r <> out.r
      } else {
        // Queues to buffer R responses
        val qs = Seq.tabulate(endId) { i =>
          val depth = edgeOut.master.masters.find(_.id.contains(i)).flatMap(_.maxFlight).getOrElse(0)
          if (depth > 0) {
            Module(new Queue(out.r.bits, beats)).io
          } else {
            Wire(new QueueIO(out.r.bits, beats))
          }
        }

        // Which ID is being enqueued and dequeued?
        val locked = RegInit(Bool(false))
        val deq_id = Reg(UInt(width=log2Up(endId)))
        val enq_id = out.r.bits.id
        val deq_OH = UIntToOH(deq_id, endId)
        val enq_OH = UIntToOH(enq_id, endId)

        // Track the number of completely received bursts per FIFO id
        val pending = Cat(Seq.tabulate(endId) { i =>
          val depth = edgeOut.master.masters.find(_.id.contains(i)).flatMap(_.maxFlight).getOrElse(0)
          if (depth == 0) {
            Bool(false)
          } else {
            val count = RegInit(UInt(0, width=log2Ceil(beats+1)))
            val next = Wire(count)
            val inc = enq_OH(i) && out.r.fire() && out.r.bits.last
            val dec = deq_OH(i) && in.r.fire() && in.r.bits.last
            next := count + inc.asUInt - dec.asUInt
            count := next
            // Bounds checking
            assert (!dec || count =/= UInt(0))
            assert (!inc || count =/= UInt(beats))
            next =/= UInt(0)
          }
        }.reverse)

        // Select which Q will we start sending next cycle
        val winner  = pending & ~(leftOR(pending) << 1)
        when (!locked || (in.r.fire() && in.r.bits.last)) {
          locked := pending.orR
          deq_id := OHToUInt(winner)
        }

        // Transmit the selected burst to inner
        in.r.valid := locked
        in.r.bits  := Vec(qs.map(_.deq.bits))(deq_id)
        (deq_OH.toBools zip qs) foreach { case (s, q) =>
          q.deq.ready := s && in.r.fire()
        }

        // Feed response into matching Q
        out.r.ready := Vec(qs.map(_.enq.ready))(enq_id)
        (enq_OH.toBools zip qs) foreach { case (s, q) =>
          q.enq.valid := s && out.r.valid
          q.enq.bits := out.r.bits
        }
      }
    }
  }
}

object AXI4Deinterleaver
{
  // applied to the AXI4 source node; y.node := AXI4Deinterleaver()(x.node)
  def apply(maxReadBytes: Int)(x: AXI4OutwardNode)(implicit p: Parameters, sourceInfo: SourceInfo): AXI4OutwardNode = {
    val deinterleaver = LazyModule(new AXI4Deinterleaver(maxReadBytes))
    deinterleaver.node := x
    deinterleaver.node
  }
}