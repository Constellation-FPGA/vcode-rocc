package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy.lazymodule._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import vcoderocc.constants._
import PermuteUnit._

/** The outer wrapping class for the VCODE accelerator.
  *
  * @constructor Create a new VCode accelerator interface using one of the
  * custom opcode sets.
  * @param opcodes The custom opcode set to use.
  * @param p The implicit key-value store of design parameters for this design.
  * This value is passed by the build system. You do not need to worry about it.
  */
class VCodeAccel(opcodes: OpcodeSet, batchSize: Int)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  // batchSize must be power of 2 to make certain ops on counters efficient
  require(isPow2(batchSize), "VCode accelerator batchSize must be power of 2!")
  require((batchSize <= 64), "VCode accelerator batchSize must not be greater than 64!")
  override lazy val module = new VCodeAccelImp(this, batchSize)
}

/** Implementation class for the VCODE accelerator.
  *
  * @constructor Create a new VCODE accelerator implementation, attached to
  * one VCodeAccel interface with one of the custom opcode sets.
  * @param outer The "interface" for the accelerator to attach to.
  * This separation allows us to attach multiple of these accelerators to
  * different HARTs, and multiple to attach to a single HART using different
  * custom opcode sets.
  */
class VCodeAccelImp(outer: VCodeAccel, batchSize: Int) extends LazyRoCCModuleImp(outer) {
  // io is "implicit" because we inherit from LazyRoCCModuleImp.
  // io is the RoCCCoreIO
  val xLen = p(TileKey).core.xLen
  val rocc_io = io
  val cmd = rocc_io.cmd
  val roccCmd = Reg(new RoCCCommand)
  val cmdValid = RegInit(false.B)

  val roccInst = roccCmd.inst // The customX instruction in instruction stream
  val returnReg = roccInst.rd
  val status = roccCmd.status
  when(cmd.fire) {
    roccCmd := cmd.bits // The entire RoCC Command provided to the accelerator
    cmdValid := true.B
  }

  /***************
   * DECODE
   * Decode instruction, yielding control signals
   **************/
  val decoder = Module(new Decoder)
  val ctrlSigs = Wire(new CtrlSigs())
  decoder.io.roccInst := roccCmd.inst
  ctrlSigs := decoder.io.ctrlSigs

  /***************
   * CONTROL UNIT
   * Control unit connects ALU, Permute unit & Data fetcher together, properly sequencing them
   **************/
  val ctrlUnit = Module(new ControlUnit(batchSize))
  // Accelerator control unit controls when we are ready to accept the next
  // instruction from the RoCC command queue. Cannot accept another command
  // unless accelerator is ready/idle
  cmd.ready := ctrlUnit.io.accelReady
  ctrlUnit.io.cmdValid := cmdValid
  // RoCC must assert RoCCCoreIO.busy line high when memory actions happening
  rocc_io.busy := ctrlUnit.io.busy
  ctrlUnit.io.roccCmd := roccCmd
  ctrlUnit.io.ctrlSigs := ctrlSigs
  ctrlUnit.io.responseCompleted := rocc_io.resp.fire

  // If invalid instruction, raise exception
  val exception = cmdValid && !ctrlSigs.legal
  rocc_io.interrupt := exception
  when(exception) {
    if(p(VCodePrintfEnable)) {
      printf("Raising exception to processor through interrupt!\nILLEGAL INSTRUCTION!\n")
    }
    roccCmd := DontCare
    cmdValid := false.B
    status := DontCare
  }

  /* The valid bit is raised to true by the main processor when the command is
   * sent to the DecoupledIO Queue. */
  when(cmdValid) {
    // TODO: Find a nice way to condense these conditional prints
    if(p(VCodePrintfEnable)) {
      printf("Got funct7 = 0x%x\trs1.val=0x%x\trs2.val=0x%x\txd.val=0x%x\n",
        roccCmd.inst.funct, roccCmd.rs1, roccCmd.rs2, roccCmd.inst.xd)
      printf("The instruction legal: %d\n", ctrlSigs.legal)
    }
  }

  /***************
   * DATA FETCH
   * Most instructions pass pointers to vectors, so we need to fetch that before
   * operating on the data.
   **************/
  val dataFetcher = Module(new DCacheFetcher(batchSize))
  dataFetcher.io.ctrlSigs := ctrlSigs
  dataFetcher.io.mstatus := status
  ctrlUnit.io.memOpCompleted := dataFetcher.io.opCompleted

  when(ctrlUnit.io.writebackReady) {
    dataFetcher.io.opToPerform := MemoryOperation.write
  } .otherwise {
    dataFetcher.io.opToPerform := MemoryOperation.read
  }

  rocc_io.mem.req :<>= dataFetcher.io.req // Connect Request queue
  dataFetcher.io.resp :<>= rocc_io.mem.resp  // Connect response queue

  /* rsX here are just wire aliases to make using rs1/rs2 slightly shorter in
   * later portions of this file, where rsX get used more frequently. */
  val rs1 = Wire(Bits(xLen.W)); rs1 := roccCmd.rs1
  val rs2 = Wire(Bits(xLen.W)); rs2 := roccCmd.rs2

