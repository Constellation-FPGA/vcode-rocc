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
        val execute = Input(Bool())
        val write = Input(Bool())
        val accelIdle = Input(Bool())
    })

    val workingSpace = withReset(io.accelIdle) {
        RegInit(VecInit.fill(batchSize)(0.U(xLen.W)))
    }
    io.out := workingSpace

    val result = RegInit(VecInit(Seq.fill(totalElements)(0.U(xLen.W))))

    val currentBatch = RegInit(0.U(log2Ceil(totalElements/batchSize).W))
    
    when(io.execute){
        switch(io.fn){
            is(34.U){
                for(i <- 0 until batchSize){
                    val globalIndex = currentBatch * batchSize.U + io.index(i)
                    result(globalIndex) := io.data(i)
                    currentBatch := currentBatch + 1.U
                    /*if(currentBatch >= (totalElements/batchSize) - 1.U){
                        currentBatch := 0.U
                    } .otherwise{
                        currentBatch := currentBatch + 1.U   
                    }*/
                }
            }
        }
    }

    when(io.write){
        for(i <- 0 until totalElements/batchSize){
            workingSpace := result.slice(batchSize * i, batchSize * (i + 1))
        }
    }
}