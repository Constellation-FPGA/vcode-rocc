package vcoderocc

import chisel3._
import chisel3.util._

/** Externally-visible properties of the ALU.
  */
object ALU {
  /** The size of the ALU's internal functional unit's addresses */
  val SZ_ALU_FN = 4.W

  /** Unknown ALU function */
  def FN_X = BitPat("b????")
  def FN_ADD = BitPat("b0000") // FIXME: Try to express BitPat as just an integer?
}
