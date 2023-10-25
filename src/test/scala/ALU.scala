package vcoderocc

import org.scalatest._
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.VecLiterals._
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

  // def listBigIntToVecUInt(l: Seq[BigInt], xLen: Int): Vec[UInt] = {
  //   val num = l.length
  //   return Vec(num, UInt(xLen.W)).Lit(
  //     l.zipWithIndex.map {
  //       /* NOTE: The arrow operator is syntax sugar for a tuple (idx, v), i.e.
  //        * idx -> v is equivalent to (idx, v) */
  //       case (v, idx) => (idx, v.U(xLen.W))
  //   })
  // }

  def create() = Vec(1, UInt(64.W)).Lit(0 -> 2.U)

  def testAddition(aVals: Seq[BigInt], bVals: Seq[BigInt], s: Int, batchSize: Int): Unit = {
    assert(aVals.length == bVals.length, "Both input vectors MUST be the same length!")

    val results = aVals zip bVals map {case (a, b) => (a + b)}

    it should s"$aVals + $bVals == $results" in {
      test(new ALU(s)(batchSize)) { c =>
        c.io.fn.poke(ALU.FN_ADD.value)

        // c.io.in1.poke(listBigIntToVecUInt(List(0), s))
        c.io.in2.poke(create())
        c.io.in2.poke(Vec(1, UInt(s.W)).Lit(0 -> 2.U))
        c.io.execute.poke(true.B)
        c.clock.step()

        // c.io.out.expect(listBigIntToVecUInt(results, s))
        c.io.out.expect(Vec(1, UInt(s.W)).Lit(0 -> 2.U))
        c.io.cout.expect(0.U)
      }
    }
  }
}

class VectorAdd extends AnyFlatSpec with ChiselScalatestTester with Matchers with ALUBehavior {
  behavior of "ALU"
  val xLen = 64
  val batchSize = 1

  val testData: List[(List[BigInt], List[BigInt])] = List[(List[BigInt], List[BigInt])](
    (List(0), List(0)),
    (List(32, 21), List(14, 21)),
    (List((BigInt(1)<<63)), List(1)),
    /* We are limited by the JVM, because it does not support unsigned integral
     * values, so testing the maximum 64-bit number + 1 is not trivial. */
    (List(BigInt(0x7FFFFFFFFFFFFFFFL)), List(1)),
  )

  testData.foreach { data =>
    it should behave like testAddition(data._1, data._2, xLen, batchSize)
  }
}
