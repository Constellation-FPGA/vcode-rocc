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
  val rocc_cmd = cmd.bits // The entire RoCC Command provided to the accelerator
  val rocc_inst = rocc_cmd.inst // The customX instruction in instruction stream
  cmd.ready := true.B // Always ready to accept a command

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

  // If invalid instruction, raise exception
  val exception = cmd.valid && !ctrl_sigs.legal
  io.interrupt := exception
  when(exception) {
    if(p(VCodePrintfEnable)) {
      printf("Raising exception to processor through interrupt!\nILLEGAL INSTRUCTION!\n");
    }
  }

  /* The valid bit is raised to true by the main processor when the command is
   * sent to the DecoupledIO Queue. */
  when(cmd.valid) {
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

  val data1 = Wire(Bits(p(XLen).W))
  val data2 = Wire(Bits(p(XLen).W))

  /***************
   * CONTROL UNIT
   * Control unit connects ALU & Data fetcher together, properly sequencing them
   **************/
  val ctrl_unit = Module(new ControlUnit())
  ctrl_unit.io.cmd := rocc_cmd
  ctrl_unit.io.ctrl_sigs := ctrl_sigs
  data_fetcher.io.should_fetch := ctrl_unit.io.should_fetch
  alu.io.execute := ctrl_unit.io.should_execute
  dmem_data := 0.U // FIXME: This is where write-back should happen

  // RoCC must assert RoCCCoreIO.busy line high when memory actions happening
  rocc_io.busy := ctrl_unit.io.busy

  // when(!busy) {
  //   data1 := data_ctrl.io.data1
  //   // data2 := data_ctrl.io.data2
  // }
  data1 := data_ctrl.io.data1
  // data1 := rocc_cmd.rs1
  data2 := rocc_cmd.rs2

  /***************
   * EXECUTE
   **************/
  val alu = Module(new ALU)
  val alu_out = Wire(UInt())
  val alu_cout = Wire(UInt())
  // Hook up the ALU to VCode signals
  alu.io.fn := ctrl_sigs.alu_fn
  // FIXME: Only use rs1/rs2 if xs1/xs2 =1, respectively.
  alu.io.in1 := data1
  alu.io.in2 := data2
  alu_out := alu.io.out
  alu_cout := alu.io.cout

  /***************
   * RESPOND
   **************/
  // Check if the accelerator needs to respond
  val response_required = cmd.valid && ctrl_sigs.legal && rocc_inst.xd
  val response = Reg(new RoCCResponse)
  response.rd := rocc_inst.rd
  response.data := alu_out
  // Send response to main processor
  /* TODO: Response can only be sent once all memory transactions and arithmetic
   * operations have completed. */
  when(response_required && io.resp.ready) {
    if(p(VCodePrintfEnable)) {
      printf("Main processor ready for response? %d\n", io.resp.ready)
    }
    io.resp.enq(response) // Sends response & sets valid bit
    if(p(VCodePrintfEnable)) {
      printf("VCode accelerator made response bits valid? %d\n", io.resp.valid)
    }
  }
  // TODO: Find way to make valid response false when no response needed or ready
  // io.resp.valid := false.B // Always invalid response until otherwise
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
