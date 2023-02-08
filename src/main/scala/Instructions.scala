package vcoderocc

import chisel3.util._

object Instructions {
  def PLUS_INT = BitPat("b0000001")

  // Accelerator configuration instructions. These are usually nonblocking.
  /** Set number of elements to operate over. */
  def SET_NUM_OPERANDS = BitPat("b1000000")
}
