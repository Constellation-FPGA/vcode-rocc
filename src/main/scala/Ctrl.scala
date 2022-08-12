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
  })

  // 4 states. Nil is End-of-list and not counted.
  val idle :: fetchingData :: exe :: write :: Nil = Enum(4)
  val execute_state = RegInit(idle) // Reset to idle state

  val busy = RegInit(false.B); io.busy := busy

  switch(execute_state) {
    is(idle) {
    }
    is(fetchingData) {
      busy := true.B
    }
    is(exe) {
    }
    is(write) {
    }
  }
}
