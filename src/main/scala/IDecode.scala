package vcoderocc

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import org.chipsalliance.cde.config.Parameters
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
  val decodeTable: Array[(BitPat, List[BitPat])]
}

/** Control signals in the processor.
  * These are set during decoding.
  */
class CtrlSigs extends Bundle {
  /* All control signals used in this coprocessor
   * See rocket-chip's rocket/IDecode.scala#IntCtrlSigs#default */
  val legal = Bool() // Example control signal.
  val aluFn = Bits(SZ_ALU_FN.W)
  val isMemOp = Bool()
  val numMemFetches = Bits(SZ_MEM_OPS)

  /** List of default control signal values
    * @return List of default control signal values. */
  def defaultDecodeCtrlSigs: List[BitPat] =
    List(N, MEM_OPS_X, FN_X, N)

  /** Decodes an instruction to its control signals.
    * @param inst The instruction bit pattern to be decoded.
    * @param table Table of instruction bit patterns mapping to list of control
    * signal values.
    * @return Sequence of control signal values for the provided instruction.
    */
  def decode(inst: UInt, decodeTable: Iterable[(BitPat, List[BitPat])]) = {
    val decoder = freechips.rocketchip.rocket.DecodeLogic(inst, defaultDecodeCtrlSigs, decodeTable)
    /* Make sequence ordered how signals are ordered.
     * See rocket-chip's rocket/IDecode.scala#IntCtrlSigs#decode#sigs */
    val ctrlSigs = Seq(legal, numMemFetches, aluFn, isMemOp)
    /* Decoder is a minimized truth-table. We partially apply the map here,
     * which allows us to apply an instruction to get its control signals back.
     * We then zip that with the sequence of names for the control signals. */
    ctrlSigs zip decoder map{case(s,d) => s := d}
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
    val Seq(legal, numMemFetches, aluFn, isMemOp) = signalPattern.map{ case (x: BitPat) => x }

    (new CtrlSigs()).Lit(
      _.legal -> BitPat.bitPatToUInt(legal).asBool,
      _.numMemFetches -> BitPat.bitPatToUInt(numMemFetches),
      _.aluFn -> aluFn.value.U, // NOTE: BitPat of unknowns BitPat("b???") will be converted to 0s by this!
      _.isMemOp -> BitPat.bitPatToUInt(isMemOp).asBool
    )
  }
}


/** Class holding a table that implements the DecodeConstants table that mapping
  * a binary operation's instruction bit pattern to control signals.
  * @param p Implicit parameter of key-value pairs that can globally alter the
  * parameters of the design during elaboration.
  */
class BinOpDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    PLUS_INT-> List(Y, MEM_OPS_TWO, FN_ADD, Y),
    SUB_INT -> List(Y, MEM_OPS_TWO, FN_SUB, Y),
    MUL_INT -> List(Y, MEM_OPS_TWO, FN_MUL, Y),
    DIV_INT -> List(Y, MEM_OPS_TWO, FN_DIV, Y),
    MOD_INT -> List(Y, MEM_OPS_TWO, FN_MOD, Y),
    LESS_INT -> List(Y, MEM_OPS_TWO, FN_LESS, Y),
    LESS_EQUAL_INT -> List(Y, MEM_OPS_TWO, FN_LESS_EQUAL, Y),
    GREATER_INT -> List(Y, MEM_OPS_TWO, FN_GREATER, Y),
    GREATER_EQUAL_INT -> List(Y, MEM_OPS_TWO, FN_GREATER_EQUAL, Y),
    EQUAL_INT -> List(Y, MEM_OPS_TWO, FN_EQUAL, Y),
    UNEQUAL_INT -> List(Y, MEM_OPS_TWO, FN_UNEQUAL, Y),
    LSHIFT_INT -> List(Y, MEM_OPS_TWO, FN_LSHIFT, Y),
    RSHIFT_INT -> List(Y, MEM_OPS_TWO, FN_RSHIFT, Y),
    NOT_INT -> List(Y, MEM_OPS_ONE, FN_NOT, Y),
    AND_INT -> List(Y, MEM_OPS_TWO, FN_AND, Y),
    OR_INT -> List(Y, MEM_OPS_TWO, FN_OR, Y),
    XOR_INT -> List(Y, MEM_OPS_TWO, FN_XOR, Y))
}

class ReduceDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    PLUS_RED_INT -> List(Y, MEM_OPS_ONE, FN_RED_ADD, Y))
}

class ScanDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    PLUS_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_ADD, Y),
    MUL_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_MUL, Y),
    MAX_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_MAX, Y),
    MIN_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_MIN, Y))
}

class SelectDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    SELECT_INT -> List(Y, MEM_OPS_THREE, FN_SELECT, Y))
}

/** Decode table for accelerator control instructions.
  * These tend to be non-blocking instructions that have no memory operands and
  * may or may not use the ALU.
  *
  * @param p Implicit parameter of key-value pairs that can globally alter the
  * parameters of the design during elaboration.
  */
class CtrlOpDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    SET_NUM_OPERANDS -> List(Y, MEM_OPS_ZERO, FN_X, N),
    SET_DEST_ADDR -> List(Y, MEM_OPS_ZERO, FN_X, N),
    SET_THIRD_OPERAND -> List(Y, MEM_OPS_ZERO, FN_X, N))
}

/** A class holding a decode table for all possible RoCC instructions that are
  * supported by the accelerator, and a small helper to find the control signals
  * of a provided RoCC instruction's funct7 code. */
class DecodeTable(implicit val p: Parameters) {
  /** The decode table for all types of operators. */
  def table = {
    Seq(new BinOpDecode) ++
    Seq(new ReduceDecode) ++
    Seq(new ScanDecode) ++
    Seq(new SelectDecode) ++
    Seq(new CtrlOpDecode)
  } flatMap(_.decodeTable)

  /** Given an operation/funct7 code, find the control signals for that
    * particular RoCC instruction. */
  def findCtrlSigs(op: BitPat): CtrlSigs = {
    val ctrlSigs = new CtrlSigs
    var sigs: List[BitPat] = ctrlSigs.defaultDecodeCtrlSigs
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
