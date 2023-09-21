package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.CoreModule

/** Externally-visible properties of the ALU.
  */
object ALU {
  /** The size of the ALU's internal functional unit's addresses */
  val SZ_ALU_FN = 4

  /** Unknown ALU function */
  def FN_X = BitPat.dontCare(SZ_ALU_FN)
  // This funky syntax creates a bit pattern of specified length with that value
  def FN_ADD = BitPat(0.U(SZ_ALU_FN.W))
  def FN_RED_ADD = BitPat(1.U(SZ_ALU_FN.W))
}

/** Implementation of an ALU.
  * @param p Implicit parameter passed by the build system.
  */
class ALU(val xLen: Int)(val batchSize: Int) extends Module {
  import ALU._ // Import ALU object, so we do not have to fully-qualify names
  val io = IO(new Bundle {
    val fn = Input(Bits(SZ_ALU_FN.W))
    // The two register content values passed over the RoCCCommand are xLen wide
    val in1 = Input(Vec(batchSize, UInt(xLen.W)))
    val in2 = Input(Vec(batchSize, UInt(xLen.W)))
    val out = Output(Valid(Vec(batchSize, UInt(xLen.W))))
    val cout = Output(UInt(xLen.W))
    val execute = Input(Bool())
  })

  val data_out = WireInit(VecInit.fill(batchSize)(0.U(xLen.W)))
  io.out.bits := data_out

  io.cout := 0.U
  io.out.valid := false.B

  switch(io.fn){
    is(0.U){
      // ADD/SUB
      // This zip->map chain feels a little gross, but it does what we want.
      data_out := io.in1.zip(io.in2).map{case (l, r) => l+r}
    }
    is(1.U){
      // +_REDUCE INT
      data_out(0) := io.in1.reduce(_ + _)
      // .reduce could be replaced by reduceTree
    }
  }

  when(io.execute) {
    // Written this way so that variable-latency operations can signal properly
    // Addition can be done in one cycle though, so it is a moto point here.
    io.out.valid := true.B
  }
}
