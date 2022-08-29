package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.rocket.HellaCacheIO
import freechips.rocketchip.tile.{XLen, RoCCCommand, RoCCResponse}
import freechips.rocketchip.config._

class ControlUnit(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cmd = Input(new RoCCCommand())
    val ctrl_sigs = Input(new CtrlSigs())
    val busy = Output(Bool())
    // TODO: Rework these booleans to an Enum which can be "exported"
    val should_fetch = Output(Bool())
    val should_execute = Output(Bool())
  })

  // 4 states. Nil is End-of-list and not counted.
  val idle :: fetchingData :: exe :: write :: Nil = Enum(4)
  val execute_state = RegInit(idle) // Reset to idle state

  val busy = RegInit(false.B); io.busy := busy
  val should_fetch = RegInit(false.B); io.should_fetch := should_fetch
  val should_execute = RegInit(false.B); io.should_execute := should_execute

  switch(execute_state) {
    is(idle) {
    }
    is(fetchingData) {
      busy := true.B
      should_fetch := true.B
    }
    is(exe) {
      should_fetch := false.B
      should_execute := true.B
    }
    is(write) {
      should_execute := false.B
    }
  }
}
