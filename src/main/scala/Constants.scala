package vcoderocc

import chisel3.util._

package object constants extends
    vcoderocc.OptionConstants with
    vcoderocc.MemorySizeConstants
{}

/** Mixin for constants representing options.
 */
trait OptionConstants {
  def X = BitPat.dontCare(1)
  def Y = BitPat.Y()
  def N = BitPat.N()
}

/** Memory transfer size constants for memory/cache subsystem.
  *
  * These match bits [14,12] (inclusive on both sides) in the RISC-V instruction
  * encoding for load byte, halfword, word, and double.
  */
trait MemorySizeConstants {
  /** Invalid/Unknown/Default Memory Transfer Size */
  def MTX  = BitPat("b???")
  /** Transfer 8 bits, 1 byte */
  def MT8  = BitPat("b000")
  /** Transfer 16 bits, 2 bytes, a half-word */
  def MT16 = BitPat("b001")
  /** Transfer 32 bits, 4 bytes, a word */
  def MT32 = BitPat("b010")
  /** Transfer 64 bits, 8 bytes, a double-word */
  def MT64 = BitPat("b011")
}
