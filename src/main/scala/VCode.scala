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
  val cmd = rocc_io.cmd
  val rocc_cmd = Reg(new RoCCCommand)
  val cmd_valid = RegInit(false.B)

  val rocc_inst = rocc_cmd.inst // The customX instruction in instruction stream
  val returnReg = rocc_inst.rd
  val status = rocc_cmd.status
  when(cmd.fire) {
    rocc_cmd := cmd.bits // The entire RoCC Command provided to the accelerator
    cmd_valid := true.B
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
  ctrl_unit.io.cmd_valid := cmd_valid
  // RoCC must assert RoCCCoreIO.busy line high when memory actions happening
  rocc_io.busy := ctrl_unit.io.busy
  ctrl_unit.io.ctrl_sigs := ctrl_sigs
  ctrl_unit.io.response_completed := rocc_io.resp.fire

  // TODO: Exception-raising module?
  // If invalid instruction, raise exception
  val exception = cmd_valid && !ctrl_sigs.legal
  rocc_io.interrupt := exception
  when(exception) {
    if(p(VCodePrintfEnable)) {
      printf("Raising exception to processor through interrupt!\nILLEGAL INSTRUCTION!\n")
    }
    rocc_cmd := DontCare
    cmd_valid := false.B
    status := DontCare
  }
  
  /* The valid bit is raised to true by the main processor when the command is
   * sent to the DecoupledIO Queue. */
  when(cmd_valid) {
    // TODO: Find a nice way to condense these conditional prints
    if(p(VCodePrintfEnable)) {
      printf("Got funct7 = 0x%x\trs1.val=0x%x\trs2.val=0x%x\txd.val=0x%x\n",
        rocc_cmd.inst.funct, rocc_cmd.rs1, rocc_cmd.rs2, rocc_cmd.inst.xd)
      printf("The instruction legal: %d\n", ctrl_sigs.legal)
    }
  }



  /** Reduction Accum **/
  val redAccum0 = RegInit(0.U(p(XLen).W));
  val redAccum1 = RegInit(~(0.U(p(XLen).W)));

  when(cmd_valid && !rocc_io.busy) {
    // TODO: Find a nice way to condense these conditional prints
    if(p(VCodePrintfEnable)) {
      printf("Reseting the reduction accumulator to 0")
    }
  }

  val numOperands = RegInit(0.U(p(XLen).W))
  when(cmd_valid && rocc_cmd.inst.funct === Instructions.SET_NUM_OPERANDS && rocc_cmd.inst.xs1) {
    numOperands := rocc_cmd.rs1
    if(p(VCodePrintfEnable)) {
      printf("VCode\tSet numOperands to 0x%x\n", rocc_cmd.rs1)
    }
    cmd_valid := false.B
  }

  val destAddr = RegInit(0.U(p(XLen).W))
  when(cmd_valid && rocc_cmd.inst.funct === Instructions.SET_DEST_ADDR && rocc_cmd.inst.xs1) {
    destAddr := rocc_cmd.rs1
    if(p(VCodePrintfEnable)) {
      printf("VCode\tSet destAddr to 0x%x\n", rocc_cmd.rs1)
    }
    cmd_valid := false.B
  }

  /***************
   * DATA FETCH
   * Most instructions pass pointers to vectors, so we need to fetch that before
   * operating on the data.
   **************/
  val data_fetcher = Module(new DCacheFetcher(8))
  data_fetcher.io.ctrl_sigs := ctrl_sigs
  data_fetcher.io.mstatus := status
  rocc_io.mem.req :<> data_fetcher.io.req // Connect Request queue
  data_fetcher.io.resp :<> rocc_io.mem.resp  // Connect response queue

  val rs1 = Wire(Bits(p(XLen).W)); rs1 := rocc_cmd.rs1
  val rs2 = Wire(Bits(p(XLen).W)); rs2 := rocc_cmd.rs2

  /* NOTE: numFetchRuns MUST be wide enough to represent the maximum number of
   * memory operands to fetch! */
  val numFetchRuns = RegInit(0.U(p(XLen).W))
  val memActive = RegInit(false.B)
  val prev_fetch_res = RegInit(0.U(p(XLen).W))
  val fetchComplete = RegInit(false.B)

  
  ctrl_unit.io.mem_op_completed := data_fetcher.io.op_completed
  ctrl_unit.io.rs1 := rs1
  ctrl_unit.io.rs2 := rs2 
  data_fetcher.io.amountData := ctrl_unit.io.amount_data
  data_fetcher.io.write := ctrl_unit.io.should_write

  when(data_fetcher.io.op_completed) {
    when(numFetchRuns === 0.U){
      prev_fetch_res := data_fetcher.io.fetched_data.bits(0)
    }
    numFetchRuns := numFetchRuns + 1.U
    memActive := false.B
    
    if(p(VCodePrintfEnable)) {
      printf("VCode\tCompleted %d fetch runs.\n", numFetchRuns)
    }
  }

  
  // FIXME: Should not need to rely on mem_op_completed boolean
  when(!ctrl_unit.io.mem_op_completed && data_fetcher.io.baseAddress.ready && !memActive) {
    when(ctrl_unit.io.should_fetch){
      // Queue addrs and set valid bit
      data_fetcher.io.baseAddress.enq(ctrl_unit.io.addr_to_fetch)
      memActive := true.B
      // data_fetcher.io.addrs.enq(addrs)
      if(p(VCodePrintfEnable)) {
        printf("VCode\tStarting Run %d\n", numFetchRuns)
        printf("VCode\tEnqueued addresses to data fetcher\n")
        printf("\tBase Address: 0x%x\tvalid? %d\tAmountData: %d\n",
          data_fetcher.io.baseAddress.bits, data_fetcher.io.baseAddress.valid, data_fetcher.io.amountData)
      }
    } .otherwise{
      // Queue addrs and set valid bit
      data_fetcher.io.baseAddress.enq(ctrl_unit.io.addr_to_write)
      memActive := true.B
      // data_fetcher.io.addrs.enq(addrs)
      if(p(VCodePrintfEnable)) {
        printf("VCode\tStarting Run %d\n", numFetchRuns)
        printf("VCode\tEnqueued addresses to data fetcher\n")
        printf("\tBase Address: 0x%x\tvalid? %d\tAmountData: %d\n",
          data_fetcher.io.baseAddress.bits, data_fetcher.io.baseAddress.valid, data_fetcher.io.amountData)
      }
    }
    
  } .otherwise {
    data_fetcher.io.baseAddress.noenq()
  }
  data_fetcher.io.start := ctrl_unit.io.should_fetch || ctrl_unit.io.should_write

  val dmem_data = Wire(Bits(p(XLen).W)) // Data to SEND to memory

  val data1 = RegInit(0.U(p(XLen).W))
  val data2 = RegInit(0.U(p(XLen).W))
  val data3 = RegInit(0.U(p(XLen).W))
  val data4 = RegInit(0.U(p(XLen).W))
  val data5 = RegInit(0.U(p(XLen).W))
  val data6 = RegInit(0.U(p(XLen).W))
  val data7 = RegInit(0.U(p(XLen).W))
  val data8 = RegInit(0.U(p(XLen).W))

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
    when (ctrl_unit.io.num_to_fetch === 2.U){
      data1 := prev_fetch_res
      data2 := data_fetcher.io.fetched_data.bits(0)  
      if(p(VCodePrintfEnable)) {
        printf("VCode\tTwo Reads Done\n")
        printf("\tData 1: %d\tData 2: %d\n", prev_fetch_res, data_fetcher.io.fetched_data.bits(0))
      }
    }.otherwise{
      data1 := data_fetcher.io.fetched_data.bits(0)
      data2 := data_fetcher.io.fetched_data.bits(1)
      data3 := data_fetcher.io.fetched_data.bits(2)
      data4 := data_fetcher.io.fetched_data.bits(3)
      data5 := data_fetcher.io.fetched_data.bits(4)
      data6 := data_fetcher.io.fetched_data.bits(5)
      data7 := data_fetcher.io.fetched_data.bits(6)
      data8 := data_fetcher.io.fetched_data.bits(7)
      if(p(VCodePrintfEnable)) {
        printf("VCode\tFetcher1: 0x%x\tFetcher2: 0x%x\tFetcher3: 0x%x\tFetcher4: 0x%x\tFetcher5: 0x%x\tFetcher6: 0x%x\tFetcher7: 0x%x\tFetcher8: 0x%x\n",
        data_fetcher.io.fetched_data.bits(0), data_fetcher.io.fetched_data.bits(1), data_fetcher.io.fetched_data.bits(2), data_fetcher.io.fetched_data.bits(3), 
        data_fetcher.io.fetched_data.bits(4), data_fetcher.io.fetched_data.bits(5), data_fetcher.io.fetched_data.bits(6), data_fetcher.io.fetched_data.bits(7))
      }
    }
  }
  alu.io.in1 := data1
  alu.io.in2 := data2
  alu.io.in3 := data3
  alu.io.in4 := data4
  alu.io.in5 := data5
  alu.io.in6 := data6
  alu.io.in7 := data7
  alu.io.in8 := data8


  alu.io.execute := ctrl_unit.io.should_execute
  ctrl_unit.io.execution_completed := false.B
  when(alu.io.out1.valid) {
    when(ctrl_unit.io.num_to_fetch === 3.U){
      switch(ctrl_sigs.alu_fn){
        is(1.U){
          redAccum0 := redAccum0 + alu.io.out1.bits
          alu_out := redAccum0 + alu.io.out1.bits
          /** Accmulate partial results **/
          if(p(VCodePrintfEnable)) {
            printf("VCode\tALU \tout: 0x%x\t Accum: out: 0x%x\nALU Results and Accum!\n",
            alu.io.out1.bits, redAccum0)
          }
        }
        is(2.U){
          redAccum0 := redAccum0 | alu.io.out1.bits
          alu_out := redAccum0 | alu.io.out1.bits
        }
        is(3.U){
          redAccum1 := redAccum1 & alu.io.out1.bits
          alu_out := redAccum1 & alu.io.out1.bits
        }
      }
    } .otherwise{
      alu_out := alu.io.out1.bits
    }

    if(p(VCodePrintfEnable)) {
      printf("VCode\tALU in1: 0x%x\tin2: 0x%x\tout: 0x%x\nALU finished executing! Output bits now valid!\n",
      alu.io.in1, alu.io.in2, alu.io.out1.bits)
      printf("VCode\tFetcher1: 0x%x\tFetcher2: 0x%x\tFetcher3: 0x%x\tFetcher4: 0x%x\tFetcher5: 0x%x\tFetcher6: 0x%x\tFetcher7: 0x%x\tFetcher8: 0x%x\n",
      alu.io.in1, alu.io.in2, alu.io.in3, alu.io.in4, alu.io.in5, alu.io.in6, alu.io.in7, alu.io.in8)
    }
  }
  ctrl_unit.io.execution_completed := alu.io.out1.valid
  alu_cout := alu.io.cout
  
  /***************
   * WRITE_BACK
   **************/
  data_fetcher.io.write_data(0.U) := alu.io.out1.bits
  data_fetcher.io.write_data(1.U) := alu.io.out2
  data_fetcher.io.write_data(2.U) := alu.io.out3
  data_fetcher.io.write_data(3.U) := alu.io.out4
  data_fetcher.io.write_data(4.U) := alu.io.out5
  data_fetcher.io.write_data(5.U) := alu.io.out6
  data_fetcher.io.write_data(6.U) := alu.io.out7
  data_fetcher.io.write_data(7.U) := alu.io.out8

  ctrl_unit.io.dest_addr := destAddr;

  dmem_data := 0.U // FIXME: This is where write-back should happen

  val response_ready = Wire(Bool())
  response_ready := ctrl_unit.io.response_ready

  /***************
   * RESPOND
   **************/
  // Check if the accelerator needs to respond
  val response_required = RegInit(false.B)
  when(cmd_valid && ctrl_sigs.legal && rocc_cmd.inst.xd) {
    response_required := true.B
  }

  // Send response to main processor
  /* NOTE: RoCCResponse has an internal register to store the response. Using a
   * wire here is a non-issue because of it. */
  val response = Wire(new RoCCResponse)
  response.rd := returnReg
  response.data := alu_out
  io.resp.bits := response
  io.resp.valid := response_required && response_ready || exception
  when(rocc_io.resp.fire) {
    response_required := false.B
    cmd_valid := false.B
    redAccum0 := 0.U
    redAccum1 := ~(0.U)
    numFetchRuns := 0.U
  }

  when(response_required && response_ready) {
    if(p(VCodePrintfEnable)) {
      printf("Main processor ready for response? %d\n", io.resp.ready)
    }

    if(p(VCodePrintfEnable)) {
      printf("VCode accelerator made response with data 0x%x valid? %d\n",
        io.resp.bits.data, io.resp.valid)
    }
  }
}
