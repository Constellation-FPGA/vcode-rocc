package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile.HasCoreParameters

/** Trait holding an abstract (non-instantiated) mapping between the instruction
  * bit pattern and its control signals.
  */
trait DecodeConstants extends HasCoreParameters { // TODO: Not sure if extends needed
  /** Array of pairs (table) mapping between instruction bit patterns and control
    * signals. */
  val decode_table: Array[(BitPat, List[BitPat])]
}

/** Control signals in the processor.
  * These are set during decoding.
  */
class CtrlSigs extends Bundle { // TODO: Rename to BinOpCtrlSigs?
  /* All control signals used in this coprocessor
   * See rocket-chip's rocket/IDecode.scala#IntCtrlSigs#default */
  // val legal = Bool() // Example control signal.
  /** List of default control signal values
    * @return List of default control signal values. */
  def default_decode_ctrl_sigs: List[BitPat] = List()
}

