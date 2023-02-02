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

      dut.io.accel_ready.expect(true.B)
      dut.io.busy.expect(false.B)
      dut.io.ctrl_sigs.poke(ctrl_sigs)
      dut.io.cmd_valid.poke(true.B)
      dut.clock.step()

      // Should be in fetchingData state now
      dut.io.accel_ready.expect(false.B)
      dut.io.busy.expect(true.B)
      dut.io.should_fetch.expect(true.B)
      dut.io.num_to_fetch.expect(2.U)
      dut.io.fetching_completed.poke(true.B)
      dut.clock.step()

      // Should be in execute state now
      dut.clock.step()
      dut.io.accel_ready.expect(false.B)
      dut.io.busy.expect(true.B)
      dut.io.should_fetch.expect(false.B)
      dut.io.should_execute.expect(true.B)
      dut.io.execution_completed.poke(true.B)

      // Should be in write-back state now
      dut.clock.step()
      dut.io.accel_ready.expect(false.B)
      dut.io.busy.expect(true.B)
      dut.io.should_execute.expect(false.B)
      dut.io.response_ready.expect(true.B)
      dut.io.response_completed.poke(true.B)

      // Return to idle
      dut.clock.step()
      dut.io.accel_ready.expect(true.B)
      dut.io.busy.expect(false.B)
      dut.io.response_ready.expect(false.B)
    }
  }
}
