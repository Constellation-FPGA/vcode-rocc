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
    def FN_DPERMUTE = BitPat(35.U(SZ_PermuteUnit_FN.W))
}

class PermuteUnit(val xLen: Int)(val batchSize: Int) extends Module {
    import PermuteUnit._ // Import ALU object
    val io = IO(new Bundle {
        val fn = Input(Bits(SZ_PermuteUnit_FN.W))
        val data = Input(Vec(batchSize, new DataIO(xLen)))
        val index = Input(Vec(batchSize, new DataIO(xLen)))
        val default = Input(UInt(xLen.W))
        val out = Output(Vec(batchSize, new DataIO(xLen)))
        val baseAddress = Input(UInt(xLen.W))
        val execute = Input(Bool())
        val accelIdle = Input(Bool())
    })

    /*val resetValuePERMUTE = VecInit(Seq.tabulate(batchSize) { i =>
        val entry = Wire(new DataIO(xLen))
        entry.data := 0.U
        entry.addr := 0.U
        entry
    })

    val resetValueDPERMUTE = VecInit(Seq.tabulate(batchSize) { i =>
        val entry = Wire(new DataIO(xLen))
        entry.data := io.default
        entry.addr := io.baseAddress + (i.U * 8.U)
        entry
    })

    val selectedResetValue = WireDefault(resetValuePERMUTE)
    when (io.fn === 35.U) {
        selectedResetValue := resetValueDPERMUTE
    }

    val workingSpace = RegInit(selectedResetValue)*/

    val workingSpace = withReset(io.accelIdle) {
      RegInit((0.U).asTypeOf(Vec(batchSize, new DataIO(xLen))))
    }
    val workingSpaceDPERMUTE = withReset(io.accelIdle) {
        RegInit(VecInit(Seq.tabulate(batchSize) { i =>
            val entry = Wire(new DataIO(xLen))
            entry.data := io.default
            entry.addr := io.baseAddress + (i.U * 8.U)
            entry
        }))
    }
    io.out := Mux(io.fn === 35.U, workingSpaceDPERMUTE, workingSpace)

    when(io.execute){
        switch(io.fn){
            is(34.U){
                for (i <- 0 until batchSize) {
                    workingSpace(i).data := io.data(i).data
                    workingSpace(i).addr := io.baseAddress + (io.index(i).data * 8.U)
                }
            }
            is(35.U){
                for (i <- 0 until batchSize) {
                    workingSpaceDPERMUTE(i).data := io.data(i).data
                    workingSpaceDPERMUTE(i).addr := io.baseAddress + (io.index(i).data * 8.U)
                }
            }
        }
    }
}
