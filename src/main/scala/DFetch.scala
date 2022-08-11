package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.CoreModule
import freechips.rocketchip.rocket.constants.MemoryOpConstants
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
  * @param p Implicit parameter passed by build system of top-level design parameters.
  */
class DCacheFetcher(implicit p: Parameters) extends CoreModule()(p)
    with MemoryOpConstants {
  /* For now, we only support "raw" loading and storing.
   * Only using M_XRD and M_XWR */
  val io = IO(new Bundle {
    val busy        = Output(Bool()) // Signal out-standing memory operations
    // Ready/Valid signals for request queue
    val req_valid   = Output(Bool())
    val req_ready   = Input(Bool())
    // Data signals for request queue
    val req_tag     = Output(Bits(coreParams.dcacheReqTagBits.W))
    val req_addr    = Output(Bits(coreMaxAddrBits.W))
    val req_cmd     = Output(Bits(M_SZ.W))
    val req_size    = Output(Bits(log2Ceil(coreDataBytes + 1).W)) // 64 bits

    val resp_valid  = Input(Bool())
    val resp_tag    = Input(Bits(coreParams.dcacheReqTagBits.W))
    val resp_data   = Input(Bits(64.W))
  })

  // We can only submit a request if we can accept a response
  // rocc_io.req.ready := io.mem.ready

  // A memory request can only be valid if the request is valid
  // rocc_io.mem.valid := io.req.valid
  //
  // rocc_io.mem.bits := io.req.bits
  // io.mem.bits.addr :=
}

/** Class to handle the actual details of fetching and organizing the memory
  * requests that were submitted.
  *
  * Needed because memory responses can be returned out-of-order from
  * submission, and we need a way to recover the proper ordering. This is
  * particularly important for VCode's pairwise vector operations.
  *
  * @param p Implicit parameter passed by build system of top-level design parameters.
  */
private class DCacheHandler(implicit p: Parameters) extends CoreModule()(p) {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new HellaCacheReq)) // Cache/Memory Requests
    val mem = Decoupled(new HellaCacheReq)          // Memory Responses
    val status = Input(new MStatus())               // Status of memory
    val sfence = Input(Bool())
  })
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
