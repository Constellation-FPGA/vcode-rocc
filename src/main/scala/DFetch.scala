package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.{XLen, CoreModule, RoCCCommand}
import freechips.rocketchip.rocket.constants.MemoryOpConstants
import vcoderocc.constants._
import freechips.rocketchip.rocket.MStatus
import freechips.rocketchip.rocket.{HellaCacheReq, HellaCacheResp}

/* TODO: Investigate if we should use RoCCCoreIO.mem (DCacheFetcher) or
 * LazyRoCC.tlNode (DMemFetcher).
 * RoCCCoreIO.mem connects to the local main processor's L1 D$ to perform
 * operations.
 * LazyRoCC.tlNode connects to the L1-L2 crossbar connecting this tile to the
 * larger system. */

// FIXME: Use another structure? Vec perhaps?
class AddressBundle(addrWidth: Int) extends Bundle {
  val addr1 = Bits(addrWidth.W)
  val addr2 = Bits(addrWidth.W)
}

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
    /** The addresses to fetch. */
    val addrs = Flipped(Decoupled(new AddressBundle(p(XLen))))
    // Actual Data outputs
    val fetched_data = Output(Valid(new Bundle {
      val data1 = Bits(p(XLen).W)
      val data2 = Bits(p(XLen).W)
    }))
    val should_fetch = Input(Bool())
    val num_to_fetch = Input(UInt())
    val req = Decoupled(new HellaCacheReq)
    val resp = Flipped(Valid(new HellaCacheResp))
    val fetching_completed = Output(Bool())
  })

  val idle :: fetching :: Nil = Enum(2)
  val state = RegInit(idle)
  var amount_fetched = RegInit(0.U)

  val vals = Mem(2, UInt(p(XLen).W)) // Only need max of 2 memory slots for now

  val fetching_completed = RegInit(false.B); io.fetching_completed := fetching_completed

  switch(state) {
    is(idle) {
      io.addrs.ready := true.B // TODO: Dequeue at most once
      fetching_completed := false.B
      when(io.should_fetch) {
        state := fetching
        if(p(VCodePrintfEnable)) {
          printf("Starting to fetch data\n")
        }
      }
    }
    is(fetching) {
      io.addrs.nodeq() // Set ready to false
      when(amount_fetched >= io.num_to_fetch) {
        // We have fetched everything we needed to fetch. We are done.
        if(p(VCodePrintfEnable)) {
          printf("Fetched all the data. Fetcher returns to idle. Do next thing\n")
        }
        state := idle
        fetching_completed := true.B
        amount_fetched := 0.U
      } .otherwise {
        // We still have a request to make. We may still have outstanding responses too.
        state := fetching
        when(true.B){
          // amount_fetched += 1.U
          amount_fetched := io.num_to_fetch
        }
        if(p(VCodePrintfEnable)) {
          printf("still fetching data, num_to_fetch:%d amount_fetched:%d \n",io.num_to_fetch,amount_fetched)
        }
        // when(io.resp.valid) {


      }
    }
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

object NumOperatorOperands {
  /** The size of the bit pattern for number of operands for operators. */
  val SZ_MEM_OPS = 2.W
  /** Unknown number of operands */
  def MEM_OPS_X = BitPat("b??")
  /** No operands. Intended for accelerator control instructions. */
  def MEM_OPS_ZERO = BitPat("b00")
  /** One operands to fetch from memory. */
  def MEM_OPS_ONE = BitPat("b01")
  /** Two operands to fetch from memory. */
  def MEM_OPS_TWO = BitPat("b10")
}
