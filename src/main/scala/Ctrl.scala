package vcoderocc

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.{XLen, RoCCCommand}

object SourceOperand extends ChiselEnum {
  val none, rs1, rs2 = Value
}

class ControlUnit(val batchSize: Int)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val roccCmd = Input(new RoCCCommand())
    val ctrl_sigs = Input(new CtrlSigs())
    val cmd_valid = Input(Bool())
    val busy = Output(Bool())
    val accel_ready = Output(Bool())
    // TODO: Rework these booleans to an Enum which can be "exported"
    val should_fetch = Output(Bool())
    // FIXME: rs1Fetch is hacky work-around to distinguish rs1 vs rs2 fetching
    // This would be fixed by the enum export method!
    val rs1Fetch = Output(Bool())
    val baseAddress = Output(UInt(p(XLen).W))
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

  // Configuration registers. Set by set-up instructions
  // TODO: Figure out better way to declare these configuration registers
  // Perhaps a separate module that handles this? class ConfigBank extends Module {}
  val numOperands = RegInit(0.U(p(XLen).W))
  val operandsToGo = RegInit(0.U(p(XLen).W))

  val rs1 = RegInit(0.U(p(XLen).W))
  val rs2 = RegInit(0.U(p(XLen).W))
  val destAddr = RegInit(0.U(p(XLen).W))
  val currentRs1 = RegInit(0.U(p(XLen).W))
  val currentRs2 = RegInit(0.U(p(XLen).W))
  val currentDestAddr = RegInit(0.U(p(XLen).W))


  // The accelerator is ready to execute if it is in the idle state
  io.accel_ready := (accel_state === State.idle)

  // We are busy if we are not idle.
  io.busy := (accel_state =/= State.idle)
  // NOTE: RoCC only requires that busy be asserted when memory requests and
  // responses are being made. Perhaps make this less strict?

  // We should fetch when we are in fetching data state
  io.should_fetch := (accel_state === State.fetch1 || accel_state === State.fetch2)
  // FIXME: This num_to_fetch is a little bit messy.
  io.num_to_fetch := Mux(operandsToGo >= batchSize.U, batchSize.U, operandsToGo)
  io.rs1Fetch := accel_state === State.fetch1

  io.baseAddress := Mux(accel_state === State.write, currentDestAddr,
    Mux(accel_state === State.fetch1, currentRs1, currentRs2))

  io.should_execute := (accel_state === State.exe)

  io.writeback_ready := (accel_state === State.write)

  io.response_ready := (accel_state === State.respond)

  // TODO: Simplify the use of non-blocking assignments to set up the accelerator
  /* NOTE: Configuration commands do NOT change the accelerator's control unit's
   * state! This is because the control unit's FSM is meant to organize the
   * execution of vector operations. Config commands can be handled in 1 cycle. */
  when(io.cmd_valid && io.ctrl_sigs.legal &&
       io.roccCmd.inst.funct === Instructions.SET_NUM_OPERANDS && io.roccCmd.inst.xs1) {
    numOperands := io.roccCmd.rs1
    operandsToGo := io.roccCmd.rs1
    if(p(VCodePrintfEnable)) {
      printf("Config\tSet numOperands to 0x%x\n", io.roccCmd.rs1)
    }
  }

  when(io.cmd_valid && io.ctrl_sigs.legal &&
       io.roccCmd.inst.funct === Instructions.SET_DEST_ADDR && io.roccCmd.inst.xs1) {
    destAddr := io.roccCmd.rs1
    currentDestAddr := io.roccCmd.rs1
    if(p(VCodePrintfEnable)) {
      printf("Config\tSet destAddr to 0x%x\n", io.roccCmd.rs1)
    }
  }

  switch(accel_state) {
    is(State.idle) {
      when(io.cmd_valid && io.ctrl_sigs.legal && io.ctrl_sigs.is_mem_op) {
        accel_state := State.fetch1
        // If we leave idle, we should grab the source addresses
        rs1 := io.roccCmd.rs1; rs2 := io.roccCmd.rs2
        currentRs1 := io.roccCmd.rs1; currentRs2 := io.roccCmd.rs2
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
        // If execution completed, but this is a reduction
        when(io.ctrl_sigs.alu_fn === ALU.FN_RED_ADD) {
          // FIXME: Turn this into a function?
          // Decrement our "counter"
          val remainingOperands = Mux(operandsToGo <= batchSize.U, 0.U, operandsToGo - batchSize.U)
          operandsToGo := remainingOperands
          when(remainingOperands > 0.U) {
            // We have not yet completed the reduction, go back.
            accel_state := State.fetch1
            // Multiply address by 8 because all values use 64 bits
            currentRs1 := currentRs1 + (batchSize.U << 3)
            currentRs2 := currentRs2 + (batchSize.U << 3)
          } .otherwise {
            // The reduction's computation is complete, write.
            accel_state := State.write
            // Set operandsToGo to 1 to write reduction's single result
            operandsToGo := 1.U
          }
        } .otherwise {
          // Execution completed, but this is NOT a reduction
          accel_state := State.write
          if(p(VCodePrintfEnable)) {
            printf("Ctrl\tMoving from exe state to write state\n")
          }
        }
      }
    }
    is(State.write) {
      if(p(VCodePrintfEnable)) {
        printf("Ctrl\tExecution done. Writeback results\n")
      }
      when(io.mem_op_completed) {
        // Decrement our "counter"
        val remainingOperands = Mux(operandsToGo <= batchSize.U, 0.U, operandsToGo - batchSize.U)
        operandsToGo := remainingOperands
        when(remainingOperands > 0.U) {
          // We have not yet completed the vector, go back.
          accel_state := State.fetch1
          // Multiply address by 8 because all values use 64 bits
          currentRs1 := currentRs1 + (batchSize.U << 3)
          currentRs2 := currentRs2 + (batchSize.U << 3)
          currentDestAddr := destAddr + (batchSize.U << 3)
        } .otherwise {
          // We have finished processing the vector. Move onwards.
          accel_state := State.respond
          if(p(VCodePrintfEnable)) {
            printf("Ctrl\tWriteback completed. Accelerator must respond to main core\n")
          }
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
