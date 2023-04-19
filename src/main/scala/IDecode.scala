package vcoderocc

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.HasCoreParameters
import Instructions._
import vcoderocc.constants._
import ALU._
import NumOperatorOperands._

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
class CtrlSigs extends Bundle {
  /* All control signals used in this coprocessor
   * See rocket-chip's rocket/IDecode.scala#IntCtrlSigs#default */
  val legal = Bool() // Example control signal.
  val alu_fn = Bits(SZ_ALU_FN.W)
  val is_mem_op = Bool()
  val num_mem_fetches = Bits(SZ_MEM_OPS)

  /** List of default control signal values
    * @return List of default control signal values. */
  def default_decode_ctrl_sigs: List[BitPat] =
    List(N, MEM_OPS_X, FN_X, N)

  /** Decodes an instruction to its control signals.
    * @param inst The instruction bit pattern to be decoded.
    * @param table Table of instruction bit patterns mapping to list of control
    * signal values.
    * @return Sequence of control signal values for the provided instruction.
    */
  def decode(inst: UInt, decode_table: Iterable[(BitPat, List[BitPat])]) = {
    val decoder = freechips.rocketchip.rocket.DecodeLogic(inst, default_decode_ctrl_sigs, decode_table)
    /* Make sequence ordered how signals are ordered.
     * See rocket-chip's rocket/IDecode.scala#IntCtrlSigs#decode#sigs */
    val ctrl_sigs = Seq(legal, num_mem_fetches, alu_fn, is_mem_op)
    /* Decoder is a minimized truth-table. We partially apply the map here,
     * which allows us to apply an instruction to get its control signals back.
     * We then zip that with the sequence of names for the control signals. */
    ctrl_sigs zip decoder map{case(s,d) => s := d}
    this
  }
}

object CtrlSigs {
  /** Convert a signal pattern (List/Seq/Array) of BitPat representing the output
    * from the decode table, and convert them to name-addressable control
    * signals.
    *
    * Do NOT use this in designs to elaborate and synthesize! This is intended
    * for only unit tests! */
  def convert(signalPattern: Iterable[BitPat]): CtrlSigs = {
    // This map destructures the signalPattern and assigns the elements to each
    // name in this sequence.
    val Seq(legal, num_mem_fetches, alu_fn, is_mem_op) = signalPattern.map{ case (x: BitPat) => x }

    (new CtrlSigs()).Lit(
      _.legal -> BitPat.bitPatToUInt(legal).asBool,
      _.num_mem_fetches -> BitPat.bitPatToUInt(num_mem_fetches),
      _.alu_fn -> alu_fn.value.U, // NOTE: BitPat of unknowns BitPat("b???") will be converted to 0s by this!
      _.is_mem_op -> BitPat.bitPatToUInt(is_mem_op).asBool
    )
  }
}


/** Class holding a table that implements the DecodeConstants table that mapping
  * a binary operation's instruction bit pattern to control signals.
  * @param p Implicit parameter of key-value pairs that can globally alter the
  * parameters of the design during elaboration.
  */
class BinOpDecode(implicit val p: Parameters) extends DecodeConstants {
  val decode_table: Array[(BitPat, List[BitPat])] = Array(
    PLUS_INT-> List(Y, MEM_OPS_TWO, FN_ADD, Y),
    PLUS_RED-> List(Y, MEM_OPS_N, FN_RED_ADD, Y))
}

/** Decode table for accelerator control instructions.
  * These tend to be non-blocking instructions that have no memory operands and
  * may or may not use the ALU.
  *
  * @param p Implicit parameter of key-value pairs that can globally alter the
  * parameters of the design during elaboration.
  */
class CtrlOpDecode(implicit val p: Parameters) extends DecodeConstants {
  val decode_table: Array[(BitPat, List[BitPat])] = Array(
    SET_NUM_OPERANDS -> List(Y, MEM_OPS_ZERO, FN_X, N),
    SET_DEST_ADDR -> List(Y, MEM_OPS_ZERO, FN_X, N))
}

/** A class holding a decode table for all possible RoCC instructions that are
  * supported by the accelerator, and a small helper to find the control signals
  * of a provided RoCC instruction's funct7 code. */
class DecodeTable(implicit val p: Parameters) {
  /** The decode table for all types of operators. */
  def table = {
    Seq(new BinOpDecode) ++
    Seq(new CtrlOpDecode)
  } flatMap(_.decode_table)

  /** Given an operation/funct7 code, find the control signals for that
    * particular RoCC instruction. */
  def findCtrlSigs(op: BitPat): CtrlSigs = {
    val ctrlSigs = new CtrlSigs
    var sigs: List[BitPat] = ctrlSigs.default_decode_ctrl_sigs
    // Try to find matching operation -> signal mapping in decode table
    for (opSigs <- table) {
      val operation = opSigs._1
      val signals = opSigs._2
      if(op.equals(operation)) {
        sigs = signals
      }
    }

    // Construct the CtrlSigs object
    return CtrlSigs.convert(sigs)
  }
}
