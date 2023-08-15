package playground.config


import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.devices.debug.{DebugModuleKey}

import sifive.blocks.devices.uart._

class WithUART(baudrate: BigInt = 115200) extends Config((site, here, up) => {
  case PeripheryUARTKey => Seq(
    UARTParams(address = 0x54000000L, nTxEntries = 256, nRxEntries = 256, initBaudRate = baudrate))
})


class WithNoDebug extends Config((site, here, up) => {
  case DebugModuleKey => None
})