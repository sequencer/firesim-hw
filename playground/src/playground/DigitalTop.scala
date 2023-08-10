package playground

import chisel3._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.system._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.devices.tilelink._

// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

// DOC include start: DigitalTop
class DigitalTop(implicit p: Parameters) extends ChipyardSystem
  with testchipip.CanHavePeripheryCustomBootPin // Enables optional custom boot pin
  with testchipip.CanHavePeripheryBootAddrReg // Use programmable boot address register
  with testchipip.CanHaveTraceIO // Enables optionally adding trace IO
  with testchipip.CanHaveBankedScratchpad // Enables optionally adding a banked scratchpad
  with testchipip.CanHavePeripheryBlockDevice // Enables optionally adding the block device
  with testchipip.CanHavePeripheryTLSerial // Enables optionally adding the backing memory and serial adapter
  with icenet.CanHavePeripheryIceNIC // Enables optionally adding the IceNIC for FireSim
  with HasChipyardPRCI // Use Chipyard reset/clock distribution
{
  override lazy val module = new DigitalTopModule(this)
}

class DigitalTopModule[+L <: DigitalTop](l: L) extends ChipyardSystemModule(l)
  with testchipip.CanHaveTraceIOModuleImp
  with freechips.rocketchip.util.DontTouch
// DOC include end: DigitalTop
