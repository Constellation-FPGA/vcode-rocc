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
    def FN_FPERMUTE = BitPat(35.U(SZ_PermuteUnit_FN.W))
}

class PermuteUnit(val xLen: Int)(val batchSize: Int) extends Module {
    import PermuteUnit._
    val io = IO(new Bundle {
        val fn = Input(Bits(SZ_PermuteUnit_FN.W))
        val index = Input(Vec(batchSize, new DataIO(xLen)))
        val data = Input(Vec(batchSize, new DataIO(xLen)))
        val flag = Input(new DataIO(xLen))
        val out = Output(Vec(batchSize, new DataIO(xLen)))
        val baseAddress = Input(UInt(xLen.W))
        val execute = Input(Bool())
        val accelIdle = Input(Bool())
    })

    val workingSpace = withReset(io.accelIdle) {
      RegInit((0.U).asTypeOf(Vec(batchSize, new DataIO(xLen))))
    }
    io.out := workingSpace

    when(io.execute){
        switch(io.fn){
            /* Permutation works by moving the input data to a different
             * address in the output based on the provided index. */
            is(34.U){
                for (i <- 0 until batchSize) {
                    workingSpace(i).data := io.data(i).data
                    workingSpace(i).addr := io.baseAddress + (io.index(i).data * 8.U)
                }
            }
            is(35.U){
                for (i <- 0 until batchSize) {
                    workingSpace(i).data := Mux(io.flag.data(i) === 1.U, io.data(i).data, workingSpace(i).data)
                    workingSpace(i).addr := Mux(io.flag.data(i) === 1.U, io.baseAddress + (io.index(i).data * 8.U), workingSpace(i).addr)
                }
            }
        }
    }
}
