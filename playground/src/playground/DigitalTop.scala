package playground

import org.chipsalliance.cde.config.Parameters

// ------------------------------------
// BOOM and/or Rocket Top Level Systems
// ------------------------------------

// DOC include start: DigitalTop
class DigitalTop(implicit p: Parameters) extends ChipyardSystem
  with testchipip.CanHaveTraceIO // Enables optionally adding trace IO
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
