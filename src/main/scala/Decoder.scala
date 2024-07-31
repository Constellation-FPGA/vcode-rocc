package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile.RoCCInstruction
import org.chipsalliance.cde.config.Parameters

class Decoder(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val roccInst = Input(new RoCCInstruction())
    val ctrlSigs = Output(new CtrlSigs())
  })

  /* Create the decode table at the top-level of the implementation
   * If additional instructions are added as separate classes in Instructions.scala
   * they can be added above BinOpDecode class. */
  val decodeTable = (new DecodeTable()).table

  /***************
   * DECODE
   **************/
  // Decode instruction, yielding control signals
  io.ctrlSigs := Wire(new CtrlSigs()).decode(io.roccInst.funct, decodeTable)
}
