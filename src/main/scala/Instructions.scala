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
  def LSHIFT_INT = BitPat("b0010000")
  def RSHIFT_INT = BitPat("b0010001")
  def NOT_INT = BitPat("b0010010")
  def AND_INT = BitPat("b0010011")
  def OR_INT = BitPat("b0010100")
  def XOR_INT = BitPat("b0010101")
  def SELECT_INT = BitPat("b0010110")
  def MUL_SCAN_INT = BitPat("b0010111")
  def MAX_SCAN_INT = BitPat("b0011000")
  def MIN_SCAN_INT = BitPat("b0011001")
  def AND_SCAN_INT = BitPat("b0011010")
  def OR_SCAN_INT = BitPat("b0011011")
  def XOR_SCAN_INT = BitPat("b0011100")
  def MUL_RED_INT = BitPat("b0011101")
  def MAX_RED_INT = BitPat("b0011110")
  def MIN_RED_INT = BitPat("b0011111")
  def AND_RED_INT = BitPat("b0100000")
  def OR_RED_INT = BitPat("b0100001")
  def XOR_RED_INT = BitPat("b0100010")
  def PERMUTE_INT = BitPat("b0100011")

  // Accelerator configuration instructions. These are usually nonblocking.
  /** Set number of elements to operate over. */
  def SET_NUM_OPERANDS = BitPat("b1000000")
  /** Set the destination vector base address for the next vector operation */
  def SET_DEST_ADDR = BitPat("b1000001")
  /* Set the third operand */
  def SET_THIRD_OPERAND = BitPat("b1000010")
}
