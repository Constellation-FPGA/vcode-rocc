package vcoderocc

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import freechips.rocketchip.config.Parameters

class ControlUnit(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ctrl_sigs = Input(new CtrlSigs())
    val cmd_valid = Input(Bool())
    val busy = Output(Bool())
    val accel_ready = Output(Bool())
    // TODO: Rework these booleans to an Enum which can be "exported"
    val should_fetch = Output(Bool())
    val num_to_fetch = Output(UInt())
    val mem_op_completed = Input(Bool())
    val should_execute = Output(Bool())
    val execution_completed = Input(Bool())
    val response_ready = Output(Bool())
    val response_completed = Input(Bool())
    val rs1 = Input(UInt())
    val rs2 = Input(UInt())
    val dest_addr = Input(UInt())
    val amount_data = Output(UInt())
    val addr_to_fetch = Output(UInt())
    val addr_to_write = Output(UInt())
    val should_write = Output(Bool())
  })

  object State extends ChiselEnum {
    /* Internally (in Verilog) represented as integers. First item in list has
     * value 0, i.e. idle = 0x0. */
    val idle, fetchingData, exe, write, respond = Value
  }
  val execute_state = RegInit(State.idle) // Reset to idle state

  // The accelerator is ready to execute if it is in the idle state
  io.accel_ready := (execute_state === State.idle)

  // We are busy if we are not idle.
  io.busy := (execute_state =/= State.idle)
  // NOTE: RoCC only requires that busy be asserted when memory requests and
  // responses are being made. Perhaps make this less strict?

  // We should fetch when we are in fetching data state
  io.should_fetch := (execute_state === State.fetchingData)
  io.num_to_fetch := io.ctrl_sigs.num_mem_fetches

  io.should_execute := (execute_state === State.exe)

  io.should_write := (execute_state === State.write)

  io.response_ready := (execute_state === State.respond)

  val runsDone = RegInit(0.U(64.W))
  val runsRequired = Wire(UInt())

  when(io.ctrl_sigs.num_mem_fetches === 3.U){
    runsRequired := ((io.rs2 - 1.U) >> 3.U) + 1.U
    when ((io.rs2 - (runsDone << 3.U)) <= 8.U){
      io.amount_data := io.rs2 - (runsDone << 3.U)
    }.otherwise{
      io.amount_data := 8.U
    }
  } .otherwise{
    io.amount_data := 1.U
    runsRequired := io.ctrl_sigs.num_mem_fetches
  }

  val addrToFetchMop2 = Mux(runsDone === 0.U, io.rs1, io.rs2)
  val addrToFetchMopN = io.rs1 + (runsDone << 6.U)
  io.addr_to_fetch := Mux(io.ctrl_sigs.num_mem_fetches === 3.U, addrToFetchMopN, addrToFetchMop2)

  val addrToWrite = io.dest_addr + (runsDone << 6.U)
  io.addr_to_write := addrToWrite

  switch(execute_state) {
    is(State.idle) {
      when(io.cmd_valid && io.ctrl_sigs.legal && io.ctrl_sigs.is_mem_op) {
        runsDone := 0.U
        execute_state := State.fetchingData
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tMoving from idle to fetchingData state\n")
        }
      }
    }
    is(State.fetchingData) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tIn fetchingData state\n")
      }
      when(io.mem_op_completed) {
        execute_state := State.exe
        runsDone := runsDone + 1.U
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tMoving from fetchingData to exe state\n")
        }
      }
    }
    is(State.exe) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tIn execution state\n")
      }
      when(io.execution_completed) {
        when(io.ctrl_sigs.num_mem_writes === 0.U){
          // No need to write anything
          when(runsDone >= runsRequired){
            execute_state := State.respond
            if(p(VCodePrintfEnable)) {
              printf("Ctrl\tMoving from exe state to respond state\n")
            }
            // Also done with operations
          }.otherwise{
            execute_state := State.fetchingData
            if(p(VCodePrintfEnable)) {
              printf("Ctrl\tMoving from exe state to fetch state\n")
            }
            // Keep fetching
          }
        }.otherwise{
          execute_state := State.write
          if(p(VCodePrintfEnable)) {
            printf("Ctrl\tMoving from exe state to write state\n")
          }
          // Move to writing stage
        }
      }
    }
    is(State.write){
      when(io.mem_op_completed) {
        execute_state := State.fetchingData
        runsDone := runsDone + 1.U
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tMoving from write to fetch state\n")
        }
      }      
    }
    is(State.respond) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tExecution done. Returning result\n")
      }
      when(io.response_completed) {
        execute_state := State.idle
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tResponse sent. Returning to idle state\n")
        }
      }
    }
  }
}
