package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import vcoderocc.constants._

import freechips.rocketchip.util.EnhancedChisel3Assign

/** The outer wrapping class for the VCODE accelerator.
  *
  * @constructor Create a new VCode accelerator interface using one of the
  * custom opcode sets.
  * @param opcodes The custom opcode set to use.
  * @param p The implicit key-value store of design parameters for this design.
  * This value is passed by the build system. You do not need to worry about it.
  */
class VCodeAccel(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(opcodes) {
  override lazy val module = new VCodeAccelImp(this)
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
class VCodeAccelImp(outer: VCodeAccel) extends LazyRoCCModuleImp(outer) {
  // io is "implicit" because we inherit from LazyRoCCModuleImp.
  // io is the RoCCCoreIO
  val rocc_io = io
  val cmd = Queue(rocc_io.cmd)
  val rocc_cmd = cmd.bits // The entire RoCC Command provided to the accelerator
  val rocc_inst = rocc_cmd.inst // The customX instruction in instruction stream
  // Register to control when to raise io.resp.valid flag back to main processor
  val returnReg = RegInit(0.U(5.W)) // FIXME: Parameterize?
  when(cmd.fire) {
    returnReg := rocc_cmd.inst.rd
  }

  /***************
   * DECODE
   * Decode instruction, yielding control signals
   **************/
  val decoder = Module(new Decoder)
  val ctrl_sigs = Wire(new CtrlSigs())
  decoder.io.rocc_inst := rocc_cmd.inst
  ctrl_sigs := decoder.io.ctrl_sigs

  /***************
   * CONTROL UNIT
   * Control unit connects ALU & Data fetcher together, properly sequencing them
   **************/
  val ctrl_unit = Module(new ControlUnit())
  // Accelerator control unit controls when we are ready to accept the next
  // instruction from the RoCC command queue. Cannot accept another command
  // unless accelerator is ready/idle
  cmd.ready := ctrl_unit.io.accel_ready
  // RoCC must assert RoCCCoreIO.busy line high when memory actions happening
  rocc_io.busy := ctrl_unit.io.busy
  ctrl_unit.io.ctrl_sigs := ctrl_sigs
  ctrl_unit.io.response_completed := rocc_io.resp.fire

  // TODO: Exception-raising module?
  // If invalid instruction, raise exception
  val exception = cmd.valid && !ctrl_sigs.legal
  rocc_io.interrupt := exception
  when(exception) {
    if(p(VCodePrintfEnable)) {
      printf("Raising exception to processor through interrupt!\nILLEGAL INSTRUCTION!cmd.fire=%d\n",cmd.fire);
    }
  }

  /* The valid bit is raised to true by the main processor when the command is
   * sent to the DecoupledIO Queue. */
  when(cmd.fire) {
    // TODO: Find a nice way to condense these conditional prints
    if(p(VCodePrintfEnable)) {
      printf("Got funct7 = 0x%x\trs1.val=0x%x\trs2.val=0x%x\txd.val=0x%x\n",
        rocc_cmd.inst.funct, rocc_cmd.rs1, rocc_cmd.rs2, rocc_cmd.inst.xd)
      printf("The instruction legal: %d\n", ctrl_sigs.legal)
    }
  }

  /***************
   * DATA FETCH
   * Most instructions pass pointers to vectors, so we need to fetch that before
   * operating on the data.
   **************/
  val data_fetcher = Module(new DCacheFetcher)
  data_fetcher.io.ctrl_sigs := ctrl_sigs
  data_fetcher.io.mstatus := rocc_cmd.status
  rocc_io.mem.req :<> data_fetcher.io.req // Connect Request queue
  data_fetcher.io.resp :<> rocc_io.mem.resp  // Connect response queue

  val rs1 = Wire(Bits(p(XLen).W)); rs1 := rocc_cmd.rs1
  val rs2 = Wire(Bits(p(XLen).W)); rs2 := rocc_cmd.rs2
  val addrs = Reg(new AddressBundle(p(XLen)))

  when(ctrl_sigs.legal && ctrl_sigs.is_mem_op) {
    addrs.addr1 := rs1; addrs.addr2 := rs2
  }

  ctrl_unit.io.fetching_completed := data_fetcher.io.fetching_completed
  data_fetcher.io.addrs.bits := addrs
  // FIXME: Should not need to rely on fetching_completed boolean
  when(ctrl_unit.io.should_fetch && !ctrl_unit.io.fetching_completed && data_fetcher.io.addrs.ready) {
    // Queue addrs and set valid bit
    data_fetcher.io.addrs.enq(addrs)
    if(p(VCodePrintfEnable)) {
      printf("VCode\tEnqueued addresses to data fetcher\n")
      printf("\taddr1: 0x%x, addr2: 0x%x\tvalid? %d\n",
        data_fetcher.io.addrs.bits.addr1, data_fetcher.io.addrs.bits.addr2, data_fetcher.io.addrs.valid)
    }
  } .otherwise {
    data_fetcher.io.addrs.valid := false.B
  }
  data_fetcher.io.should_fetch := ctrl_unit.io.should_fetch
  data_fetcher.io.num_to_fetch := ctrl_unit.io.num_to_fetch

  val dmem_data = Wire(Bits(p(XLen).W)) // Data to SEND to memory

  val data1 = RegInit(0.U(p(XLen).W))
  val data2 = RegInit(0.U(p(XLen).W))

  /***************
   * EXECUTE
   **************/
  val alu = Module(new ALU(p(XLen)))
  val alu_out = Reg(UInt())
  val alu_cout = Wire(UInt())
  // Hook up the ALU to VCode signals
  alu.io.fn := ctrl_sigs.alu_fn
  // FIXME: Only use rs1/rs2 if xs1/xs2 =1, respectively.
  when(data_fetcher.io.fetched_data.valid) {
    data1 := data_fetcher.io.fetched_data.bits(0)
    data2 := data_fetcher.io.fetched_data.bits(1)
  }
  alu.io.in1 := data1
  alu.io.in2 := data2
  alu.io.execute := ctrl_unit.io.should_execute
  when(alu.io.out.valid) {
    alu_out := alu.io.out.bits
    if(p(VCodePrintfEnable)) {
      printf("VCode\tALU in1: 0x%x\tin2: 0x%x\tout: 0x%x\nALU finished executing! Output bits now valid!\n",
      alu.io.in1, alu.io.in2, alu.io.out.bits)
    }
  }
  alu_cout := alu.io.cout
  ctrl_unit.io.execution_completed := alu.io.out.valid

  dmem_data := 0.U // FIXME: This is where write-back should happen

  val response_ready = Wire(Bool())
  response_ready := ctrl_unit.io.response_ready

  /***************
   * RESPOND
   **************/
  // Check if the accelerator needs to respond
  val response_required = RegInit(false.B)
  when(ctrl_sigs.legal && rocc_cmd.inst.xd) {
    response_required := true.B
  }

  // Send response to main processor
  /* NOTE: RoCCResponse has an internal register to store the response. Using a
   * wire here is a non-issue because of it. */
  val response = Wire(new RoCCResponse)
  response.rd := returnReg
  response.data := alu_out
  io.resp.bits := response
  io.resp.valid := response_required && response_ready
  when(rocc_io.resp.fire) {
    response_required := false.B
  }

  when(response_required && io.resp.ready && response_ready) {
    if(p(VCodePrintfEnable)) {
      printf("Main processor ready for response? %d\n", io.resp.ready)
    }

    cmd.deq // Dequeue this instruction from the queue

    if(p(VCodePrintfEnable)) {
      printf("VCode accelerator made response with data 0x%x valid? %d\n",
        io.resp.bits.data, io.resp.valid)
    }
  }
}
