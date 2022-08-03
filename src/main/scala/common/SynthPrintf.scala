package vcoderocc.common

import scala.language.experimental.macros
// import scala.reflect.macros.Context
import scala.reflect.macros.blackbox.Context
import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import vcoderocc.VCodePrintfEnable

/* This needs to be a macro because Chisel's underlying printf takes implicit
 * arguments of source information & compile options. If the conditional printing
 * were abstracted to functions, this information would likely be wrong. So, we
 * use "text substitution" macros, like C would. */

private class SynthPrintfImpl(val c: Context) {
  def printf(c: Context)(p: Parameters)
    (fmt: c.Expr[String], args: c.Expr[Bits]*) : c.Expr[Unit] = {
    import c.universe._
    c.Expr[Unit](Literal(Constant()))
  }

  def printf(c: Context)(p: Parameters)
    (pable: c.Expr[Printable]) : c.Expr[Unit] = {
    import c.universe._
    c.Expr[Unit](Block(if(p(VCodePrintfEnable)) chisel3.printf(c.literal(pable)), Literal(Constant())))
  }
}

class SynthPrintf(implicit p: Parameters) {
  def printf(fmt: String, args: Bits*) = macro SynthPrintfImpl.printf(p)(fmt, args)
  def printf(pable: Printable) = macro SynthPrintfImpl.printf(p)(pable)
}
