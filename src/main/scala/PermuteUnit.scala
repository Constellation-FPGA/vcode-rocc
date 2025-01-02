package vcoderocc

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.CoreModule

object PermuteUnit {
    val SZ_PermuteUnit_FN = 7

    def FN_DEFAULT = BitPat.dontCare(SZ_PermuteUnit_FN)
    def FN_PERMUTE = BitPat(34.U(SZ_PermuteUnit_FN.W))
}

class PermuteUnit(val xLen: Int)(val batchSize: Int) extends Module {
    import PermuteUnit._ // Import ALU object
    val totalElements = 64
    val io = IO(new Bundle {
        val fn = Input(Bits(SZ_PermuteUnit_FN.W))
        val index = Input(Vec(batchSize, UInt(xLen.W)))
        val data = Input(Vec(batchSize, UInt(xLen.W)))
        val default = Input(UInt(xLen.W))
        val out = Output(Vec(batchSize, UInt(xLen.W)))
        val numToFetch = Input(UInt(xLen.W))
        val execute = Input(Bool())
        val write = Input(Bool())
        val accelIdle = Input(Bool())
    })

    val workingSpace = withReset(io.accelIdle) {
        RegInit(VecInit.fill(batchSize)(0.U(xLen.W)))
    }
    io.out := workingSpace

    val result = withReset(io.accelIdle) {
        RegInit(VecInit(Seq.fill(totalElements)(0.U(xLen.W))))
    }
    
    when(io.execute){
        switch(io.fn){
            is(34.U){
                for (i <- 0 until batchSize) {
                    when(i.U < io.numToFetch){
                        result(io.index(i)) := io.data(i)
                    }
                }
            }
        }
    }

    when(io.write){
        workingSpace := result.slice(0, batchSize)
        // Drop the first batchSize elements from the result vector, shifting
        // everything down and "backfill" with zeros.
        result := result.drop(batchSize) ++ Seq.fill(batchSize)(0.U(xLen.W))
    }
}