package chiselExample.problems

import Chisel.log2Ceil
import chisel3._
import chisel3.internal.firrtl.Width
import chisel3.tester.{testableClock, testableData}
import chisel3.util.Counter
import chiselExample.problems.imageProcesser.ImageLoader
import runOption.ComplexRunner.generating

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import chiseltest.RawTester.test

import scala.util.Random

//=> (960) x (480 x 24) bits
case class IntellectualCCTVRealParams(
                                   videoWidth:Int  = 960,
                                   videoHeight:Int = 480,
                                   colorDomain:Int = 3,
                                   colorScale:Int  = 8/**256bit*/,
                                   hardfixFrameCoefficient:Int = 50,
                                   hardfixL1NormSensitivityThreshold:Int = 20,
                                   hardfixFrameSensitivityThreshold:Int = 15
                                 )
{
  def bitSize(numb : Int): Int = log2Ceil(numb + 1)
  val transSpeed:Int  = videoWidth / 4
  val transBits: Int = 1 /**Flag bit**/ + bitSize(transSpeed*colorDomain*colorScale)
  val inputBits: Int = colorDomain*colorScale
  val blockPixelUnit: Int = videoHeight * videoWidth / transSpeed
}

class IntellectualCCTVReal(intellectualCCTVParams:IntellectualCCTVRealParams) extends Module {

  def recusiver(n : Int, s : String): String = {
    if(n == 0) ""
    else s"$s${recusiver(n-1, s)}"
  }

  val videoLines:Int = intellectualCCTVParams.videoHeight
  val videoWidth:Int = intellectualCCTVParams.videoWidth
  val lineDataSize: Width = intellectualCCTVParams.transBits.W

  class VolumeIntegratorBundle extends Bundle {
    val videoInput: UInt = Input(UInt(lineDataSize))
    val getResult: Bool = Input(Bool())
//    val AlertOutput: Bool = Output(Bool())
//    val Output2: UInt = Output(UInt(lineDataSize))
//    val OutputGround: Vec[UInt] = Output(Vec(videoLines*videoWidth, UInt()))
  }

  val io: VolumeIntegratorBundle = IO(new VolumeIntegratorBundle())

  val pixelLocator: Counter = Counter(intellectualCCTVParams.blockPixelUnit)

  val startStateBits: String = "b1" + recusiver(intellectualCCTVParams.transBits - 1, "1")
  val endStateBits: String = "b1" + recusiver(intellectualCCTVParams.transBits - 1, "0")

  when(io.videoInput === startStateBits.U)
  {
    pixelLocator.reset()
  }.elsewhen(io.videoInput === endStateBits.U)
  {
    pixelLocator.reset()
  }
  .elsewhen(io.videoInput(intellectualCCTVParams.transBits - 1)) // Data가 들어와있는 상황
  {
    pixelLocator.inc()
  }

  case class Pixel(colors:Seq[UInt])
  val frameBuffer: Vec[UInt] = Reg(Vec(intellectualCCTVParams.hardfixFrameCoefficient * videoLines * videoWidth, UInt(24.W)))
  val bufferLocationCounter: (UInt, Bool) = Counter(true.B, intellectualCCTVParams.hardfixFrameCoefficient)

  //  frameBuffer(bufferLocationCounter._1)((pixelLocator.value * intellectualCCTVParams.transSpeed.U) + 0.U) := io.videoInput(24*(0+1), 24*0)
  //  frameBuffer(bufferLocationCounter._1)((pixelLocator.value * intellectualCCTVParams.transSpeed.U) + 1.U) := io.videoInput(24*(1+1), 24*1)
  //  frameBuffer(bufferLocationCounter._1)((pixelLocator.value * intellectualCCTVParams.transSpeed.U) + 2.U) := io.videoInput(24*(2+1), 24*2)
  for(i <- 0 until intellectualCCTVParams.transSpeed)
  {
    frameBuffer(bufferLocationCounter._1)((pixelLocator.value * intellectualCCTVParams.transSpeed.U) +& i.U) := io.videoInput(24*(i+1), 24*i)
  }


//  val fff  = ff(0) + ff2(0) + ff3(0) + ff4(0) + ff5(0) + ff6(0)
//  val fff2 = ff(1) + ff2(1) + ff3(1) + ff4(1) + ff5(1) + ff6(1)
////  val fff3 = sum of elements(i)

//  val ppp = frameBuffer.map{ aFrame =>
//    val aFrameGrouped = aFrame.zipWithIndex.groupBy{ aPixel =>
//      aPixel._2
//    }
//  }
//
//  val gg = frameBuffer.groupBy{ aFrame =>
//    val pp = aFrame.zipWithIndex
//    pp.sortBy(_._2)
//  }
//
//  val lll = 1

