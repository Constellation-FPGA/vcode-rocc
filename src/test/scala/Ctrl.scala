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
      val ctrlSigs = (new DecodeTable).findCtrlSigs(PLUS_INT)

      dut.io.accelReady.expect(true.B)
      dut.io.busy.expect(false.B)
      dut.io.ctrlSigs.poke(ctrlSigs)
      dut.io.cmdValid.poke(true.B)
      dut.clock.step()

      // Should be in fetchingData state now
      dut.io.accelReady.expect(false.B)
      dut.io.busy.expect(true.B)
      dut.io.shouldFetch.expect(true.B)
      dut.io.numToFetch.expect(2.U)
      dut.io.fetchingCompleted.poke(true.B)
      dut.clock.step()

      // Should be in execute state now
      dut.clock.step()
      dut.io.accelReady.expect(false.B)
      dut.io.busy.expect(true.B)
      dut.io.shouldFetch.expect(false.B)
      dut.io.shouldExecute.expect(true.B)
      dut.io.executionCompleted.poke(true.B)

      // Should be in write-back state now
      dut.clock.step()
      dut.io.accelReady.expect(false.B)
      dut.io.busy.expect(true.B)
      dut.io.shouldExecute.expect(false.B)
      dut.io.responseReady.expect(true.B)
      dut.io.responseCompleted.poke(true.B)

      // Return to idle
      dut.clock.step()
      dut.io.accelReady.expect(true.B)
      dut.io.busy.expect(false.B)
      dut.io.responseReady.expect(false.B)
    }
  }
}
