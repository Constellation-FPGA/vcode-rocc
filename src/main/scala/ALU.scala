package vcoderocc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.CoreModule

/** Externally-visible properties of the ALU.
  */
object ALU {
  /** The size of the ALU's internal functional unit's addresses */
  val SZ_ALU_FN = 7

  /** Unknown ALU function */
  def FN_X = BitPat.dontCare(SZ_ALU_FN)
  // This funky syntax creates a bit pattern of specified length with that value
  def FN_ADD = BitPat(0.U(SZ_ALU_FN.W))
  def FN_RED_ADD = BitPat(1.U(SZ_ALU_FN.W))
  def FN_SCAN_ADD = BitPat(2.U(SZ_ALU_FN.W))
  def FN_SUB = BitPat(3.U(SZ_ALU_FN.W))
  def FN_MUL = BitPat(4.U(SZ_ALU_FN.W))
  def FN_DIV =  BitPat(7.U(SZ_ALU_FN.W))
  def FN_MOD =  BitPat(8.U(SZ_ALU_FN.W))
  def FN_LESS = BitPat(9.U(SZ_ALU_FN.W))
  def FN_LESS_EQUAL = BitPat(10.U(SZ_ALU_FN.W))
  def FN_GREATER = BitPat(11.U(SZ_ALU_FN.W))
  def FN_GREATER_EQUAL = BitPat(12.U(SZ_ALU_FN.W))
  def FN_EQUAL = BitPat(13.U(SZ_ALU_FN.W))
  def FN_UNEQUAL = BitPat(14.U(SZ_ALU_FN.W))
  def FN_LSHIFT = BitPat(15.U(SZ_ALU_FN.W))
  def FN_RSHIFT = BitPat(16.U(SZ_ALU_FN.W))
  def FN_NOT = BitPat(17.U(SZ_ALU_FN.W))
  def FN_AND = BitPat(18.U(SZ_ALU_FN.W))
  def FN_OR = BitPat(19.U(SZ_ALU_FN.W))
  def FN_XOR = BitPat(20.U(SZ_ALU_FN.W))
  def FN_SELECT = BitPat(21.U(SZ_ALU_FN.W))
  def FN_SCAN_MUL = BitPat(22.U(SZ_ALU_FN.W))
  def FN_SCAN_MAX = BitPat(23.U(SZ_ALU_FN.W))
  def FN_SCAN_MIN = BitPat(24.U(SZ_ALU_FN.W))
  def FN_SCAN_AND = BitPat(25.U(SZ_ALU_FN.W))
  def FN_SCAN_OR = BitPat(26.U(SZ_ALU_FN.W))
  def FN_SCAN_XOR = BitPat(27.U(SZ_ALU_FN.W))
  //def FN_RED_MUL = BitPat(28.U(SZ_ALU_FN.W))
  def FN_RED_MAX = BitPat(29.U(SZ_ALU_FN.W))
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
    val in3 = Input(UInt(xLen.W))
    val out = Output(Vec(batchSize, UInt(xLen.W)))
    val cout = Output(UInt(xLen.W))
    val execute = Input(Bool())
    val accelIdle = Input(Bool())
  })

  val selectFlagsCounter = withReset(io.accelIdle) {
    RegInit(0.U(log2Down(xLen).W))
  }
  /* Use a for-loop to dynamically index and pull out the flags provided
   * through io.in3, creating a "view/slice" of the flags. This MUST be done
   * this way because Chisel does not have a built-in function to generate
   * this kind of code.
   * When looking at the generated code on Scastie, it amounts to 3 assigns,
   * which is pretty much exactly what we want. */
  val selectFlags = WireInit(VecInit(Seq.fill(batchSize)(false.B)))
  for (i <- 0 until batchSize) {
    selectFlags(i) := io.in3(i.U + selectFlagsCounter)
  }

  // FIXME: This should be RegInit(Bits(xLen.W))?
  val workingSpace = withReset(io.accelIdle) {
    RegInit(VecInit.fill(batchSize)(0.U(xLen.W)))
  }
  io.out := workingSpace

  /*val batchCounter = withReset(io.accelIdle) {
    RegInit(0.U(log2Down(xLen).W))
  }*/

  val lastBatchResult = workingSpace(0)

  val scanPlusIdentity = withReset(io.accelIdle) {
    RegInit(0.U)
  }

  val scanMulIdentity = withReset(io.accelIdle) {
    RegInit(1.U(xLen.W))
  }

  val scanMaxIdentity = withReset(io.accelIdle) {
    RegInit((-(BigInt(1) << (xLen - 1))).S(xLen.W))
  }

  val scanMinIdentity = withReset(io.accelIdle) {
    RegInit(((BigInt(1) << (xLen - 1)) - 1).S(xLen.W))
  }

  val scanANDIdentity = withReset(io.accelIdle) {
    RegInit(1.U(xLen.W))
  }

  val scanORIdentity = withReset(io.accelIdle) {
    RegInit(0.U(xLen.W))
  }

  val scanXORIdentity = withReset(io.accelIdle) {
    RegInit(0.U(xLen.W))
  }

  val reduceMulIdentity = withReset(io.accelIdle) {
    RegInit(1.U(xLen.W))
  }

  val reduceMaxIdentity = withReset(io.accelIdle) {
    RegInit((-(BigInt(1) << (xLen - 1))).S(xLen.W))
  }

  io.cout := 0.U

  when(io.execute) {
    switch(io.fn) {
      is(0.U) {
        // ADD/SUB
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x + y }
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
        val tmp = io.in1.scan(scanPlusIdentity)(_ + _)
        workingSpace := tmp.slice(0, batchSize) // .slice(from, to) is [from, to). to is EXCLUSIVE
        scanPlusIdentity := tmp(batchSize) // Grab the last bit, the end of the vector.
        // NOTE .scan has .scanLeft & .scanRight variants
      }
      is(3.U){
        // SUB
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x - y }
      }
      is(4.U){
        // MUL
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x * y }
      }
      is(7.U){
        // DIV
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x / y }
      }
      is(8.U){
        // MOD
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x % y }
      }
      is(9.U){
        // LESS
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x < y }
      }
      is(10.U){
        // LESS OR EQUAL
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x <= y }
      }
      is(11.U){
        // GREATER
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x > y }
      }
      is(12.U){
        // GREATER OR EQUAL
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x >= y }
      }
      is(13.U){
        // EQUAL
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x === y }
      }
      is(14.U){
        // UNEQUAL
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x =/= y }
      }
      is(15.U){
        // LEFT SHIFT
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x << y(18,0) }
      }
      is(16.U){
        // RIGHT SHIFT
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x >> y(18,0) }
      }
      is(17.U){
        // NOT (bitwise for ints)
        workingSpace := io.in1.map { case (x) => ~x }
      }
      is(18.U){
        // AND (bitwise or boolean)
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x & y }
      }
      is(19.U){
        // OR (bitwise or boolean)
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x | y }
      }
      is(20.U){
        // XOR (bitwise or boolean)
        workingSpace := io.in1.zip(io.in2).map{ case (x, y) => x ^ y }
      }
      is(21.U){
        // SELECT
        workingSpace := selectFlags.lazyZip(io.in1)
          .lazyZip(io.in2)
          .toVector
          .map({ case (s, t, f) => Mux(s, t, f) })
        selectFlagsCounter := selectFlagsCounter + batchSize.U;
      }
      is(22.U){
        // *_SCAN INT
        val tmp = io.in1.scan(scanMulIdentity)(_ * _)
        workingSpace := tmp.slice(0, batchSize)
        scanMulIdentity := tmp(batchSize) 
      }
      is(23.U){
        // MAX SCAN INT
        val tmp = io.in1.map(_.asSInt).scan(scanMaxIdentity)({(x, y) => Mux(y > x, y, x)})
        workingSpace := tmp.slice(0, batchSize).map(_.asUInt)
        scanMaxIdentity := tmp(batchSize) 
      }
      is(24.U){
        // MIN SCAN INT
        val tmp = io.in1.map(_.asSInt).scan(scanMinIdentity)({(x, y) => Mux(y < x, y, x)})
        workingSpace := tmp.slice(0, batchSize).map(_.asUInt)
        scanMinIdentity := tmp(batchSize) 
      }
      is(25.U){
        // AND SCAN INT
        val tmp = io.in1.scan(scanANDIdentity)(_ & _)
        workingSpace := tmp.slice(0, batchSize)
        scanANDIdentity := tmp(batchSize) 
      }
      is(26.U){
        // OR SCAN INT
        val tmp = io.in1.scan(scanORIdentity)(_ | _)
        workingSpace := tmp.slice(0, batchSize)
        scanORIdentity := tmp(batchSize) 
      }
      is(27.U){
        // XOR SCAN INT
        val tmp = io.in1.scan(scanXORIdentity)(_ ^ _)
        workingSpace := tmp.slice(0, batchSize)
        scanXORIdentity := tmp(batchSize) 
      }
      /*is(28.U) {
        // *_REDUCE INT
        /*when(batchCounter === 0.U){
          lastBatchResult := io.in1.reduce(_ * _)(xLen-1, 0)
        } .otherwise{
          val product = io.in1.reduce(_ * _)
          lastBatchResult := (lastBatchResult * product)(xLen-1, 0)
        }
        batchCounter := batchCounter + 1.U*/
        val lastBatchResult = io.in1.fold(reduceMulIdentity)((x, y) => x * y)(xLen-1, 0)
        reduceMulIdentity := lastBatchResult
      }*/

      is(29.U) {
        // MAX_REDUCE INT
        val reduceMaximum = io.in1.map(_.asSInt).fold(reduceMaxIdentity)((x, y) => Mux(x > y, x, y))
        lastBatchResult := Mux(lastBatchResult.asSInt > reduceMaximum, lastBatchResult, reduceMaximum.asUInt)
      }
    }
  }
}
