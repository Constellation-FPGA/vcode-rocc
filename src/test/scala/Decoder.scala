package vcoderocc

import org.scalatest._
import chisel3._
import chisel3.util.BitPat
import chisel3.experimental.BundleLiterals._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import vcoderocc.Instructions._

import freechips.rocketchip.config.Parameters

class DecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = new vcoderocc.VCodeTestConfig

  behavior of "Decoder"
  it should s"Decode ${PLUS_INT}" in {
    test(new Decoder) { dut =>
      // val inst_to_test = PLUS_INT.toString().U
      val rocc_inst = vcoderocc.RoCCInstructionFactory.buildRoCCInstruction(
        PLUS_INT, 0, 0, 0, true, true, true,
        RoCCInstructionFactory.ROCC_CUSTOM_OPCODE_0)

      val expected_sigs = (new DecodeTable).findCtrlSigs(PLUS_INT)

      dut.io.rocc_inst.poke(rocc_inst)
      dut.io.ctrl_sigs.expect(expected_sigs)
    }
  }
}
