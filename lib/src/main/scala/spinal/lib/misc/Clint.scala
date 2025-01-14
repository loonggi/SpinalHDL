package spinal.lib.misc

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3SlaveFactory}
import spinal.lib.bus.amba4.axilite.{AxiLite4, AxiLite4Config, AxiLite4SlaveFactory}
import spinal.lib.bus.bmb.{Bmb, BmbAccessCapabilities, BmbAccessParameter, BmbParameter, BmbSlaveFactory}
import spinal.lib.bus.misc.BusSlaveFactory
import spinal.lib.bus.wishbone.{Wishbone, WishboneConfig, WishboneSlaveFactory}

object Clint{
  def getWisboneConfig() = WishboneConfig(
    addressWidth = addressWidth-2,
    dataWidth = 32
  )

  def getBmbCapabilities(accessSource : BmbAccessCapabilities) = BmbSlaveFactory.getBmbCapabilities(
    accessSource,
    addressWidth = addressWidth,
    dataWidth = 32
  )
  def addressWidth = 16
}

case class Clint(hartCount : Int) extends Area{
  val time = Reg(UInt(64 bits)) init(0)
  time := time + 1

  val harts = for(hartId <- 0 until hartCount) yield new Area{
    val cmp = Reg(UInt(64 bits))
    val timerInterrupt = RegNext(time >= cmp)
    val softwareInterrupt = RegInit(False)
  }

  def driveFrom(bus : BusSlaveFactory, bufferTime : Boolean = false) = new Area{
    val IPI_ADDR = 0x0000
    val CMP_ADDR = 0x4000
    val TIME_ADDR = 0xBFF8

    bufferTime match {
      case false => bus.readMultiWord(time, TIME_ADDR)
      case true => new Composite(this){
        assert(bus.busDataWidth == 32)

        val timeMsb = RegNextWhen(time(63 downto 32), bus.isReading(TIME_ADDR))
        bus.read(time(31 downto 0), TIME_ADDR)
        bus.read(timeMsb, TIME_ADDR + 4)
      }
    }

    val hartsMapping = for(hartId <- 0 until hartCount) yield new Area{
      bus.writeMultiWord(harts(hartId).cmp, CMP_ADDR + 8*hartId)
      bus.readAndWrite(harts(hartId).softwareInterrupt, IPI_ADDR + 4*hartId, bitOffset = 0)
    }
  }
}


case class Apb3Clint(hartCount : Int) extends Component{
  val io = new Bundle {
    val bus = slave(Apb3(16, 32))
    val timerInterrupt = out Bits(hartCount bits)
    val softwareInterrupt = out Bits(hartCount bits)
    val time = out UInt(64 bits)
  }

  val factory = Apb3SlaveFactory(io.bus)
  val logic = Clint(hartCount)
  logic.driveFrom(factory)

  for(hartId <- 0 until hartCount){
    io.timerInterrupt(hartId) := logic.harts(hartId).timerInterrupt
    io.softwareInterrupt(hartId) := logic.harts(hartId).softwareInterrupt
  }

  io.time := logic.time
}


case class WishboneClint(hartCount : Int) extends Component{
  val io = new Bundle {
    val bus = slave(Wishbone(WishboneConfig(16-2, 32)))
    val timerInterrupt = out Bits(hartCount bits)
    val softwareInterrupt = out Bits(hartCount bits)
    val time = out UInt(64 bits)
  }

  val factory = WishboneSlaveFactory(io.bus)
  val logic = Clint(hartCount)
  logic.driveFrom(factory)

  for(hartId <- 0 until hartCount){
    io.timerInterrupt(hartId) := logic.harts(hartId).timerInterrupt
    io.softwareInterrupt(hartId) := logic.harts(hartId).softwareInterrupt
  }

  io.time := logic.time
}

case class AxiLite4Clint(hartCount : Int, bufferTime : Boolean = false) extends Component{
  val io = new Bundle {
    val bus = slave(AxiLite4(AxiLite4Config(16, 32)))
    val timerInterrupt = out Bits(hartCount bits)
    val softwareInterrupt = out Bits(hartCount bits)
    val time = out UInt(64 bits)
  }

  val factory = new AxiLite4SlaveFactory(io.bus)
  val logic = Clint(hartCount)
  logic.driveFrom(factory, bufferTime)

  for(hartId <- 0 until hartCount){
    io.timerInterrupt(hartId) := logic.harts(hartId).timerInterrupt
    io.softwareInterrupt(hartId) := logic.harts(hartId).softwareInterrupt
  }

  io.time := logic.time
}

case class BmbClint(bmbParameter : BmbParameter, hartCount : Int) extends Component{
  val io = new Bundle {
    val bus = slave(Bmb(bmbParameter))
    val timerInterrupt = out Bits(hartCount bits)
    val softwareInterrupt = out Bits(hartCount bits)
    val time = out UInt(64 bits)
  }

  val factory = BmbSlaveFactory(io.bus)
  val logic = Clint(hartCount)
  logic.driveFrom(factory)

  for(hartId <- 0 until hartCount){
    io.timerInterrupt(hartId) := logic.harts(hartId).timerInterrupt
    io.softwareInterrupt(hartId) := logic.harts(hartId).softwareInterrupt
  }

  io.time := logic.time
}