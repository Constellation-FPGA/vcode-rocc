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
    val writeback_ready = Output(Bool())
    val response_ready = Output(Bool())
    val response_completed = Input(Bool())
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
  io.num_to_fetch := Mux(execute_state === State.fetchingData, io.ctrl_sigs.num_mem_fetches, 0.U)

  io.should_execute := (execute_state === State.exe)

  io.writeback_ready := (execute_state === State.write)

  io.response_ready := (execute_state === State.respond)

  switch(execute_state) {
    is(State.idle) {
      when(io.cmd_valid && io.ctrl_sigs.legal && io.ctrl_sigs.is_mem_op) {
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
        execute_state := State.write
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tMoving from exe state to write state\n")
        }
      }
    }
    is(State.write) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tExecution done. Writeback results\n")
      }
      when(io.mem_op_completed) {
        execute_state := State.respond
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tWriteback completed. Accelerator must respond to main core\n")
        }
      }
    }
    is(State.respond) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tWriteback done. Accelerator responding\n")
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
