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

  // The accelerator is ready to execute if it is in the idle state
  io.accel_ready := (execute_state === State.idle)

  // We are busy if we are not idle.
  io.busy := (execute_state =/= State.idle)
  // NOTE: RoCC only requires that busy be asserted when memory requests and
  // responses are being made. Perhaps make this less strict?

  // We should fetch when we are in fetching data state
  io.should_fetch := (execute_state === State.fetchingData)
  val num_to_fetch = RegInit(0.U); io.num_to_fetch := num_to_fetch

  io.should_execute := (execute_state === State.exe)

  io.response_ready := (execute_state === State.write)

  switch(execute_state) {
    is(State.idle) {
      when(io.ctrl_sigs.legal && io.ctrl_sigs.is_mem_op) {
        execute_state := State.fetchingData
        if(p(VCodePrintfEnable)) {
          printf("Moving from idle to fetchingData state\n")
        }
      }
    }
    is(State.fetchingData) {
      num_to_fetch := io.ctrl_sigs.num_mem_fetches
      if(p(VCodePrintfEnable)) {
        printf("In fetchingData state\n")
      }
      when(io.fetching_completed) {
        execute_state := State.exe
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tMoving from fetchingData to exe state\n")
        }
      }
    }
    is(State.exe) {
      if(p(VCodePrintfEnable)) {
        printf("In execution state\n")
      }
      when(io.execution_completed) {
        execute_state := State.write
      }
    }
    is(State.write) {
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
