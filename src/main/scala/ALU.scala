package vcoderocc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.CoreModule
import vcoderocc.DataIO

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
  def FN_RED_MUL = BitPat(28.U(SZ_ALU_FN.W))
  def FN_RED_MAX = BitPat(29.U(SZ_ALU_FN.W))
  def FN_RED_MIN = BitPat(30.U(SZ_ALU_FN.W))
  def FN_RED_AND = BitPat(31.U(SZ_ALU_FN.W))
  def FN_RED_OR = BitPat(32.U(SZ_ALU_FN.W))
  def FN_RED_XOR = BitPat(33.U(SZ_ALU_FN.W))
}

/** Implementation of an ALU.
  * @param p Implicit parameter passed by the build system.
  */
class ALU(val xLen: Int)(val batchSize: Int) extends Module {
  import ALU._ // Import ALU object, so we do not have to fully-qualify names
  val io = IO(new Bundle {
    val fn = Input(Bits(SZ_ALU_FN.W))
    // The two register content values passed over the RoCCCommand are xLen wide
    val in1 = Input(Vec(batchSize, new DataIO(xLen)))
    val in2 = Input(Vec(batchSize, new DataIO(xLen)))
    val in3 = Input(new DataIO(xLen))
    val out = Output(Valid(Vec(batchSize, new DataIO(xLen))))
    val baseAddress = Input(UInt(xLen.W))
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
    selectFlags(i) := io.in3.data(i.U + selectFlagsCounter)
  }

  /* XXX: If we allow a pipelined divider to exit early, then workingSpace
   * should have type Vec(Valid(DataIO)). Then io.out.valid will need to be an
   * andR across all elements's valid flag in workingSpace. */
  val workingSpace = withReset(io.accelIdle) {
    RegInit((0.U).asTypeOf(Vec(batchSize, new DataIO(xLen))))
  }
  io.out.bits := workingSpace
  io.out.valid := false.B

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

  val reduceMinIdentity = withReset(io.accelIdle) {
    RegInit(((BigInt(1) << (xLen - 1)) - 1).S(xLen.W))
  }

  val reduceANDIdentity = withReset(io.accelIdle) {
    RegInit(1.U(xLen.W))
  }

  /** Perform a paired element-wise binary operation to two `DataIO` vectors.
    */
  def elementWiseMap(xs: Vec[DataIO], ys: Vec[DataIO],
                     op: (UInt, UInt) => UInt): Vec[DataIO] = {
    val indexedPairs = xs.zip(ys).zipWithIndex
    val results = WireInit((0.U).asTypeOf(Vec(batchSize, new DataIO(xLen))))
    /* The address calculation should be a completely parallel indexed map.
     * (i) = thisBatchBaseAddr + (i * 8), where thisBatchBaseAddr comes from
     * some part of the control unit to control where this batch should
     * output */
    results := indexedPairs.map{ case ((x, y), i) => {
      val result = Wire(new DataIO(xLen))
      result.addr := io.baseAddress + (i.U * 8.U)
      result.data := op(x.data, y.data)
      result
      }
    }
    results
  }

  when(io.execute) {
    switch(io.fn) {
      is(0.U) {
        // ADD
        workingSpace := elementWiseMap(io.in1, io.in2, _ + _)
        // Addition/Subtraction only take 1 clock cycle to complete.
        // Or at least the + operator is not too terrible in synthesis.
        io.out.valid := true.B
      }
      is(1.U) {
        // +_REDUCE INT
        lastBatchResult.addr := io.baseAddress
        val batchData = io.in1.map{ case d => d.data }
        lastBatchResult.data := lastBatchResult.data + batchData.reduce(_ + _)
        // NOTE: .reduce could be replaced by reduceTree
      }
      is(2.U) { // +_SCAN INT
        /* FIXME: Can factor out SCAN HW out and just select identity & binary operator
         * rather than the entire thing. Works because .scan()() requires identity
         * as first argument (partial evaluation). */
        val batchData = io.in1.map{ case d => d.data }
        val scanTmp = batchData.scan(scanPlusIdentity)(_ + _)
        // NOTE .scan has .scanLeft & .scanRight variants
        /* NOTE: Technically we compute an extra index for the element
         * we pull out. */
        val scanResults = scanTmp.zipWithIndex.map{ case (d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d
          result
          }
        }
        // .slice(from, to) is [from, to). to is EXCLUSIVE
        workingSpace := scanResults.slice(0, batchSize)
        // Grab the last bit, the end of the vector.
        scanPlusIdentity := scanResults(batchSize).data
      }
      is(3.U){
        // SUB
        workingSpace := elementWiseMap(io.in1, io.in2, _ - _)
        io.out.valid := true.B
      }
      is(4.U){
        // MUL
        val indexedPairs = io.in1.zip(io.in2).zipWithIndex
        workingSpace := indexedPairs.map{ case ((x, y), i) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (i.U * 8.U)
          result.data := x.data * y.data
          result
          }
        }
      }
      is(7.U){
        // DIV
        val indexedPairs = io.in1.zip(io.in2).zipWithIndex
        workingSpace := indexedPairs.map{ case ((x, y), i) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (i.U * 8.U)
          result.data := x.data / y.data
          result
          }
        }
      }
      is(8.U){
        // MOD
        val indexedPairs = io.in1.zip(io.in2).zipWithIndex
        workingSpace := indexedPairs.map{ case ((x, y), i) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (i.U * 8.U)
          result.data := x.data % y.data
          result
          }
        }
      }
      is(9.U){
        // LESS
        workingSpace := elementWiseMap(io.in1, io.in2, _ < _)
        io.out.valid := true.B
      }
      is(10.U){
        // LESS OR EQUAL
        workingSpace := elementWiseMap(io.in1, io.in2, _ <= _)
        io.out.valid := true.B
      }
      is(11.U){
        // GREATER
        workingSpace := elementWiseMap(io.in1, io.in2, _ > _)
        io.out.valid := true.B
      }
      is(12.U){
        // GREATER OR EQUAL
        workingSpace := elementWiseMap(io.in1, io.in2, _ >= _)
        io.out.valid := true.B
      }
      is(13.U){
        // EQUAL
        workingSpace := elementWiseMap(io.in1, io.in2, _ === _)
        io.out.valid := true.B
      }
      is(14.U){
        // UNEQUAL
        workingSpace := elementWiseMap(io.in1, io.in2, _ =/= _)
        io.out.valid := true.B
      }
      is(15.U){
        // LEFT SHIFT
        workingSpace := elementWiseMap(io.in1, io.in2, (x, y) => x << y(18, 0))
        io.out.valid := true.B
      }
      is(16.U){
        // RIGHT SHIFT
        workingSpace := elementWiseMap(io.in1, io.in2, (x, y) => x >> y(18, 0))
        io.out.valid := true.B
      }
      is(17.U){
        // NOT (bitwise for ints)
        workingSpace := elementWiseMap(io.in1, io.in2, (x, _y) => ~x)
        io.out.valid := true.B
      }
      is(18.U){
        // AND (bitwise and boolean)
        workingSpace := elementWiseMap(io.in1, io.in2, _ & _)
        io.out.valid := true.B
      }
      is(19.U){
        // OR (bitwise or boolean)
        workingSpace := elementWiseMap(io.in1, io.in2, _ | _)
        io.out.valid := true.B
      }
      is(20.U){
        // XOR (bitwise xor boolean)
        workingSpace := elementWiseMap(io.in1, io.in2, _ ^ _)
        io.out.valid := true.B
      }
      is(21.U){
        // SELECT
        val selectData = selectFlags.lazyZip(io.in1).lazyZip(io.in2).toVector
        workingSpace := selectData.zipWithIndex.map{ case((s, t, f), i) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (i.U * 8.U)
          result.data := Mux(s, t.data, f.data)
          result
          }
        }
        selectFlagsCounter := selectFlagsCounter + batchSize.U;
      }
      is(22.U){
        // *_SCAN INT
        val batchData = io.in1.map{ case d => d.data }
        val scanTmp = batchData.scan(scanMulIdentity)(_ * _)
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        scanMulIdentity := results(batchSize).data
      }
      is(23.U){
        // MAX SCAN INT
        val batchData = io.in1.map{ case d => d.data.asSInt }
        val scanTmp = batchData.scan(scanMaxIdentity)({(x, y) => Mux(y > x, y, x)})
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d.asUInt
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        scanMaxIdentity := results(batchSize).data.asSInt
      }
      is(24.U){
        // MIN SCAN INT
        val batchData = io.in1.map{ case d => d.data.asSInt }
        val scanTmp = batchData.scan(scanMinIdentity)({(x, y) => Mux(y < x, y, x)})
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d.asUInt
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        scanMinIdentity := results(batchSize).data.asSInt
      }
      is(25.U){
        // AND SCAN INT
        val batchData = io.in1.map{ case d => d.data }
        val scanTmp = batchData.scan(scanANDIdentity)(_ & _)
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        scanANDIdentity := results(batchSize).data
      }
      is(26.U){
        // OR SCAN INT
        val batchData = io.in1.map{ case d => d.data }
        val scanTmp = batchData.scan(scanORIdentity)(_ | _)
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        scanORIdentity := results(batchSize).data
      }
      is(27.U){
        // XOR SCAN INT
        val batchData = io.in1.map{ case d => d.data }
        val scanTmp = batchData.scan(scanXORIdentity)(_ ^ _)
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        scanXORIdentity := results(batchSize).data
      }
      is(28.U) {
        // *_REDUCE INT
        lastBatchResult.addr := io.baseAddress
        val batchData = io.in1.map{ case d => d.data }
        val result = batchData.fold(reduceMulIdentity)((x, y) => x * y)
        lastBatchResult.data := result
        reduceMulIdentity := result
      }
      is(29.U) {
        // MAX_REDUCE INT
        lastBatchResult.addr := io.baseAddress
        val batchData = io.in1.map{ case d => d.data }
        val reduceMaximum = batchData.map(_.asSInt).fold(reduceMaxIdentity)((x, y) => Mux(x > y, x, y))
        val result = Mux(lastBatchResult.data.asSInt > reduceMaximum,
          lastBatchResult.data, reduceMaximum.asUInt)
        lastBatchResult.data := result
        reduceMaxIdentity := result.asSInt
      }
      is(30.U) {
        // MIN_REDUCE INT
        lastBatchResult.addr := io.baseAddress
        val batchData = io.in1.map{ case d => d.data }
        val reduceMaximum = batchData.map(_.asSInt).fold(reduceMinIdentity)((x, y) => Mux(x < y, x, y))
        val result = Mux(lastBatchResult.data.asSInt < reduceMaximum,
          lastBatchResult.data, reduceMaximum.asUInt)
        lastBatchResult.data := result
        reduceMinIdentity := result.asSInt
      }
      is(31.U) {
        // AND_REDUCE INT
        lastBatchResult.addr := io.baseAddress
        val batchData = io.in1.map{ case d => d.data }
        lastBatchResult.data := lastBatchResult.data & batchData.reduce(_ & _)
      }
      is(32.U) {
        // OR_REDUCE INT
        lastBatchResult.addr := io.baseAddress
        val batchData = io.in1.map{ case d => d.data }
        lastBatchResult.data := lastBatchResult.data | batchData.reduce(_ | _)
      }
      is(33.U) {
        // XOR_REDUCE INT
        lastBatchResult.addr := io.baseAddress
        val batchData = io.in1.map{ case d => d.data }
        lastBatchResult.data := lastBatchResult.data ^ batchData.reduce(_ ^ _)
      }
    }
  }
}
