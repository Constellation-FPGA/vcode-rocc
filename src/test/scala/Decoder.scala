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
import freechips.rocketchip.tile.RoCCInstruction

class DecoderTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = new vcoderocc.VCodeTestConfig

  def testDecode(inst: BitPat, rocc_inst: RoCCInstruction): Unit = {
    it should s"Decode ${inst}" in {
      test(new Decoder) { dut =>
        val expected_sigs = (new DecodeTable).findCtrlSigs(inst)
        dut.io.rocc_inst.poke(rocc_inst)

        dut.io.ctrl_sigs.expect(expected_sigs)
      }
    }
  }

  behavior of "Decoder"
  val testData: List[(BitPat, RoCCInstruction)] = List[(BitPat, RoCCInstruction)](
    (PLUS_INT,
      vcoderocc.RoCCInstructionFactory.buildRoCCInstruction(PLUS_INT, 0, 0, 0,
        true, true, true, RoCCInstructionFactory.ROCC_CUSTOM_OPCODE_0)),
    (SET_NUM_OPERANDS, vcoderocc.RoCCInstructionFactory.buildRoCCInstruction(SET_NUM_OPERANDS, 0, 0, 0,
      true, false, false, RoCCInstructionFactory.ROCC_CUSTOM_OPCODE_0)),
    (SET_DEST_ADDR, vcoderocc.RoCCInstructionFactory.buildRoCCInstruction(SET_DEST_ADDR, 0, 0, 0,
        true, false, false, RoCCInstructionFactory.ROCC_CUSTOM_OPCODE_0)),
  )

  testData.foreach { datum =>
    it should behave like testDecode(datum._1, datum._2)
  }
}
