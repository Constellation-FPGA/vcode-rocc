package vcoderocc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.CoreModule
import freechips.rocketchip.rocket.{ALUFN, MulDivParams, MulDiv}
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
    val identityVal = Input(Bits(xLen.W))
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
  /* FIXME: Explode the bools, then dynamically slice the resulting vector. */
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

  /* The pipelined module banks require that their .valid input only be raised
   * for a SINGLE clock cycle. We want the ALU to receive a single sign that it
   * should be executing (hence the io.execute). So we use this register to
   * raise the input data's valid signal when we JUST START executing, and we
   * must only raise it for a single cycle. The combinational value is passed
   * through with no change, but is then held low for the rest of the time.
   * The order of operations is:
   * 1. io.execute = false.B -> Reset this register.
   * 2. io.execute = true.B -> Start the ALU, pipelineStart returns true.B.
   * 3. Next clock cycle (io.execute remains true.B through execution) this
   *    register returns false.B.
   * 4. Eventually the ALU finished, which makes the control unit take
   *    io.execute down to false.B, returning us to step 1. */
  val pipelineStart = withReset(!io.execute) {
    io.execute && !RegNext(io.execute)
  }

  /* Create a pipelined INTEGER multiplier/divider. */
  val mulDivParams = new MulDivParams() // Use default parameters
  val muldivBank = for (i <- 0 until batchSize) yield {
    val muldiv = Module(new MulDiv(mulDivParams, width = xLen,
      // nXpr = batchSize, // The number of expressions in-flight?
      // aluFn = aluFn
    ))
    /* XXX: Choice of FN_MUL here is arbitrary. We need some default value for
     * the connection, before we override it in each ALU function below. */
    muldiv.io.req.bits.fn := ALUFN().FN_MUL
    // All VCODE operations are double-word (64-bit)
    muldiv.io.req.bits.dw := true.B
    muldiv.io.req.bits.in1 := io.in1(i).data
    muldiv.io.req.bits.in2 := io.in2(i).data
    /* We don't use the tag bits for anything here. */
    muldiv.io.req.bits.tag := 0.U
    /* Multiplier inputs are valid when the ALU should start executing. */
    muldiv.io.req.valid := pipelineStart
    /* The multipliers never have back pressure exerted on them. We (the ALU)
     * are always ready to accept a muldiv unit's computed data response, when
     * the ALU is supposed to be computing things.. */
    muldiv.io.resp.ready := io.execute
    /* No muldiv unit should ever receive a kill. */
    muldiv.io.kill := false.B
    /* NOTE: muldiv MUST BE RETURNED from this for-yield's lambda! */
    muldiv
  }

  val compareBank = for (i <- 0 until batchSize) yield {
    // val comparator = Module(new Comparator[SInt](xLen))
    val comparator = Module(new Comparator(xLen))
    comparator.io.req.valid := pipelineStart
    comparator.io.req.bits.fn  := DontCare
    comparator.io.req.bits.in1 := DontCare
    comparator.io.req.bits.in2 := DontCare
    /* NOTE: comparator MUST BE RETURNED from this for-yield's lambda! */
    comparator
  }

  val identity = withReset(io.accelIdle) {
    RegInit(io.identityVal)
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

  /** Perform a reduction on a vector.
   *
   * TODO: This function is well-suited to pipelining between elements in the
   * batch! */
  def reduction(xs: Vec[DataIO], op: (UInt, UInt) => UInt): DataIO = {
    val xsData = xs.map(_.data)
    // NOTE: .reduce could be replaced by reduceTree
    val result = op(identity, xsData.reduce(op))
    val retData = Wire(new DataIO(xLen))
    retData.addr := io.baseAddress
    retData.data := result
    retData
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
        val result = reduction(io.in1, _ + _)
        lastBatchResult := result
        identity := result.data
        io.out.valid := true.B
      }
      is(2.U) { // +_SCAN INT
        val batchData = io.in1.map{ case d => d.data }
        val scanTmp = batchData.scan(identity)(_ + _)
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
        identity := scanResults(batchSize).data
        io.out.valid := true.B
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
          val muldivBankReady = VecInit(muldivBank.map { _.io.req.ready }).reduce(_ & _)
          muldivBank(i).io.req.bits.fn := ALUFN().FN_MUL
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (i.U * 8.U)
          result.data := muldivBank(i).io.resp.bits.data
          result
          }
        }
        io.out.valid := VecInit(muldivBank.map { _.io.resp.valid }).reduce(_ & _)
      }
      is(7.U){
        // DIV
        val indexedPairs = io.in1.zip(io.in2).zipWithIndex
        workingSpace := indexedPairs.map{ case ((x, y), i) => {
          muldivBank(i).io.req.bits.fn := ALUFN().FN_DIV
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (i.U * 8.U)
          result.data := muldivBank(i).io.resp.bits.data
          result
          }
        }
        io.out.valid := VecInit(muldivBank.map { _.io.resp.valid }).reduce(_ & _)
      }
      is(8.U){
        // MOD
        val indexedPairs = io.in1.zip(io.in2).zipWithIndex
        workingSpace := indexedPairs.map{ case ((x, y), i) => {
          muldivBank(i).io.req.bits.fn := ALUFN().FN_REM
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (i.U * 8.U)
          result.data := muldivBank(i).io.resp.bits.data
          result
          }
        }
        io.out.valid := VecInit(muldivBank.map { _.io.resp.valid }).reduce(_ & _)
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
        workingSpace := elementWiseMap(io.in1, io.in2, (x, y) => x << y(5, 0))
        io.out.valid := true.B
      }
      is(16.U){
        // RIGHT SHIFT
        workingSpace := elementWiseMap(io.in1, io.in2, (x, y) => x >> y(5, 0))
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
        io.out.valid := true.B
      }
      is(22.U){
        // *_SCAN INT
        val batchData = io.in1.map{ case d => d.data }
        /* First, hook up working space addresses, because those are easy. */
        for (i <- 0 until batchSize) {
          workingSpace(i).addr := io.baseAddress + (i.U * 8.U)
        }

        /* Now wire the muldiv bank up to perform the scan. */
        muldivBank(0).io.req.bits.in1 := identity
        muldivBank(0).io.req.bits.in2 := batchData(0)
        muldivBank(0).io.req.bits.fn := ALUFN().FN_MUL
        workingSpace(0).data := identity
        for (i <- 1 until batchSize) {
          muldivBank(i).io.req.bits.in1 := muldivBank(i-1).io.resp.bits.data
          muldivBank(i).io.req.bits.in2 := batchData(i)
          muldivBank(i).io.req.bits.fn := ALUFN().FN_MUL
          muldivBank(i).io.req.valid := muldivBank(i-1).io.resp.valid
          workingSpace(i).data := muldivBank(i-1).io.resp.bits.data
        }

        when (muldivBank(batchSize-1).io.resp.valid) {
          identity := muldivBank(batchSize-1).io.resp.bits.data
        }
        io.out.valid := muldivBank(batchSize-1).io.resp.valid
      }
      is(23.U){
        // MAX SCAN INT
        val batchData = io.in1.map{ case d => d.data.asSInt }
        for (i <- 0 until batchSize) {
          workingSpace(i).addr := io.baseAddress + (i.U * 8.U)
        }

        compareBank(0).io.req.bits.in1 := identity.asSInt
        compareBank(0).io.req.bits.in2 := batchData(0)
        compareBank(0).io.req.bits.fn := ComparatorOp.max
        workingSpace(0).data := identity
        for (i <- 1 until batchSize) {
          compareBank(i).io.req.bits.in1 := compareBank(i-1).io.resp.bits.data
          compareBank(i).io.req.bits.in2 := batchData(i)
          compareBank(i).io.req.bits.fn := ComparatorOp.max
          compareBank(i).io.req.valid := compareBank(i-1).io.resp.valid
          workingSpace(i).data := compareBank(i-1).io.resp.bits.data.asUInt
        }

        when (compareBank(batchSize-1).io.resp.valid) {
          identity := compareBank(batchSize-1).io.resp.bits.data.asUInt
        }
        io.out.valid := compareBank(batchSize-1).io.resp.valid
      }
      is(24.U){
        // MIN SCAN INT
        val batchData = io.in1.map{ case d => d.data.asSInt }
        for (i <- 0 until batchSize) {
          workingSpace(i).addr := io.baseAddress + (i.U * 8.U)
        }

        compareBank(0).io.req.bits.in1 := identity.asSInt
        compareBank(0).io.req.bits.in2 := batchData(0)
        compareBank(0).io.req.bits.fn := ComparatorOp.min
        workingSpace(0).data := identity
        for (i <- 1 until batchSize) {
          compareBank(i).io.req.bits.in1 := compareBank(i-1).io.resp.bits.data
          compareBank(i).io.req.bits.in2 := batchData(i)
          compareBank(i).io.req.bits.fn := ComparatorOp.min
          compareBank(i).io.req.valid := compareBank(i-1).io.resp.valid
          workingSpace(i).data := compareBank(i-1).io.resp.bits.data.asUInt
        }

        when (compareBank(batchSize-1).io.resp.valid) {
          identity := compareBank(batchSize-1).io.resp.bits.data.asUInt
        }
        io.out.valid := compareBank(batchSize-1).io.resp.valid
      }
      is(25.U){
        // AND SCAN INT
        val batchData = io.in1.map{ case d => d.data }
        val scanTmp = batchData.scan(identity)(_ & _)
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        identity := results(batchSize).data
        io.out.valid := true.B
      }
      is(26.U){
        // OR SCAN INT
        val batchData = io.in1.map { case d => d.data }
        val scanTmp = batchData.scan(identity)(_ | _)
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        identity := results(batchSize).data
        io.out.valid := true.B
      }
      is(27.U){
        // XOR SCAN INT
        val batchData = io.in1.map { case d => d.data }
        val scanTmp = batchData.scan(identity)(_ ^ _)
        val results = scanTmp.zipWithIndex.map{ case(d, idx) => {
          val result = Wire(new DataIO(xLen))
          result.addr := io.baseAddress + (idx.U * 8.U)
          result.data := d
          result
          }
        }
        workingSpace := results.slice(0, batchSize)
        identity := results(batchSize).data
        io.out.valid := true.B
      }
      is(28.U) {
        // *_REDUCE INT
        val batchData = io.in1.map{ case d => d.data }
        muldivBank(0).io.req.bits.in1 := identity
        muldivBank(0).io.req.bits.in2 := batchData(0)
        muldivBank(0).io.req.bits.fn := ALUFN().FN_MUL
        for (i <- 1 until batchSize) yield {
          muldivBank(i).io.req.bits.in1 := muldivBank(i-1).io.resp.bits.data
          muldivBank(i).io.req.bits.in2 := batchData(i)
          muldivBank(i).io.req.bits.fn := ALUFN().FN_MUL
          muldivBank(i).io.req.valid := muldivBank(i-1).io.resp.valid
        }

        lastBatchResult.addr := io.baseAddress
        lastBatchResult.data := muldivBank(batchSize-1).io.resp.bits.data
        identity := muldivBank(batchSize-1).io.resp.bits.data
        io.out.valid := muldivBank(batchSize-1).io.resp.valid
      }
      is(29.U) {
        // MAX_REDUCE INT
        val batchData = io.in1.map{ case d => d.data.asSInt }
        compareBank(0).io.req.bits.in1 := identity.asSInt
        compareBank(0).io.req.bits.in2 := batchData(0)
        compareBank(0).io.req.bits.fn := ComparatorOp.max

        for (i <- 1 until batchSize) yield {
          compareBank(i).io.req.bits.in1 := compareBank(i-1).io.resp.bits.data
          compareBank(i).io.req.bits.in2 := batchData(i)
          compareBank(i).io.req.bits.fn := ComparatorOp.max
          compareBank(i).io.req.valid := compareBank(i-1).io.resp.valid
        }

        lastBatchResult.addr := io.baseAddress
        lastBatchResult.data := compareBank(batchSize-1).io.resp.bits.data.asUInt
        identity := compareBank(batchSize-1).io.resp.bits.data.asUInt
        io.out.valid := compareBank(batchSize-1).io.resp.valid
      }
      is(30.U) {
        // MIN_REDUCE INT
        val batchData = io.in1.map{ case d => d.data.asSInt }
        compareBank(0).io.req.bits.in1 := identity.asSInt
        compareBank(0).io.req.bits.in2 := batchData(0)
        compareBank(0).io.req.bits.fn := ComparatorOp.min

        for (i <- 1 until batchSize) yield {
          compareBank(i).io.req.bits.in1 := compareBank(i-1).io.resp.bits.data
          compareBank(i).io.req.bits.in2 := batchData(i)
          compareBank(i).io.req.bits.fn := ComparatorOp.min
          compareBank(i).io.req.valid := compareBank(i-1).io.resp.valid
        }

        lastBatchResult.addr := io.baseAddress
        lastBatchResult.data := compareBank(batchSize-1).io.resp.bits.data.asUInt
        identity := compareBank(batchSize-1).io.resp.bits.data.asUInt
        io.out.valid := compareBank(batchSize-1).io.resp.valid
      }
      is(31.U) {
        // AND_REDUCE INT
        val result = reduction(io.in1, _ & _)
        lastBatchResult := result
        identity := result.data
        io.out.valid := true.B
      }
      is(32.U) {
        // OR_REDUCE INT
        val result = reduction(io.in1, _ | _)
        lastBatchResult := result
        identity := result.data
        io.out.valid := true.B
      }
      is(33.U) {
        // XOR_REDUCE INT
        val result = reduction(io.in1, _ ^ _)
        lastBatchResult := result
        identity := result.data
        io.out.valid := true.B
      }
    }
  }
}

