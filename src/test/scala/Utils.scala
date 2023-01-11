package vcoderocc

import chisel3._
import chisel3.util.BitPat
import chisel3.experimental.BundleLiterals._

import freechips.rocketchip.tile.RoCCInstruction

object RoCCInstructionFactory {
  def ROCC_CUSTOM_OPCODE_0: UInt = "b0001011".U
  def ROCC_CUSTOM_OPCODE_1: UInt = "b0101011".U
  def ROCC_CUSTOM_OPCODE_2: UInt = "b1011011".U
  def ROCC_CUSTOM_OPCODE_3: UInt = "b1111011".U

  def buildRoCCInstruction(funct: BitPat, rs1: Int, rs2: Int, rd: Int,
    xs1: Boolean, xs2: Boolean, xd: Boolean,
    roccOpcode: Data): RoCCInstruction = {

    val rocc_inst = (new RoCCInstruction()).Lit(
      _.funct -> funct.value.U,
      _.rs1 -> rs1.U,
      _.rs2 -> rs2.U,
      _.rd -> rd.U,
      _.xs1 -> xs1.B,
      _.xs2 -> xs2.B,
      _.xd -> xs2.B,
      _.opcode -> ROCC_CUSTOM_OPCODE_0)
    rocc_inst
  }
}
