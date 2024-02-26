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
  def FN_SCAN_ADD = BitPat(2.U(SZ_ALU_FN.W))
  def FN_SUB = BitPat(3.U(SZ_ALU_FN.W))
  def FN_MUL = BitPat(4.U(SZ_ALU_FN.W))
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
    val out = Output(Vec(batchSize, UInt(xLen.W)))
    val cout = Output(UInt(xLen.W))
    val execute = Input(Bool())
    val accelIdle = Input(Bool())
  })

  // FIXME: This should be RegInit(Bits(xLen.W))?
  val workingSpace = withReset(io.accelIdle) {
    RegInit(VecInit.fill(batchSize)(0.U(xLen.W)))
  }
  io.out := workingSpace
  val lastBatchResult = workingSpace(0)

  val identity = withReset(io.accelIdle) {
    RegInit(0.U)
  }
  io.cout := 0.U

  when(io.execute) {
    switch(io.fn) {
      is(0.U) {
        // ADD/SUB
        workingSpace := (io.in1, io.in2).zipped.map(_ + _)
      }
      is(1.U) {
        // +_REDUCE INT
        lastBatchResult := lastBatchResult + io.in1.reduce(_ + _)
        // NOTE: .reduce could be replaced by reduceTree
      }
      is(2.U) { // +_SCAN INT
        /* FIXME: Can factor out SCAN HW out and just select identity & binary operator
         * rather than the entire thing. Works because .scan()() requires identity
         * as first argument (partial evaluation). */
        val tmp = io.in1.scan(identity)(_ + _)
        workingSpace := tmp.slice(0, batchSize) // .slice(from, to) is [from, to). to is EXCLUSIVE
        identity := tmp(batchSize) // Grab the last bit, the end of the vector.
        // NOTE .scan has .scanLeft & .scanRight variants
      }
      is(3.U){
        // SUB
        workingSpace := (io.in1, io.in2).zipped.map(_ - _)
      }
      is(4.U){
        // MUL
        workingSpace := (io.in1, io.in2).zipped.map(_ * _)
      }
    }
  }
}
