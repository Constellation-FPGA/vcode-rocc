package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.rocket._
import freechips.rocketchip.tilelink._
import vcoderocc.constants._

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
  // TODO: Make rocc_cmd & rocc_cmd_valid use bundle/class
  val rocc_cmd = Reg(new RoCCCommand())
  val rocc_cmd_valid = RegInit(false.B)
  val rocc_inst = rocc_cmd.inst // The customX instruction in instruction stream
  // Register to control when to raise io.resp.valid flag back to main processor
  when(cmd.fire) {
    // cmd.fire is 1 for only 1 clock cycle!
    rocc_cmd := cmd.bits // The entire RoCC Command provided to the accelerator
    rocc_cmd_valid := true.B
  }

  /***************
   * CONTROL UNIT
   * Control unit connects ALU & Data fetcher together, properly sequencing them
   **************/
  val ctrl_unit = Module(new ControlUnit())
  ctrl_unit.io.cmd.cmd := rocc_cmd
  ctrl_unit.io.cmd.valid := rocc_cmd_valid

  // Accelerator control unit controls when we are ready to accept the next
  // instruction from the RoCC command queue. Cannot accept another command
  // unless accelerator is ready/idle
  cmd.ready := ctrl_unit.io.accel_ready

  val rs1 = Wire(Bits(p(XLen).W)); rs1 := rocc_cmd.rs1
  val rs2 = Wire(Bits(p(XLen).W)); rs2 := rocc_cmd.rs2
  val addrs = Wire(new AddressBundle(p(XLen)))
  addrs.addr1 := rs1; addrs.addr2 := rs2

  /* Create the decode table at the top-level of the implementation
   * If additional instructions are added as separate classes in Instructions.scala
   * they can be added above BinOpDecode class. */
  val decode_table = {
    Seq(new BinOpDecode)
  } flatMap(_.decode_table)

  /***************
   * DECODE
   **************/
  // Decode instruction, yielding control signals
  val ctrl_sigs = Wire(new CtrlSigs()).decode(rocc_inst.funct, decode_table)
  ctrl_unit.io.ctrl_sigs := ctrl_sigs

  // TODO: Exception-raising module?
  // If invalid instruction, raise exception
  val exception = rocc_cmd_valid && !ctrl_sigs.legal
  rocc_io.interrupt := exception
  when(exception) {
    if(p(VCodePrintfEnable)) {
      printf("Raising exception to processor through interrupt!\nILLEGAL INSTRUCTION!\n");
    }
    rocc_cmd_valid := false.B // The provided command was invalid!
  }

  /* The valid bit is raised to true by the main processor when the command is
   * sent to the DecoupledIO Queue. */
  when(rocc_cmd_valid) {
    // TODO: Find a nice way to condense these conditional prints
    if(p(VCodePrintfEnable)) {
      printf("Got funct7 = 0x%x\trs1.val=0x%x\trs2.val=0x%x\n",
        rocc_inst.funct, rocc_cmd.rs1, rocc_cmd.rs2)
      printf("The instruction legal: %d\n", ctrl_sigs.legal)
    }
  }

  /***************
   * DATA FETCH
   * Most instructions pass pointers to vectors, so we need to fetch that before
   * operating on the data.
   **************/
  val data_fetcher = Module(new DCacheFetcher)
  rocc_io.mem.req <> data_fetcher.io.req // Connect Request queue
  data_fetcher.io.resp <> rocc_io.mem.resp  // Connect response queue

  val dmem_data = Wire(Bits(p(XLen).W)) // Data to SEND to memory

  val data1 = RegInit(0.U(p(XLen).W))
  val data2 = RegInit(0.U(p(XLen).W))

  /***************
   * EXECUTE
   **************/
  val alu = Module(new ALU)
  val alu_out = Reg(UInt())
  val alu_cout = Wire(UInt())
  // Hook up the ALU to VCode signals
  alu.io.fn := ctrl_sigs.alu_fn
  // FIXME: Only use rs1/rs2 if xs1/xs2 =1, respectively.
  alu.io.in1 := data1
  alu.io.in2 := data2
  when(alu.io.out.valid) {
    alu_out := alu.io.out.bits
  }
  alu_cout := alu.io.cout

  /***************
   * Connect more control unit signals
   **************/
  // Data-fetching control signals
  // RoCC must assert RoCCCoreIO.busy line high when memory actions happening
  rocc_io.busy := ctrl_unit.io.busy
  ctrl_unit.io.fetching_completed := data_fetcher.io.fetching_completed
  when(data_fetcher.io.addrs.ready) {
    // Queue addrs and set valid bit
    data_fetcher.io.addrs.enq(addrs)
  } .otherwise {
    data_fetcher.io.addrs.noenq()
  }
  data_fetcher.io.should_fetch := ctrl_unit.io.should_fetch
  data_fetcher.io.num_to_fetch := ctrl_unit.io.num_to_fetch

  // Execution control signals.
  alu.io.execute := ctrl_unit.io.should_execute
  ctrl_unit.io.execution_completed := alu.io.out.valid
  dmem_data := 0.U // FIXME: This is where write-back should happen

  when(data_fetcher.io.fetched_data.valid) {
    data1 := data_fetcher.io.fetched_data.bits.data1
    data2 := data_fetcher.io.fetched_data.bits.data2
  }

  // Result-returning control signals
  val response_ready = Wire(Bool()); response_ready := ctrl_unit.io.response_ready
  ctrl_unit.io.response_completed := io.resp.valid

  /***************
   * RESPOND
   **************/
  // Check if the accelerator needs to respond
  val response_required = ctrl_sigs.legal && rocc_inst.xd
  val response = Reg(new RoCCResponse)
  response.rd := rocc_inst.rd
  response.data := alu_out
  // Send response to main processor
  /* TODO: Response can only be sent once all memory transactions and arithmetic
   * operations have completed. */
  when(response_required && io.resp.ready && response_ready) {
    if(p(VCodePrintfEnable)) {
      printf("Main processor ready for response? %d\n", io.resp.ready)
    }
    io.resp.enq(response) // Sends response & sets valid bit
    io.resp.bits := response
    rocc_cmd_valid := false.B // Now done, so this instruction is no longer valid
    if(p(VCodePrintfEnable)) {
      printf("VCode accelerator made response bits valid? %d\n", io.resp.valid)
    }
  }
}

/** Mixin to build a chip that includes a VCode accelerator.
  */
class WithVCodeAccel extends Config((site, here, up) => {
  case BuildRoCC => List (
    (p: Parameters) => {
      val vcodeAccel = LazyModule(new VCodeAccel(OpcodeSet.custom0)(p))
      vcodeAccel
    })
})

/** Design-level configuration option to toggle the synthesis of print statements
  * in the synthesized hardware design.
  */
case object VCodePrintfEnable extends Field[Boolean](false)

/** Mixin to enable print statements from the synthesized design.
  * This mixin should only be used AFTER the WithVCodeAccel mixin.
  */
class WithVCodePrintf extends Config((site, here, up) => {
  case VCodePrintfEnable => true
})
