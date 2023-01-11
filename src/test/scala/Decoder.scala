package vcoderocc

import org.scalatest._
import chisel3._
import chisel3.util.BitPat
import chisel3.experimental.BundleLiterals._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.RoCCInstruction

class DecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = new vcoderocc.VCodeTestConfig

  val ROCC_CUSTOM_OPCODE_0: UInt = "b0001011".U
  val ROCC_CUSTOM_OPCODE_1: UInt = "b0101011".U
  val ROCC_CUSTOM_OPCODE_2: UInt = "b1011011".U
  val ROCC_CUSTOM_OPCODE_3: UInt = "b1111011".U

  behavior of "Decoder"
  it should "Decode PLUS_INT" in {
    test(new Decoder) { dut =>
      // val inst_to_test = PLUS_INT.toString().U
      val rocc_inst = (new RoCCInstruction()).Lit(
        _.funct -> 1.U(7.W),
        _.rs1 -> "b00000".U,
        _.rs2 -> "b00000".U,
        _.rd -> "b00000".U,
        _.xs1 -> true.B,
        _.xs2 -> true.B,
        _.xd -> true.B,
        _.opcode -> ROCC_CUSTOM_OPCODE_0
      )

      val expected_sigs = (new CtrlSigs()).Lit(
        _.legal -> true.B,
        _.alu_fn -> ALU.FN_ADD.value.U,
        _.is_mem_op -> true.B,
        _.num_mem_fetches -> NumOperatorOperands.MEM_OPS_TWO.value.U)

      dut.io.rocc_inst.poke(rocc_inst)
      dut.io.ctrl_sigs.expect(expected_sigs)
    }
  }
}
