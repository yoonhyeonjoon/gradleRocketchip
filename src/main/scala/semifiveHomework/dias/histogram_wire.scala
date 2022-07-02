package semifiveHomework.dias.histogram3

import chisel3._
import chisel3.util._
import chisel3.stage._

class histogram_wire(inwidth: Int, outwidth: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Irrevocable(Vec(inwidth, UInt(log2Ceil(outwidth).W))))
    val out = Irrevocable(Vec(outwidth, UInt(log2Ceil(outwidth).W)))
  })

  io.out.valid := io.in.valid
  io.in.ready := io.out.ready


  io.out.bits.zipWithIndex.foreach{case(out, idx) =>  val k = io.in.bits.map { in =>
      val temp = Wire(UInt())
      when(in === (idx+1).U) { temp := true.B }.otherwise { temp := false.B }
      temp
    }
    io.out.bits(idx) := k.reduce{(a,b) => a +& b}
    //io.out.bits(idx) := Cat(k)
  }



}
object test4 extends App{
  //println(getVerilogString(new Domainchanger()))
  (new ChiselStage).emitVerilog(new histogram_wire(1000,100))
}

