package playground.config

import scala.util.matching.Regex
import chisel3._
import chisel3.util.{log2Up}

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.devices.tilelink.{BootROMLocated, PLICKey}
import freechips.rocketchip.devices.debug.{Debug, ExportDebug, DebugModuleKey, DMI}
import freechips.rocketchip.stage.phases.TargetDirKey
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.spi._

import testchipip._

class WithUART(baudrate: BigInt = 115200) extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x54000000L, nTxEntries = 256, nRxEntries = 256, initBaudRate = baudrate))
})


class WithNoDebug extends Config((site, here, up) => {
  case DebugModuleKey => None
})