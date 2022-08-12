package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.rocket.HellaCacheIO
import freechips.rocketchip.tile.{XLen, RoCCCommand, RoCCResponse}
import freechips.rocketchip.config._

class ControlUnit(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val cmd = Input(new RoCCCommand())
    val response = Output(new RoCCResponse())
    val ctrl_sigs = Input(new CtrlSigs())
    // val mem = Input(new HellaCacheIO)
    val exception = Output(Bool())
    val busy = Output(Bool())
  })

  // 4 states. Nil is End-of-list and not counted.
  val idle :: fetchingData :: exe :: write :: Nil = Enum(4)
  val execute_state = RegInit(idle) // Reset to idle state

  val data1 = Wire(Bits(p(XLen).W))
  val data2 = Wire(Bits(p(XLen).W))

  switch(execute_state) {
    is(idle) {
    }
    is(fetchingData) {
    }
    is(exe) {
    }
    is(write) {
    }
  }

  data1 := io.cmd.rs1
  data2 := io.cmd.rs2

  io.busy := false.B // FIXME: Set to proper value
  io.exception := false.B // FIXME: Set to proper value

  io.response.rd := io.cmd.inst.rd
  io.response.data := 0.U
}
