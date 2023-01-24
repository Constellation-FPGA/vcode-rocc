package vcoderocc

import org.scalatest._
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.experimental.BundleLiterals._
import org.scalatest.matchers.should.Matchers

/** Example of tests that are "shared by multiple fixture objects"
  * More information on https://www.scalatest.org/user_guide/sharing_tests
  *
  * Taken from https://github.com/ucb-bar/chiseltest/blob/main/src/test/scala/chiseltest/tests/AluTest.scala
  */
trait ALUBehavior {
  this: AnyFlatSpec with ChiselScalatestTester =>

  // Int is 32-bit, breaking shifting by 64. Use BigInt instead.
  // NOTE: Scala << rotates! 16-bit number: 1 << 16 == 1!
  def mask(s: Int): BigInt = (BigInt(1) << s) - 1

  def testAddition(a: BigInt, b: BigInt, s: Int): Unit = {
    val result = (a + b) & mask(s)
    it should s"$a + $b == $result" in {
      test(new ALU(s)) { c =>
        c.io.fn.poke(ALU.FN_ADD.value)
        c.io.in1.poke(a.U(s.W))
        c.io.in2.poke(b.U(s.W))
        c.io.execute.poke(true.B)
        c.clock.step()

        c.io.out.bits.expect(result.U(s.W))
        c.io.out.valid.expect(true.B)
        c.io.cout.expect(0.U)
      }
    }
  }
}

class ALUTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with ALUBehavior {
  behavior of "ALU"
  val xLen = 64

  val testData: List[(BigInt, BigInt)] = List[(BigInt, BigInt)](
    (0, 0),
    (32, 14),
    ((BigInt(1)<<63), 1),
    /* We are limited by the JVM, because it does not support unsigned integral
     * values, so testing the maximum 64-bit number + 1 is not trivial. */
    (BigInt(0x7FFFFFFFFFFFFFFFL), 1),
  )

  testData.foreach { data =>
    it should behave like testAddition(data._1, data._2, xLen)
  }
}
