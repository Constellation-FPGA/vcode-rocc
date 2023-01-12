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
}

/** Implementation of an ALU.
  * @param p Implicit parameter passed by the build system.
  */
class ALU(val xLen: Int) extends Module {
  import ALU._ // Import ALU object, so we do not have to fully-qualify names
  val io = IO(new Bundle {
    val fn = Input(Bits(SZ_ALU_FN.W))
    // The two register content values passed over the RoCCCommand are xLen wide
    val in1 = Input(UInt(xLen.W))
    val in2 = Input(UInt(xLen.W))
    val out = Output(Valid(UInt(xLen.W)))
    val cout = Output(UInt(xLen.W))
    val execute = Input(Bool())
  })

  io.out.valid := false.B
  io.cout := 0.U

  val data_out = RegInit(0.U(xLen.W))
  // ADD/SUB
  data_out := io.in1 + io.in2

  io.out.bits := data_out
  /* Update the register with the result of the ALU's computation */
  when(io.execute) {
    io.out.valid := true.B
  }
}
