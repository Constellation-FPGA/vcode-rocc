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
  def FN_RED_MAX = BitPat(4.U(SZ_ALU_FN.W))
  def FN_RED_MIN = BitPat(5.U(SZ_ALU_FN.W))
  def FN_VEC_ADD = BitPat(6.U(SZ_ALU_FN.W))
  def FN_SCAN_ADD = BitPat(7.U(SZ_ALU_FN.W))
}

/** Implementation of an ALU.
  * @param p Implicit parameter passed by the build system.
  */
class ALU(val xLen: Int, val batchSize: Int) extends Module {
  import ALU._ // Import ALU object, so we do not have to fully-qualify names
  val io = IO(new Bundle {
    val fn = Input(Bits(SZ_ALU_FN.W))
    // The two register content values passed over the RoCCCommand are xLen wide
    val in = Input(Vec(batchSize, UInt(xLen.W)))
    val out = Output(Vec(batchSize, Valid(UInt(xLen.W))))
    val cout = Output(UInt(xLen.W))
    val execute = Input(Bool())
    val vec_first_round = Input(Bool())
    val scan_accum = Input(UInt(xLen.W))
  })

  io.cout := 0.U
  for (i <- 0 until batchSize){
    io.out(i).valid := false.B
  }

  val data_out = RegInit(VecInit(Seq.fill(batchSize)(0.U(xLen.W))))
  val scan_tmp = Wire(Vec(batchSize + 1, UInt(xLen.W)))
  val data_in_buffer = RegInit(VecInit(Seq.fill(batchSize)(0.U(xLen.W))))

  
  scan_tmp := io.in.scan(0.U)((a: UInt, b: UInt) => (a + b))
  
  switch(io.fn){
    is(0.U){
      // ADD/SUB
      data_out(0) := io.in(0) + io.in(1)
    }
    is(1.U){
      data_out(0) := io.in.reduceTree((a: UInt, b: UInt) => (a + b))
    }
    is(2.U){
      data_out(0) := io.in.reduceTree((a: UInt, b: UInt) => (a | b))
    }
    is(3.U){
      data_out(0) := io.in.reduceTree((a: UInt, b: UInt) => (a & b))
      
    }
    is(4.U){
      data_out(0) := io.in.reduceTree((a: UInt, b: UInt) => Mux(a.asSInt > b.asSInt, a, b))
    }
    is(5.U){
      data_out(0) := io.in.reduceTree((a: UInt, b: UInt) => Mux(a.asSInt < b.asSInt, a, b))
    }
    is(6.U){

      when(!io.vec_first_round){
        for (i <- 0 until batchSize){
          data_in_buffer(i) := io.in(i)
        }
      }

      for (i <- 0 until batchSize){
        data_out(i) := io.in(i) + data_in_buffer(i)
      }

    }
    is(7.U){
      for (i <- 0 until batchSize){
        data_out(i) := io.scan_accum + scan_tmp(i + 1)
      }
    }
  }

  for (i <- 0 until batchSize){
    io.out(i).bits := data_out(i)
  }

  when(io.execute) {
    // Written this way so that variable-latency operations can signal properly
    // Addition can be done in one cycle though, so it is a moto point here.
    for (i <- 0 until batchSize){
      io.out(i).valid := true.B
    }
  }
}
