package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.{XLen, CoreModule, RoCCCommand, RoCCResponse}
import freechips.rocketchip.rocket.{HellaCacheIO, HellaCacheReq, MStatus}
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import freechips.rocketchip.config._
import vcoderocc.constants._

class ControlUnit(implicit p: Parameters) extends CoreModule()(p) with MemoryOpConstants {
  val io = IO(new Bundle {
    // RoCCCoreIO passthrough
    val cmd = Input(new RoCCCommand())
    val response = Output(new Bundle {
      val rd = Bits(5.W)
      val data = Bits(p(XLen).W)
    })
    /* For now, we only support "raw" loading and storing.
     * Only using M_XRD and M_XWR */
    val mem = new Bundle {
      val req_valid  = Output(Bool())
      val req_ready  = Input(Bool())
      val req_tag    = Output(Bits(coreParams.dcacheReqTagBits.W))
      val req_addr   = Output(Bits(coreMaxAddrBits.W))
      val req_cmd    = Output(Bits(M_SZ.W))
      val req_size   = Output(Bits(log2Ceil(coreDataBytes + 1).W))
      val resp_valid = Input(Bool())
      val resp_tag   = Input(Bits(7.W))
      val resp_data  = Input(Bits(p(XLen).W))
    }
    // Accelerator-internal Control signals
    val ctrl_sigs = Input(new CtrlSigs())
    // Special signals
    val exception = Output(Bool())
    val busy = Output(Bool())
    // val sfence = Output(Bool())
  })

  // 4 states. Nil is End-of-list and not counted.
  val idle :: fetchingData :: exe :: write :: Nil = Enum(4)
  val execute_state = RegInit(idle) // Reset to idle state

  val data1 = Wire(Bits(p(XLen).W))
  val data2 = Wire(Bits(p(XLen).W))

  io.mem.req_tag := 0.U(6.W)
  io.mem.req_size := BitPat.bitPatToUInt(MT64)
  io.mem.req_cmd := M_XRD
  io.mem.req_addr := io.cmd.rs1
  io.mem.req_valid := false.B // TODO: Make request valid to initiate transfer
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
  io.sfence := false.B
}