  val addrToFetch = ctrlUnit.io.baseAddress
  // FIXME: Should not need to rely on op_completed boolean
  when((ctrlUnit.io.shouldFetch || ctrlUnit.io.writebackReady) &&
    !dataFetcher.io.opCompleted && dataFetcher.io.baseAddress.ready) {
    // Queue addrs and set valid bit
    dataFetcher.io.baseAddress.enq(addrToFetch)
    if(p(VCodePrintfEnable)) {
      printf("VCode\tEnqueued addresses to data fetcher\n")
      printf("\tBase Address: 0x%x\tvalid? %d\n",
        dataFetcher.io.baseAddress.bits, dataFetcher.io.baseAddress.valid)
    }
  } .otherwise {
    dataFetcher.io.baseAddress.noenq()
  }
  dataFetcher.io.start := ctrlUnit.io.shouldFetch || ctrlUnit.io.writebackReady
  dataFetcher.io.amountData := ctrlUnit.io.numToFetch

  val data1 = RegInit((0.U).asTypeOf(Vec(batchSize, new DataIO(xLen))))
  val data2 = RegInit((0.U).asTypeOf(Vec(batchSize, new DataIO(xLen))))
  val data3 = RegInit(0.U(xLen.W))
  // FIXME: Only use rs1/rs2 if xs1/xs2 =1, respectively.
  when(dataFetcher.io.fetchedData.valid) {
    /* TODO: Use SourceOperand here! */
    when(ctrlUnit.io.rs1Fetch) {
      data1 := dataFetcher.io.fetchedData.bits
    } .elsewhen(ctrlUnit.io.rs2Fetch){
      data2 := dataFetcher.io.fetchedData.bits
    }.otherwise {
      data3 := dataFetcher.io.fetchedData.bits(0).data
    }
  }

  /***************
   * EXECUTE
   **************/
  /* TODO: Have multiple ALUs, one for each element, thus one per lane?
   * Or have one big ALU handle a whole batchSize simultaneously? */
  // Must more specifically specify MY ALU, because freechips.rocketchip.rocket.ALU is also defined.
  // ALU processing integer instructions except permutations
  val alu = Module(new vcoderocc.ALU(xLen)(batchSize))
  // Hook up the ALU to VCode signals
  alu.io.fn := ctrlSigs.aluFn
  alu.io.in1 := data1
  alu.io.in2 := data2
  alu.io.in3 := data3
  alu.io.baseAddress := ctrlUnit.io.baseAddress
  alu.io.execute := ctrlUnit.io.shouldExecute
  alu.io.accelIdle := !ctrlUnit.io.busy // ctrlUnit.io.accelReady is also valid.

  // ALU processing permute instructions
  val permute = Module(new vcoderocc.PermuteUnit(xLen)(batchSize))
  permute.io.fn := ctrlSigs.aluFn
  permute.io.index := data1
  permute.io.data := data2
  permute.io.default := data3
  permute.io.baseAddress := ctrlUnit.io.baseAddress
  permute.io.execute := ctrlUnit.io.shouldExecute
  permute.io.accelIdle := !ctrlUnit.io.busy

  // assert(forall ctrlUnit.io.baseAddr <= dataToWrite.bits.addr &&
  //               dataToWrite.bits.addr < (ctrlUnit.io.baseAddr + ctrlUnit.io.totalLength * 8))
  /* FIXME: 34.U is a magic number. Turn it into a constant.
   * Even better in this situation is to somehow mark each of the instructions
   * as being part of a class. Then we can match against the /kind/ of instruction
   * it is. */
  dataFetcher.io.dataToWrite.bits := Mux(ctrlSigs.aluFn === 34.U, permute.io.out, alu.io.out)
  dataFetcher.io.dataToWrite.valid := ctrlUnit.io.writebackReady

  val responseReady = Wire(Bool())
  responseReady := ctrlUnit.io.responseReady

  /***************
   * RESPOND
   **************/
  // Check if the accelerator needs to respond
  val responseRequired = RegInit(false.B)
  when(cmdValid && ctrlSigs.legal && roccCmd.inst.xd) {
    responseRequired := true.B
  }

  // Send response to main processor
  /* NOTE: RoCCResponse has an internal register to store the response. Using a
   * wire here is a non-issue because of it. */
  val response = Wire(new RoCCResponse)
  response.rd := returnReg
  response.data := 0.U // 0 for success. Could be number of elements processed too.
  io.resp.bits := response
  io.resp.valid := responseRequired && responseReady || exception
  when(rocc_io.resp.fire) {
    responseRequired := false.B
    cmdValid := false.B
  }

  when(responseRequired && responseReady) {
    if(p(VCodePrintfEnable)) {
      printf("Main processor ready for response? %d\n", io.resp.ready)
    }

    if(p(VCodePrintfEnable)) {
      printf("VCode accelerator made response with data 0x%x valid? %d\n",
        io.resp.bits.data, io.resp.valid)
    }
  }

  /* LazyRoCC class contains two TLOutputNode instances, atlNode and tlNode.
   * atlNode connects into a tile-local arbiter along with the backside of the
   * L1 instruction cache.
   * tlNode connects directly to the L1-L2 crossbar. The corresponding Tilelink
   * ports in the module implementation’s IO bundle are atl and tl, respectively. */
}
