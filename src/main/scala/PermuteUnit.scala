package vcoderocc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.CoreModule
import vcoderocc.DataIO

object PermuteUnit {
    val SZ_PermuteUnit_FN = 7

    def FN_DEFAULT = BitPat.dontCare(SZ_PermuteUnit_FN)
    def FN_PERMUTE = BitPat(34.U(SZ_PermuteUnit_FN.W))
}

class PermuteUnit(val xLen: Int)(val batchSize: Int) extends Module {
    import PermuteUnit._
    val io = IO(new Bundle {
        val fn = Input(Bits(SZ_PermuteUnit_FN.W))
        val index = Input(Vec(batchSize, new DataIO(xLen)))
        val data = Input(Vec(batchSize, new DataIO(xLen)))
        val default = Input(new DataIO(xLen))
        val out = Output(Valid(Vec(batchSize, new DataIO(xLen))))
        val baseAddress = Input(UInt(xLen.W))
        val execute = Input(Bool())
        val accelIdle = Input(Bool())
    })

    val workingSpace = withReset(io.accelIdle) {
      RegInit((0.U).asTypeOf(Vec(batchSize, new DataIO(xLen))))
    }
    io.out.bits := workingSpace
    io.out.valid := false.B

    when(io.execute){
        switch(io.fn){
            /* Permutation works by moving the input data to a different
             * address in the output based on the provided index. */
            is(34.U){
                for (i <- 0 until batchSize) {
                    workingSpace(i).data := io.data(i).data
                    workingSpace(i).addr := io.baseAddress + (io.index(i).data * 8.U)
                }
                /* A permute is "essentially" an O(1) operation on this scale,
                 * since we know the index and we know the base address. A
                 * permute turns into just a left-shift and an addition. */
                io.out.valid := true.B
            }
        }
    }
}