  //
//  val voxels: Seq[Seq[Vec[UInt]]] = //domain, widthxlines
//    Seq.fill(videoWidth * videoLines)(
//      Seq.fill(intellectualCCTVParams.colorDomain)(
//        RegInit(VecInit(
//          Seq.fill(intellectualCCTVParams.hardfixFrameCoefficient)(
//            RegInit(0.U((intellectualCCTVParams.hardfixFrameCoefficient*intellectualCCTVParams.colorScale).W)))
//      )))
//    )
//
//  val evalCounter: (UInt, Bool) = Counter(true.B, intellectualCCTVParams.hardfixFrameCoefficient)
//
//  for (yy <- 0 until videoLines) {
//    for (xx <- 0 until videoWidth) {
//      voxels(yy*videoWidth + xx).zipWithIndex.foreach{ aPixelSet =>
//        aPixelSet._1(evalCounter._1) := videoSeq(yy)(xx).colors(aPixelSet._2)
//      }
//    }
//  }
//
//  val gapShower: Seq[UInt] = Seq.fill(videoLines*videoWidth)(Wire(UInt()))
//
//  for (yy <- 0 until videoLines) {
//    for (xx <- 0 until videoWidth) {
//
//
//    }
//  }



}



object IntellectualCCTVRealUsage extends App {


//  generating(new IntellectualCCTVReal(IntellectualCCTVRealParams(hardfixFrameCoefficient = 5, hardfixL1NormSensitivityThreshold = 20, hardfixFrameSensitivityThreshold = 35)))

//  val getResult: Bool = Input(Bool())
//
  // 주의!! 해당 테스트 예제는 colorDomain = 3(R,G,B), scale 8bits에 hardfix 되어있습니다
  val colorDomain:Int = 3
  val colorScale:Int = 8

  /***Image Load***/
  var frames : Array[Seq[Seq[mutable.Buffer[Int]]]] = Array()
  var videoHeight:Int = 0
  var videoWidth:Int = 0

  val aFuturable = Future {
    new ImageLoader().loadImages(
      folderPath = "src\\main\\scala\\chiselExample\\problems\\imageProcesser",
      scaleFactor = 4,
      response = { (getInfo, response) =>
        frames = response.map(_._2)
        videoHeight = getInfo._1
        videoWidth = getInfo._2
      })
  }

  Await.result(aFuturable, Duration.Inf)
//  /***Image Load Complete***/

  def iterator(num : Int, tartStr : String) : String =
  {
    if(num == 0) ""
    else s"${tartStr}${iterator(num-1,tartStr)}"
  }

  def numIterator(num : Int, range : Int) : String =
  {
    if(num == 0) ""
    else s"${Random.nextInt(range).toString}${numIterator(num-1,range)}"
  }

    test(new IntellectualCCTVReal(IntellectualCCTVRealParams(
      videoHeight = videoHeight,
      videoWidth = videoWidth,
      hardfixFrameCoefficient = 5,
      hardfixL1NormSensitivityThreshold = 20,
      hardfixFrameSensitivityThreshold = 35)
    )) { c =>

      c.io.getResult.poke(false.B)

      //Image Waves..
      val setPacketBlockSize = 1 //size가 1이면 (1 + 24)bit, 2이면 (1 + 24*2)bit이 전송됨
      frames.foreach{ aFrame =>

        //simulated recess timing, noise
        for(p <- 0 until 5 + Random.nextInt(10)) { //5 ~ 15 random blank
          val noiseSignal = ("b" + numIterator(1+(setPacketBlockSize*24) ,2))
          c.io.videoInput.poke(noiseSignal.U)
          c.clock.step()
        }

        //Start Signal
        val startSignal = "b" + "1" + iterator(24*setPacketBlockSize,"1")
        c.io.videoInput.poke(startSignal.U)
        c.clock.step()

        var aCompress = "b1"
        var counter = 0
        for (aPacket <- 0 until videoHeight*videoWidth) {
          counter = counter + 1
          val xx = aPacket % videoWidth
          val yy = aPacket / videoWidth

          aCompress += "%08d".format(aFrame.head(yy)(xx).toBinaryString.toInt) /*+ getZero(num = 24*xx)   */  //R
          aCompress += "%08d".format(aFrame(1)(yy)(xx).toBinaryString.toInt)   /*+ getZero(num = 8+24*xx) */  //G
          aCompress += "%08d".format(aFrame(2)(yy)(xx).toBinaryString.toInt)   /*+ getZero(num = 16+24*xx)*/  //B

          if(counter % setPacketBlockSize == 0) {
            counter = 0 //side-effect init
            c.io.videoInput.poke(aCompress.U)
            c.clock.step()
            aCompress = "b1" //It should be ordered at last in this block(procedure style)
          }

        }

        //Start Signal
        val endSignal = "b" + "1" + iterator(24*setPacketBlockSize,"0")
        c.io.videoInput.poke(endSignal.U)
        c.clock.step()
      }

    }

}