object ComparatorOp extends ChiselEnum {
  val min, max = Value
}

/* TODO: Get proper subtyping on the comparator classes. We can only compare
 * classes T that are subclasses of Num, namely T <: Num */
final class ComparatorReq(dataBits: Int) extends Bundle {
  val fn = ComparatorOp()
  val in1 = SInt(dataBits.W)
  val in2 = SInt(dataBits.W)
}

final class ComparatorResp(dataBits: Int) extends Bundle {
  val data = SInt(dataBits.W)
}

/** A simple comparator that produces its value one clock cycle delayed.
* This is intended for pipelining. */
final class Comparator(val xLen: Int) extends Module {
  val io = IO(new Bundle {
    val req = Input(Valid(new ComparatorReq(xLen)))
    val resp = Output(Valid(new ComparatorResp(xLen)))
  })
  /* Rename/Alias in1's & in2's bit data for ease of reading the module. */
  val x = io.req.bits.in1
  val y = io.req.bits.in2

  val result = Wire(new ComparatorResp(xLen))

  /* We expect that a single xLen comparison can be done in a single clock
   * cycle, but comparison is still relatively slow compared to other operations.
   * So if you have a very high clock frequency or many comparisons strung
   * together in a chain, you will end up experiencing timing violations when you
   * synthesize the design for that clock frequency.
   * By "overriding" Chisel's default .min and .max methods and attaching
   * pipeline registers between them, you should be able to do comparisons even
   * faster.
   * NOTE: There is a theoretical limit here as determined by the target
   * platform. Often comparisons are done on just a few bits in parallel and the
   * results are combined later. Different platforms that have native HW
   * support for this, will have different native widths, which will affect how
   * you have to pipeline your design. */
  result.data := DontCare
  switch (io.req.bits.fn) {
    is (ComparatorOp.min) {
      result.data := x.min(y)
    }
    is (ComparatorOp.max) {
      result.data := x.max(y)
    }
  }

  io.resp.valid := RegNext(io.req.valid)
  io.resp.bits := RegNext(result)
}
