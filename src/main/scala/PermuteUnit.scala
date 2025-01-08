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
    import PermuteUnit._ // Import ALU object
    val io = IO(new Bundle {
        val fn = Input(Bits(SZ_PermuteUnit_FN.W))
        val index = Input(Vec(batchSize, UInt(xLen.W)))
        val data = Input(Vec(batchSize, UInt(xLen.W)))
        val default = Input(UInt(xLen.W))
        val out = Output(Vec(batchSize, new DataIO(xLen)))
        val baseAddress = Input(UInt(xLen.W))
        val execute = Input(Bool())
        val write = Input(Bool())
        val accelIdle = Input(Bool())
    })

    val workingSpace = withReset(io.accelIdle) {
        RegInit(VecInit.fill(batchSize)(0.U.asTypeOf(new DataIO(xLen))))
    }
    io.out := workingSpace

    when(io.execute){
        switch(io.fn){
            is(34.U){
                for (i <- 0 until batchSize) {
                    workingSpace(i).data := io.data(i)
                    workingSpace(i).addr := io.baseAddress + io.index(i) * 8.U
                }
            }
        }
    }
}
