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
  def FN_RED_OR = BitPat(2.U(SZ_ALU_FN.W))
  def FN_RED_AND = BitPat(3.U(SZ_ALU_FN.W))
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
    val in3 = Input(UInt(xLen.W))
    val in4 = Input(UInt(xLen.W))
    val in5 = Input(UInt(xLen.W))
    val in6 = Input(UInt(xLen.W))
    val in7 = Input(UInt(xLen.W))
    val in8 = Input(UInt(xLen.W))
    val out = Output(Valid(UInt(xLen.W)))
    val cout = Output(UInt(xLen.W))
    val execute = Input(Bool())
  })

  io.cout := 0.U
  io.out.valid := false.B

  val data_out = RegInit(0.U(xLen.W))
  switch(io.fn){
    is(0.U){
      // ADD/SUB
      data_out := io.in1 + io.in2
    }
    is(1.U){
      val l11 = Wire(Bits(xLen.W))
      val l12 = Wire(Bits(xLen.W))
      val l13 = Wire(Bits(xLen.W))
      val l14 = Wire(Bits(xLen.W))
      val l21 = Wire(Bits(xLen.W))
      val l22 = Wire(Bits(xLen.W))
      val l3 = Wire(Bits(xLen.W))

      l11 := io.in1 + io.in2
      l12 := io.in3 + io.in4
      l13 := io.in5 + io.in6
      l14 := io.in7 + io.in8
      l21 := l11 + l12
      l22 := l13 + l14
      l3 := l21 + l22
      data_out := l3
    }
    is(2.U){
      val l11 = Wire(Bits(xLen.W))
      val l12 = Wire(Bits(xLen.W))
      val l13 = Wire(Bits(xLen.W))
      val l14 = Wire(Bits(xLen.W))
      val l21 = Wire(Bits(xLen.W))
      val l22 = Wire(Bits(xLen.W))
      val l3 = Wire(Bits(xLen.W))

      l11 := io.in1 | io.in2
      l12 := io.in3 | io.in4
      l13 := io.in5 | io.in6
      l14 := io.in7 | io.in8
      l21 := l11 | l12
      l22 := l13 | l14
      l3 := l21 | l22
      data_out := l3
    }
    is(3.U){
      val l11 = Wire(Bits(xLen.W))
      val l12 = Wire(Bits(xLen.W))
      val l13 = Wire(Bits(xLen.W))
      val l14 = Wire(Bits(xLen.W))
      val l21 = Wire(Bits(xLen.W))
      val l22 = Wire(Bits(xLen.W))
      val l3 = Wire(Bits(xLen.W))

      l11 := io.in1 & io.in2
      l12 := io.in3 & io.in4
      l13 := io.in5 & io.in6
      l14 := io.in7 & io.in8
      l21 := l11 & l12
      l22 := l13 & l14
      l3 := l21 & l22
      data_out := l3
    }
  }

  io.out.bits := data_out

  when(io.execute) {
    // Written this way so that variable-latency operations can signal properly
    // Addition can be done in one cycle though, so it is a moto point here.
    io.out.valid := true.B
  }
}
