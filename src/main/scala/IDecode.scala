package vcoderocc

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.{HasCoreParameters, TileKey}
import Instructions._
import vcoderocc.constants._
import ALU._
import PermuteUnit._
import NumOperatorOperands._

/** Trait holding an abstract (non-instantiated) mapping between the instruction
  * bit pattern and its control signals.
  */
sealed trait DecodeConstants extends HasCoreParameters { // TODO: Not sure if extends needed
  /** Array of pairs (table) mapping between instruction bit patterns and control
    * signals. */
  val decodeTable: Array[(BitPat, List[BitPat])]

  def uIntMin(xLen: Int): UInt = 0.U(xLen.W)
  def uIntMax(xLen: Int): UInt = ~(0.U(xLen.W))
  def sIntMin(xLen: Int): SInt = (-(BigInt(1) << (xLen - 1))).S(xLen.W)
  def sIntMax(xLen: Int): SInt = ((BigInt(1) << (xLen - 1)) - 1).S(xLen.W)
}

/** Control signals in the processor.
  * These are set during decoding.
  */
class CtrlSigs(xLen: Int) extends Bundle {
  /* All control signals used in this coprocessor
   * See rocket-chip's rocket/IDecode.scala#IntCtrlSigs#default */
  val legal = Bool() // Example control signal.
  val numMemFetches = Bits(SZ_MEM_OPS)
  val aluFn = Bits(SZ_ALU_FN.W)
  val identityVal = UInt(xLen.W)
  val isMemOp = Bool()

  /** List of default control signal values
    * @return List of default control signal values. */
  def defaultDecodeCtrlSigs: List[BitPat] =
    List(N, MEM_OPS_X, FN_X, BitPat.dontCare(xLen), N)

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
    val ctrlSigs = Seq(legal, numMemFetches, aluFn, identityVal, isMemOp)
    /* Decoder is a minimized truth-table. We partially apply the map here,
     * which allows us to apply an instruction to get its control signals back.
     * We then zip that with the sequence of names for the control signals. */
    ctrlSigs zip decoder map{case(s,d) => s := d}
    this
  }
}

object CtrlSigs {
  /* FIXME: This xLen is hard-coded for this object. Exactly what its value is
   * does not matter all too much, since the CtrlSigs OBJECT is only use by
   * Scala-level unit tests. */
  val xLen: Int = 64

  /** Convert a signal pattern (List/Seq/Array) of BitPat representing the output
    * from the decode table, and convert them to name-addressable control
    * signals.
    *
    * Do NOT use this in designs to elaborate and synthesize! This is intended
    * for only unit tests! */
  def convert(signalPattern: Iterable[BitPat]): CtrlSigs = {
    // This map destructures the signalPattern and assigns the elements to each
    // name in this sequence.
    val Seq(legal, numMemFetches, aluFn, identityVal, isMemOp) = signalPattern.map{ case (x: BitPat) => x }

    (new CtrlSigs(xLen)).Lit(
      _.legal -> BitPat.bitPatToUInt(legal).asBool,
      _.numMemFetches -> BitPat.bitPatToUInt(numMemFetches),
      _.aluFn -> aluFn.value.U, // NOTE: BitPat of unknowns BitPat("b???") will be converted to 0s by this!
      _.identityVal -> BitPat.bitPatToUInt(identityVal),
      _.isMemOp -> BitPat.bitPatToUInt(isMemOp).asBool
    )
  }
}


/** Class holding a table that implements the DecodeConstants table that mapping
  * a binary operation's instruction bit pattern to control signals.
  * @param p Implicit parameter of key-value pairs that can globally alter the
  * parameters of the design during elaboration.
  */
