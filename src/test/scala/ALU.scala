package vcoderocc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.experimental.BundleLiterals._

/** Example of tests that are "shared by multiple fixture objects"
  * More information on https://www.scalatest.org/user_guide/sharing_tests
  *
  * Taken from https://github.com/ucb-bar/chiseltest/blob/main/src/test/scala/chiseltest/tests/AluTest.scala
  */
trait ALUBehavior {
  this: AnyFlatSpec with ChiselScalatestTester =>

  // Int is 32-bit, breaking shifting by 64. Use BigInt instead.
  def mask(s: Int): BigInt = (BigInt(1) << s) - 1

}

class ALUTest extends AnyFlatSpec with ChiselScalatestTester {
  "Test description" in {
    test(new ALU()) { dut =>
      dut.io.fn := FN_ADD
      dut.io.in1 := 32.U
      dut.io.in2 := 14.U
    }
  }
}
