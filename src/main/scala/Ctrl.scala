package vcoderocc

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import freechips.rocketchip.config.Parameters

class ControlUnit(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ctrl_sigs = Input(new CtrlSigs())
    val busy = Output(Bool())
    val accel_ready = Output(Bool())
    // TODO: Rework these booleans to an Enum which can be "exported"
    val should_fetch = Output(Bool())
    val num_to_fetch = Output(UInt())
    val fetching_completed = Input(Bool())
    val should_execute = Output(Bool())
    val execution_completed = Input(Bool())
    val response_ready = Output(Bool())
    val response_completed = Input(Bool())
  })

  object State extends ChiselEnum {
    val idle, fetchingData, exe, write = Value
  }
  val execute_state = RegInit(State.idle) // Reset to idle state

  val busy = RegInit(false.B); io.busy := busy
  val should_fetch = RegInit(false.B); io.should_fetch := should_fetch
  val num_to_fetch = RegInit(0.U); io.num_to_fetch := num_to_fetch

  val should_execute = RegInit(false.B); io.should_execute := should_execute

  val response_ready = RegInit(false.B); io.response_ready := response_ready

  // The accelerator is ready to execute if it is in the idle state
  io.accel_ready := execute_state === State.idle

  switch(execute_state) {
    is(State.idle) {
      response_ready := false.B
      when(io.ctrl_sigs.legal && io.ctrl_sigs.is_mem_op) {
        execute_state := State.fetchingData
        if(p(VCodePrintfEnable)) {
          printf("Moving from idle to fetchingData state\n")
        }
      }
    }
    is(State.fetchingData) {
      busy := true.B
      should_fetch := true.B
      num_to_fetch := io.ctrl_sigs.num_mem_fetches
      if(p(VCodePrintfEnable)) {
        printf("In fetchingData state\n")
      }
      when(io.fetching_completed) {
        execute_state := State.exe
      }
    }
    is(State.exe) {
      should_fetch := false.B
      should_execute := true.B
      if(p(VCodePrintfEnable)) {
        printf("In execution state\n")
      }
      when(io.execution_completed) {
        execute_state := State.write
      }
    }
    is(State.write) {
      should_execute := false.B
      response_ready := true.B
      if(p(VCodePrintfEnable)) {
        printf("Execution done. Returning result\n")
      }
      when(io.response_completed) {
        execute_state := State.idle
        if(p(VCodePrintfEnable)) {
          printf("Response sent. Returning to idle state\n")
        }
      }
    }
  }
}