final class BinOpDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    PLUS_INT-> List(Y, MEM_OPS_TWO, FN_ADD, BitPat(0.U), Y),
    SUB_INT -> List(Y, MEM_OPS_TWO, FN_SUB, BitPat(0.U), Y),
    MUL_INT -> List(Y, MEM_OPS_TWO, FN_MUL, BitPat(1.U), Y),
    DIV_INT -> List(Y, MEM_OPS_TWO, FN_DIV, BitPat(1.U), Y),
    MOD_INT -> List(Y, MEM_OPS_TWO, FN_MOD, BitPat(1.U), Y),
    LESS_INT -> List(Y, MEM_OPS_TWO, FN_LESS, BitPat(false.B), Y),
    LESS_EQUAL_INT -> List(Y, MEM_OPS_TWO, FN_LESS_EQUAL, BitPat(false.B), Y),
    GREATER_INT -> List(Y, MEM_OPS_TWO, FN_GREATER, BitPat(false.B), Y),
    GREATER_EQUAL_INT -> List(Y, MEM_OPS_TWO, FN_GREATER_EQUAL, BitPat(false.B), Y),
    EQUAL_INT -> List(Y, MEM_OPS_TWO, FN_EQUAL, BitPat(false.B), Y),
    UNEQUAL_INT -> List(Y, MEM_OPS_TWO, FN_UNEQUAL, BitPat(false.B), Y),
    LSHIFT_INT -> List(Y, MEM_OPS_TWO, FN_LSHIFT, BitPat(0.U), Y),
    RSHIFT_INT -> List(Y, MEM_OPS_TWO, FN_RSHIFT, BitPat(0.U), Y),
    NOT_INT -> List(Y, MEM_OPS_ONE, FN_NOT, BitPat(false.B), Y),
    AND_INT -> List(Y, MEM_OPS_TWO, FN_AND, BitPat(true.B), Y),
    OR_INT -> List(Y, MEM_OPS_TWO, FN_OR, BitPat(false.B), Y),
    XOR_INT -> List(Y, MEM_OPS_TWO, FN_XOR, BitPat(false.B), Y))
}

final class ReduceDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    PLUS_RED_INT -> List(Y, MEM_OPS_ONE, FN_RED_ADD, BitPat(0.U), Y),
    MUL_RED_INT -> List(Y, MEM_OPS_ONE, FN_RED_MUL, BitPat(1.U), Y),
    MAX_RED_INT -> List(Y, MEM_OPS_ONE, FN_RED_MAX,
      BitPat(sIntMin(xLen).asUInt), Y),
    MIN_RED_INT -> List(Y, MEM_OPS_ONE, FN_RED_MIN,
      BitPat(sIntMax(xLen).asUInt), Y),
    AND_RED_INT -> List(Y, MEM_OPS_ONE, FN_RED_AND, BitPat(true.B), Y),
    OR_RED_INT -> List(Y, MEM_OPS_ONE, FN_RED_OR, BitPat(false.B), Y),
    XOR_RED_INT -> List(Y, MEM_OPS_ONE, FN_RED_XOR, BitPat(false.B), Y)
    )
}

final class ScanDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    PLUS_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_ADD, BitPat(0.U), Y),
    MUL_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_MUL, BitPat(1.U), Y),
    MAX_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_MAX,
      BitPat(sIntMin(xLen).asUInt), Y),
    MIN_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_MIN,
      BitPat(sIntMax(xLen).asUInt), Y),
    AND_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_AND, BitPat(true.B), Y),
    OR_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_OR, BitPat(false.B), Y),
    XOR_SCAN_INT -> List(Y, MEM_OPS_ONE, FN_SCAN_XOR, BitPat(false.B), Y))
}

final class SelectDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    SELECT_INT -> List(Y, MEM_OPS_THREE, FN_SELECT, BitPat.dontCare(xLen), Y))
}

final class PermuteDecode (implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    PERMUTE_INT -> List(Y, MEM_OPS_TWO, FN_PERMUTE, BitPat.dontCare(xLen), Y))
}

/** Decode table for accelerator control instructions.
  * These tend to be non-blocking instructions that have no memory operands and
  * may or may not use the ALU.
  *
  * @param p Implicit parameter of key-value pairs that can globally alter the
  * parameters of the design during elaboration.
  */
final class CtrlOpDecode(implicit val p: Parameters) extends DecodeConstants {
  val decodeTable: Array[(BitPat, List[BitPat])] = Array(
    SET_NUM_OPERANDS -> List(Y, MEM_OPS_ZERO, FN_X, BitPat.dontCare(xLen), N),
    SET_DEST_ADDR -> List(Y, MEM_OPS_ZERO, FN_X, BitPat.dontCare(xLen), N),
    SET_THIRD_OPERAND -> List(Y, MEM_OPS_ZERO, FN_X, BitPat.dontCare(xLen), N))
}

/** A class holding a decode table for all possible RoCC instructions that are
  * supported by the accelerator, and a small helper to find the control signals
  * of a provided RoCC instruction's funct7 code. */
class DecodeTable(implicit val p: Parameters) {
  val xLen = p(TileKey).core.xLen

  /** The decode table for all types of operators. */
  def table = {
    Seq(new BinOpDecode) ++
    Seq(new ReduceDecode) ++
    Seq(new ScanDecode) ++
    Seq(new SelectDecode) ++
    Seq(new PermuteDecode) ++
    Seq(new CtrlOpDecode)
  } flatMap(_.decodeTable)

  /** Given an operation/funct7 code, find the control signals for that
    * particular RoCC instruction. */
  def findCtrlSigs(op: BitPat): CtrlSigs = {
    val ctrlSigs = new CtrlSigs(xLen)
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
