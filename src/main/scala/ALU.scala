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
  def FN_VEC_ADD = BitPat(4.U(SZ_ALU_FN.W))
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
    val out1 = Output(Valid(UInt(xLen.W)))
    val out2 = Output(UInt(xLen.W))
    val out3 = Output(UInt(xLen.W))
    val out4 = Output(UInt(xLen.W))
    val out5 = Output(UInt(xLen.W))
    val out6 = Output(UInt(xLen.W))
    val out7 = Output(UInt(xLen.W))
    val out8 = Output(UInt(xLen.W))
    val cout = Output(UInt(xLen.W))
    val execute = Input(Bool())
    val vec_first_round = Input(Bool())
  })

  io.cout := 0.U
  io.out1.valid := false.B

  val data_out1 = RegInit(0.U(xLen.W))
  val data_out2 = RegInit(0.U(xLen.W))
  val data_out3 = RegInit(0.U(xLen.W))
  val data_out4 = RegInit(0.U(xLen.W))
  val data_out5 = RegInit(0.U(xLen.W))
  val data_out6 = RegInit(0.U(xLen.W))
  val data_out7 = RegInit(0.U(xLen.W))
  val data_out8 = RegInit(0.U(xLen.W))

  val data_in_buffer1 = RegInit(0.U(xLen.W))
  val data_in_buffer2 = RegInit(0.U(xLen.W))
  val data_in_buffer3 = RegInit(0.U(xLen.W))
  val data_in_buffer4 = RegInit(0.U(xLen.W))
  val data_in_buffer5 = RegInit(0.U(xLen.W))
  val data_in_buffer6 = RegInit(0.U(xLen.W))
  val data_in_buffer7 = RegInit(0.U(xLen.W))
  val data_in_buffer8 = RegInit(0.U(xLen.W))

  switch(io.fn){
    is(0.U){
      // ADD/SUB
      data_out1 := io.in1 + io.in2
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
      data_out1 := l3
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
      data_out1 := l3
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
      data_out1 := l3
    }
    is(4.U){
      val l1 = Wire(Bits(xLen.W))
      val l2 = Wire(Bits(xLen.W))
      val l3 = Wire(Bits(xLen.W))
      val l4 = Wire(Bits(xLen.W))
      val l5 = Wire(Bits(xLen.W))
      val l6 = Wire(Bits(xLen.W))
      val l7 = Wire(Bits(xLen.W))
      val l8 = Wire(Bits(xLen.W))

      when(!io.vec_first_round){
        data_in_buffer1 := io.in1
        data_in_buffer2 := io.in2
        data_in_buffer3 := io.in3
        data_in_buffer4 := io.in4
        data_in_buffer5 := io.in5
        data_in_buffer6 := io.in6
        data_in_buffer7 := io.in7
        data_in_buffer8 := io.in8
      }

      l1 := io.in1 + data_in_buffer1
      l2 := io.in2 + data_in_buffer2
      l3 := io.in3 + data_in_buffer3
      l4 := io.in4 + data_in_buffer4
      l5 := io.in5 + data_in_buffer5
      l6 := io.in6 + data_in_buffer6
      l7 := io.in7 + data_in_buffer7
      l8 := io.in8 + data_in_buffer8
      data_out1 := l1
      data_out2 := l2
      data_out3 := l3
      data_out4 := l4
      data_out5 := l5
      data_out6 := l6
      data_out7 := l7
      data_out8 := l8
    }
  }

  io.out1.bits := data_out1
  io.out2 := data_out2
  io.out3 := data_out3
  io.out4 := data_out4
  io.out5 := data_out5
  io.out6 := data_out6
  io.out7 := data_out7
  io.out8 := data_out8

  when(io.execute) {
    // Written this way so that variable-latency operations can signal properly
    // Addition can be done in one cycle though, so it is a moto point here.
    io.out1.valid := true.B
  }
}
