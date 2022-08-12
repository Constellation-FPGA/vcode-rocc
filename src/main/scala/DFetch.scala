package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.{XLen, CoreModule, RoCCCommand}
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import vcoderocc.constants._
import freechips.rocketchip.rocket.MStatus
import freechips.rocketchip.rocket.HellaCacheReq

/* TODO: Investigate if we should use RoCCCoreIO.mem (DCacheFetcher) or
 * LazyRoCC.tlNode (DMemFetcher).
 * RoCCCoreIO.mem connects to the local main processor's L1 D$ to perform
 * operations.
 * LazyRoCC.tlNode connects to the L1-L2 crossbar connecting this tile to the
 * larger system. */

/** Module connecting VCode accelerator to main processor's non-blocking L1 data
  * cache.
  *
  * Handles the actual details of fetching and organizing the memory requests
  * that were submitted.
  *
  * Needed because memory responses can be returned out-of-order from
  * submission, and we need a way to recover the proper ordering. This is
  * particularly important for VCode's pairwise vector operations.
  *
  * @param p Implicit parameter passed by build system of top-level design parameters.
  */
class DCacheFetcher(implicit p: Parameters) extends CoreModule()(p)
    with MemoryOpConstants {
  /* For now, we only support "raw" loading and storing.
   * Only using M_XRD and M_XWR */
  val io = IO(new Bundle {
    val cmd         = Input(new RoCCCommand) // Need RoCC command to interact with mem
    val busy        = Output(Bool()) // Signal out-standing memory operations
    // Ready/Valid signals for request queue
    val req_valid   = Output(Bool()) // Request passed to subsystem is valid?
    val req_ready   = Input(Bool())  // Subsystem ready to receive request
    // Data signals for request queue
    // Outputs because they output TO a DecoupledIO[HellaCacheReq]
    val req_tag     = Output(Bits(coreParams.dcacheReqTagBits.W))
    val req_addr    = Output(Bits(coreMaxAddrBits.W))
    val req_cmd     = Output(Bits(M_SZ.W))
    val req_size    = Output(Bits(log2Ceil(coreDataBytes + 1).W)) // 64 bits
    // val req_data    = Output(Bits(64.W)) // FIXME: Use symbol for the 64

    // Inputs because DecoupledIO[HellaCacheReq] returns data TO cache fetcher
    val resp_valid  = Input(Bool())
    val resp_tag    = Input(Bits(coreParams.dcacheReqTagBits.W))
    val resp_data   = Input(Bits(64.W)) // FIXME: Use symbol for the 64

    // Actual Data outputs
    val data1 = Output(Bits(p(XLen).W))
    val data2 = Output(Bits(p(XLen).W))
  })

  when(io.req_ready) {
    io.req_tag   := 0.U // Tag is 7 bits
    io.req_cmd   := M_XRD // Performing read
    io.req_addr := io.cmd.rs1 // Address to read
    // FIXME
    // data_ctrl.io.req_size  := MemorySizeConstants.MT64 // Xfer size
    io.req_valid := true.B
    io.busy := true.B
  }

  when(io.resp_valid) {
    io.data1 := io.resp_data // FIXME: For now, we assume tag is correct
    io.busy := false.B
  }

  /** Internal buffer to organize and synchronize the two operands' data. */
  // val buffer = new Bundle {
  //   val myWidth = 64
  //   // The backing buffer implementation
  //   val mem = Mem(128, Clock()) // FIXME: Clock?? Holds 2 64-bit values
  //   // Addressing
  //   val r_addr = Reg(UInt(2.W)) // FIXME: This hardcoded 2 should be a design parameter based on memory size
  //   val w_addr = Wire(UInt(myWidth.W)); w_addr := 0.U
  //   // Data
  //   val r_data = Bits(myWidth.W)
  //   val w_data = Wire(UInt(myWidth.W)); w_data := 0.U
  //   // Enable
  //   val w_enable = Wire(Bool())
  // }
}

/** Module connecting VCode accelerator directly to the L1-L2 crossbar connecting
  * all tiles to other components in the system.
  *
  * @param p Implicit parameter passed by build system of top-level design parameters.
  */
class DMemFetcher(implicit p: Parameters) extends CoreModule()(p)
    with MemoryOpConstants {
  val io = IO(new Bundle {})
}
