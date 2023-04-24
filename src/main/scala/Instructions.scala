package vcoderocc

import chisel3.util._

object Instructions {
  def PLUS_INT = BitPat("b0000001")
  def PLUS_RED = BitPat("b0000010")
  def OR_RED = BitPat("b0000011")
  def AND_RED = BitPat("b0000100")

  // Accelerator configuration instructions. These are usually nonblocking.
  /** Set number of elements to operate over. */
  def SET_NUM_OPERANDS = BitPat("b1000000")
  /** Set the destination vector base address for the next vector operation */
  def SET_DEST_ADDR = BitPat("b1000001")
}
