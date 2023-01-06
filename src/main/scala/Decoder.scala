package vcoderocc

import chisel3._
import chisel3.util._
import freechips.rocketchip.tile.RoCCInstruction
import freechips.rocketchip.config.Parameters

class Decoder(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val rocc_inst = Input(new RoCCInstruction())
    val ctrl_sigs = Output(new CtrlSigs())
  })

  /* Create the decode table at the top-level of the implementation
   * If additional instructions are added as separate classes in Instructions.scala
   * they can be added above BinOpDecode class. */
  val decode_table = {
    Seq(new BinOpDecode)
  } flatMap(_.decode_table)

  /***************
   * DECODE
   **************/
  // Decode instruction, yielding control signals
  io.ctrl_sigs := Wire(new CtrlSigs()).decode(io.rocc_inst.funct, decode_table)
}
