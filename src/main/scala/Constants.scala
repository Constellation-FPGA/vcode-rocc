package vcoderocc

import chisel3.util._

package object constants extends
    vcoderocc.OptionConstants
{}

/** Mixin for constants representing options.
 */
trait OptionConstants {
  def X = BitPat.dontCare(1)
  def Y = BitPat.Y()
  def N = BitPat.N()
}
