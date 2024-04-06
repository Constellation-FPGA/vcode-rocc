package vcoderocc

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
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

object MemoryOperation extends ChiselEnum {
  val read, write = Value
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
  * @param bufferEntries Ceiling of the number of elements to batch together
  *        before returning data to another component. Total size is
  *        bufferEntries * XLen.
  * @param p Implicit parameter passed by build system of top-level design parameters.
  *
  * freechips.rocketchip.rocket.constants.MemoryOpConstants provides named bit
  * patterns for submitting requests to the memory system.
  *
  * From Chipyard documentation:
  * RoCC accelerator can access memory through the L1 Cache of the core it is
  * attached to. This is a simpler interface for accelerator architects to
  * implement, but will generally have lower achievable throughput than a dedicated
  * TileLink port.
  */
class DCacheFetcher(val bufferEntries: Int)(implicit p: Parameters) extends CoreModule()(p)
    with MemoryOpConstants {
  /* For now, we only support "raw" loading and storing.
   * Only using M_XRD and M_XWR */
  val io = IO(new Bundle {
    val ctrlSigs = Input(new CtrlSigs)
    /** The base address from which to operate on (load from/store to). */
    val baseAddress = Flipped(Decoupled(Bits(p(XLen).W)))
    val mstatus = Input(new MStatus)
    val opToPerform = Input(MemoryOperation()) // NOTE: The () is important!
    // Actual Data outputs
    // fetched_data is only of interest if a read was performed
    val fetchedData = Output(Valid(Vec(bufferEntries, Bits(p(XLen).W))))
    val dataToWrite = Input(Valid(Vec(bufferEntries, Bits(p(XLen).W))))
    /** Flag to tell DCacheFetcher to start loading/storing from/to memory. */
    val start = Input(Bool())
    /** The number of elements to fetch. */
    val amountData = Input(UInt())
    /** Has the requested operation been completed? */
    val opCompleted = Output(Bool())
    val req = Decoupled(new HellaCacheReq)
    val resp = Input(Valid(new HellaCacheResp))
  })

  /* The amount of data this module can handle in one round of fetching must
   * always be less than the maximum size of this module's internal buffer. */
  assert(io.amountData <= bufferEntries.U,
    "DCacheFetcher: Amount of data to handle > internal buffer!")

  object State extends ChiselEnum {
    val idle, running = Value
  }
  val state = RegInit(State.idle)

  // NOTE: 0.U implies a 1-bit unsigned integer. Need to explicitly state width
  /* log2Up(bufferEntries)+1.W means amountFetched is exactly big enough to count
   * bufferEntries number of elements and NOT wrap around. */
  // Number of requests that have been fulfilled.
  val amountFetched = RegInit(0.U((log2Up(bufferEntries)+1).W))
  // Number of requests that have been sent.
  val reqsSent = RegInit(0.U(8.W))

  val vals = withReset(state === State.idle) {
    RegInit(VecInit.fill(bufferEntries)(0.U(p(XLen).W)))
  }

  val waitForResp = RegInit(VecInit.fill(bufferEntries)(false.B))
  val allDone = Wire(Bool()); allDone := !(waitForResp.reduce(_ || _))

  // Operation completed when running & requests fulfilled >= amount of data requested
  io.opCompleted := (state === State.running) && (amountFetched >= io.amountData)
  // We can accept a new base address when we are idle.
  io.baseAddress.ready := (state === State.idle)

  switch(state) {
    is(State.idle) {
      amountFetched := 0.U; reqsSent := 0.U
      when(io.start && io.baseAddress.valid) {
        state := State.running
        if(p(VCodePrintfEnable)) {
          printf("DFetch\tStarting to fetch data\n")
        }
      }
    }
    is(State.running) {
      when(amountFetched >= io.amountData) {
        // We have fetched everything we needed to fetch. We are done.
        if(p(VCodePrintfEnable)) {
          printf("DFetch\tFetched all the data. Fetcher returns to idle. Do next thing\n")
          printf("DFetch\tdata1: 0x%x\tdata2: 0x%x\n",
            vals(0.U), vals(1.U))
        }
        state := State.idle
        io.fetchedData.bits := vals
        io.fetchedData.valid := allDone
        amountFetched := 0.U; reqsSent := 0.U
      } .otherwise {
        // We still have a request to make. We may still have outstanding responses too.
        state := State.running
        if(p(VCodePrintfEnable)) {
          printf("Fetching data, amountData: %d\tamount_fetched: %d\n",
            io.amountData, amountFetched)
        }

        // We have a response to handle!
        when(io.resp.valid) {
          if(p(VCodePrintfEnable)) {
            printf("DFetch\tGot cache response for tag 0x%x!\n", io.resp.bits.tag)
            printf("DFetch\tTag 0x%x data: 0x%x\n", io.resp.bits.tag, io.resp.bits.data)
          }
          vals(io.resp.bits.tag) := io.resp.bits.data
          when(waitForResp(io.resp.bits.tag)) {
            // If we were waiting for a response on this tag, and we now have
            // that tags response, then we increase the amount we fetch.
            amountFetched := amountFetched + 1.U
            waitForResp(io.resp.bits.tag) := false.B
            if(p(VCodePrintfEnable)) {
              printf("DFetch\tMarking tag 0x%x as done\n", io.resp.bits.tag)
              printf("DFetch\tamount_fetched: %d\tdata: 0x%x\n", amountFetched + 1.U, io.resp.bits.data)
            }
          } .otherwise {
            if(p(VCodePrintfEnable)) {
              printf("DFetch\tAlready got response for tag 0x%x. Doing nothing\n", io.resp.bits.tag)
            }
          }
        }

        // We should submit a memory request!
        when(io.start) {
          // Static shift by 3 as all data is 8-byte aligned
          val addrToRequest = io.baseAddress.bits + (reqsSent << 3)
          val tag = reqsSent
          // I am not a fan of the comparator here... But c'est la vie.
          val shouldSendRequest = io.start && !waitForResp(tag) && (reqsSent < io.amountData)

          if(p(VCodePrintfEnable)) {
            printf("DFetch\tstart: %d\tbaseAddress_valid: %d\n",
              io.start, io.baseAddress.valid)
            printf("DFetch\tShould submit new request for address 0x%x with tag 0x%x? %d\n",
              addrToRequest, tag, shouldSendRequest)
            printf("DFetch\tdprv: %d\tdv: %d\n", io.mstatus.dprv, io.mstatus.dv)
          }

          io.req.valid := shouldSendRequest
          io.req.bits.addr := addrToRequest
          io.req.bits.tag := tag
          io.req.bits.size := log2Ceil(8).U // Load 8 bytes
          io.req.bits.signed := false.B
          switch(io.opToPerform) {
            is(MemoryOperation.read) {
              io.req.bits.data := 0.U // Does not matter what we set data to.
              io.req.bits.cmd := M_XRD
            }
            is(MemoryOperation.write) {
              if(p(VCodePrintfEnable)) {
                printf("DFetch\tTag 0x%x will WRITE %b\n", tag, io.dataToWrite.bits(tag))
              }
              io.req.bits.data := io.dataToWrite.bits(tag)
              io.req.bits.cmd := M_XWR
            }
          }
          io.req.bits.phys := false.B
          io.req.bits.dprv := io.mstatus.dprv
          io.req.bits.dv := io.mstatus.dv
          when(io.req.fire) {
            // When our request is sent, we must increment number of requests made
            reqsSent := reqsSent + 1.U
            waitForResp(tag) := true.B
            if(p(VCodePrintfEnable)) {
              printf("DFetch\tMarked tag 0x%x (request tag 0x%x) as busy\n", tag, io.req.bits.tag)
            }
          }
        }
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
