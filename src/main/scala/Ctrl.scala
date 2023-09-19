package vcoderocc

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.XLen

object SourceOperand extends ChiselEnum {
  val none, rs1, rs2 = Value
}

class ControlUnit(val batchSize: Int)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val ctrl_sigs = Input(new CtrlSigs())
    val cmd_valid = Input(Bool())
    val busy = Output(Bool())
    val accel_ready = Output(Bool())
    // TODO: Rework these booleans to an Enum which can be "exported"
    val should_fetch = Output(Bool())
    val sourceToFetch = Output(SourceOperand())
    val num_to_fetch = Output(UInt(p(XLen).W))
    val mem_op_completed = Input(Bool())
    val should_execute = Output(Bool())
    val execution_completed = Input(Bool())
    val writeback_ready = Output(Bool())
    // Writeback completion is marked by mem_op_completed
    val response_ready = Output(Bool())
    val response_completed = Input(Bool())
  })

  object State extends ChiselEnum {
    /* Internally (in Verilog) represented as integers. First item in list has
     * value 0, i.e. idle = 0x0. */
    val idle, fetch1, fetch2, exe, write, respond = Value
  }
  val accel_state = RegInit(State.idle) // Reset to idle state

  // The accelerator is ready to execute if it is in the idle state
  io.accel_ready := (accel_state === State.idle)

  // We are busy if we are not idle.
  io.busy := (accel_state =/= State.idle)
  // NOTE: RoCC only requires that busy be asserted when memory requests and
  // responses are being made. Perhaps make this less strict?

  // We should fetch when we are in fetching data state
  io.should_fetch := (accel_state === State.fetch1 || accel_state === State.fetch2)
  io.num_to_fetch := Mux(accel_state === State.fetch1 || accel_state === State.fetch2,
    io.ctrl_sigs.num_mem_fetches, 0.U)
  io.sourceToFetch := Mux(accel_state === State.fetch1, SourceOperand.rs1,
    Mux(accel_state === State.fetch2, SourceOperand.rs2,
      SourceOperand.none))

  io.should_execute := (accel_state === State.exe)

  io.writeback_ready := (accel_state === State.write)

  io.response_ready := (accel_state === State.respond)

  switch(accel_state) {
    is(State.idle) {
      when(io.cmd_valid && io.ctrl_sigs.legal && io.ctrl_sigs.is_mem_op) {
        accel_state := State.fetch1
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tMoving from idle to fetch1 state\n")
        }
      }
    }
    is(State.fetch1) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tIn fetch1 state\n")
      }
      when(io.mem_op_completed) {
        when(io.ctrl_sigs.num_mem_fetches === NumOperatorOperands.MEM_OPS_TWO) {
          if(p(VCodePrintfEnable)) {
            printf("Ctrl\tMoving from fetch1 to fetch2 state\n")
          }
          accel_state := State.fetch2
        } .otherwise {
          if(p(VCodePrintfEnable)) {
            printf("Ctrl\tMoving from fetch1 to exe state\n")
          }
          accel_state := State.exe
        }
      }
    }
    is(State.fetch2) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tIn fetch2 state\n")
      }
      when(io.mem_op_completed) {
        accel_state := State.exe
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tMoving from fetch2 to exe state\n")
        }
      }
    }
    is(State.exe) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tIn execution state\n")
      }
      when(io.execution_completed) {
        accel_state := State.write
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
        accel_state := State.respond
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
        accel_state := State.idle
        if(p(VCodePrintfEnable)) {
          printf("Ctrl\tResponse sent. Returning to idle state\n")
        }
      }
    }
  }
}
