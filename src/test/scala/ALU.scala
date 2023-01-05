package vcoderocc

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.experimental.BundleLiterals._

class ALUTest extends AnyFlatSpec with ChiselScalatestTester {
  "Test description" in {
    test(new ALU()) { dut =>
      dut.io.fn := FN_ADD
      dut.io.in1 := 32.U
      dut.io.in2 := 14.U
    }
  }
}
