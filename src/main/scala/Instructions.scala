package vcoderocc

import chisel3.util._

object Instructions {
  def PLUS_INT = BitPat("b0000001")
  def PLUS_RED_INT = BitPat("b0000010")
  def PLUS_SCAN_INT = BitPat("b0000011")
  def SUB_INT = BitPat("b0000100")
  def MUL_INT = BitPat("b0000101")
  def DIV_INT = BitPat("b0001000")
  def MOD_INT = BitPat("b0001001")
  def LESS_INT = BitPat("b0001010")
  def LESS_EQUAL_INT = BitPat("b0001011")
  def GREATER_INT = BitPat("b0001100")
  def GREATER_EQUAL_INT = BitPat("b0001101")
  def EQUAL_INT = BitPat("b0001110")
  def UNEQUAL_INT = BitPat("b0001111")

  // Accelerator configuration instructions. These are usually nonblocking.
  /** Set number of elements to operate over. */
  def SET_NUM_OPERANDS = BitPat("b1000000")
  /** Set the destination vector base address for the next vector operation */
  def SET_DEST_ADDR = BitPat("b1000001")
}
