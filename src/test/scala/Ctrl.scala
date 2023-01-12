package vcoderocc

import org.scalatest._
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.experimental.BundleLiterals._
import org.scalatest.matchers.should.Matchers

import vcoderocc.Instructions._

import freechips.rocketchip.config.Parameters

class ControlUnitTest extends AnyFlatSpec with ChiselScalatestTester {
  implicit val p: Parameters = new vcoderocc.VCodeTestConfig

  behavior of "Control Unit"
  it should s"Control signals for ${PLUS_INT}" in {
    test(new ControlUnit) { dut =>
      val ctrl_sigs = (new DecodeTable).findCtrlSigs(PLUS_INT)

      dut.io.busy.expect(false.B)
      dut.io.ctrl_sigs.poke(ctrl_sigs)
      dut.clock.step()
      dut.io.busy.expect(false.B)
      dut.clock.step()
      dut.io.busy.expect(true.B)
    }
  }
}
