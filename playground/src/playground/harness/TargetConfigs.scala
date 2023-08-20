package playground.harness

import firesim.configs.{WithDefaultMemModel}
import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.devices.tilelink.BootROMLocated
import freechips.rocketchip.subsystem.{WithMemoryBusFrequency, WithPeripheryBusFrequency, WithSystemBusFrequency, WithoutTLMonitors}
import org.chipsalliance.cde.config.Config
import playground.ChipyardPRCIControlKey
import playground.clocking.{WithClockGroupsCombinedByName, WithPassthroughClockGenerator}
import playground.config.{WithInheritBusFrequencyAssignments, WithNoDebug, WithNoSubsystemDrivenClocks, WithTraceIO, WithUART}
import playground.iobinders._

import java.io.File

class WithBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => {
    val bootROMPath = new File(s"./dependencies/testchipip/bootrom/bootrom.rv64.img").getAbsolutePath

    up(BootROMLocated(x), site).map(_.copy(contentFileName = bootROMPath))
  }
})

// Disables clock-gating; doesn't play nice with our FAME-1 pass
class WithoutClockGating extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey, site).map(_.copy(clockGate = false))
})

// Use the firesim clock bridge instantiator. this is required
class WithFireSimHarnessClockBridgeInstantiator extends Config((site, here, up) => {
  case HarnessClockInstantiatorKey => () => new FireSimClockBridgeInstantiator
})

class WithNIC extends icenet.WithIceNIC(inBufFlits = 8192, ctrlQueueDepth = 64)

// Minimal set of FireSim-related design tweaks - notably discludes FASED, TraceIO, and the BlockDevice
class WithMinimalFireSimDesignTweaks extends Config(
  // Required*: Punch all clocks to FireSim's harness clock instantiator
  new WithFireSimHarnessClockBridgeInstantiator ++
  new WithHarnessBinderClockFreqMHz(1000.0) ++
  new WithClockAndResetFromHarness ++
  new WithPassthroughClockGenerator ++
  // Required*: When using FireSim-as-top to provide a correct path to the target bootrom source
  new WithBootROM ++
  // Required: Existing FAME-1 transform cannot handle black-box clock gates
  new WithoutClockGating ++
  // Required*: Removes thousands of assertions that would be synthesized (* pending PriorityMux bugfix)
  new WithoutTLMonitors ++
  // Required: Do not support debug module w. JTAG until FIRRTL stops emitting @(posedge ~clock)
  new WithNoDebug
)

// Non-frequency tweaks that are generally applied to all firesim configs
class WithFireSimDesignTweaks extends Config(
  new WithMinimalFireSimDesignTweaks ++
  // Required: Bake in the default FASED memory model
  new WithDefaultMemModel ++
  // Optional: reduce the width of the Serial TL interface
  new testchipip.WithSerialTLWidth(4) ++
  // Required*: Scale default baud rate with periphery bus frequency
  new WithUART(BigInt(3686400L)) ++
  // Optional: Adds IO to attach tracerV bridges
  new WithTraceIO ++
  // Optional: Request 16 GiB of target-DRAM by default (can safely request up to 32 GiB on F1)
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 16L) ++
  // Optional: Removing this will require using an initramfs under linux
  new testchipip.WithBlockDevice
)

// Tweaks that are generally applied to all firesim configs setting a single clock domain at 1000 MHz
class WithFireSimConfigTweaks extends Config(
  // 1 GHz matches the FASED default (DRAM modeli realistically configured for that frequency)
  // Using some other frequency will require runnings the FASED runtime configuration generator
  // to generate faithful DDR3 timing values.
  new WithSystemBusFrequency(1000.0) ++
  new WithPeripheryBusFrequency(1000.0) ++
  new WithMemoryBusFrequency(1000.0) ++
  new WithFireSimDesignTweaks
)

/*******************************************************************************
 * Full TARGET_CONFIG configurations. These set parameters of the target being
 * simulated.
 *
 * In general, if you're adding or removing features from any of these, you
 * should CREATE A NEW ONE, WITH A NEW NAME. This is because the manager
 * will store this name as part of the tags for the AGFI, so that later you can
 * reconstruct what is in a particular AGFI. These tags are also used to
 * determine which driver to build.
 *******************************************************************************/

//*****************************************************************
// Rocket configs, base off chipyard's RocketConfig
//*****************************************************************
// DOC include start: firesimconfig

class AbstractConfig extends Config(
  // The HarnessBinders control generation of hardware in the TestHarness
  new WithUARTAdapter ++                          // add UART adapter to display UART on stdout, if uart is present
  new WithSimTSIOverSerialTL ++                   // add external serial-adapter and RAM
  new WithSimAXIMMIO ++                           // add SimAXIMem for axi4 mmio port, if enabled
  new WithTieOffInterrupts ++                     // tie-off interrupt ports, if present
  new WithTieOffL2FBusAXI ++                      // tie-off external AXI4 master, if present
  new WithCustomBootPinPlusArg ++                 // drive custom-boot pin with a plusarg, if custom-boot-pin is present
  new WithClockAndResetFromHarness ++             // all Clock/Reset I/O in ChipTop should be driven by harnessClockInstantiator
  new WithAbsoluteFreqHarnessClockInstantiator ++ // generate clocks in harness with unsynthesizable ClockSourceAtFreqMHz

  // The IOBinders instantiate ChipTop IOs to match desired digital IOs
  // IOCells are generated for "Chip-like" IOs, while simulation-only IOs are directly punched through
  new WithAXI4MemPunchthrough ++
  new WithAXI4MMIOPunchthrough ++
  new WithL2FBusAXI4Punchthrough ++
  new WithBlockDeviceIOPunchthrough ++
  new WithNICIOPunchthrough ++
  new WithSerialTLIOCells ++
  new WithUARTIOCells ++
  new WithTraceIOPunchthrough ++
  new WithExtInterruptIOCells ++
  new WithCustomBootPin ++

  // By default, punch out IOs to the Harness
  new WithPassthroughClockGenerator ++
  new WithClockGroupsCombinedByName(("uncore", Seq("sbus", "mbus", "pbus", "fbus", "cbus", "implicit"), Seq("tile"))) ++
  new WithPeripheryBusFrequency(500.0) ++           // Default 500 MHz pbus
  new WithMemoryBusFrequency(500.0) ++              // Default 500 MHz mbus

  new testchipip.WithCustomBootPin ++                               // add a custom-boot-pin to support pin-driven boot address
  new testchipip.WithBootAddrReg ++                                 // add a boot-addr-reg for configurable boot address
  new testchipip.WithSerialTLClientIdBits(4) ++                     // support up to 1 << 4 simultaneous requests from serialTL port
  new testchipip.WithSerialTLWidth(32) ++                           // fatten the serialTL interface to improve testing performance
  new testchipip.WithDefaultSerialTL ++                             // use serialized tilelink port to external serialadapter/harnessRAM
  new WithUART ++                                   // add a UART
  new WithNoSubsystemDrivenClocks ++                // drive the subsystem diplomatic clocks from ChipTop instead of using implicit clocks
  new WithInheritBusFrequencyAssignments ++         // Unspecified clocks within a bus will receive the bus frequency if set
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++      // Default 1 memory channels
  new freechips.rocketchip.subsystem.WithClockGateModel ++          // add default EICG_wrapper clock gate model
  new freechips.rocketchip.subsystem.WithJtagDTM ++                 // set the debug module to expose a JTAG port
  new freechips.rocketchip.subsystem.WithNoMMIOPort ++              // no top-level MMIO master port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithNoSlavePort ++             // no top-level MMIO slave port (overrides default set in rocketchip)
  new freechips.rocketchip.subsystem.WithInclusiveCache ++          // use Sifive L2 cache
  new freechips.rocketchip.subsystem.WithNExtTopInterrupts(0) ++    // no external interrupts
  new freechips.rocketchip.subsystem.WithDontDriveBusClocksFromSBus ++ // leave the bus clocks undriven by sbus
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++     // hierarchical buses including sbus/mbus/pbus/fbus/cbus/l2
  new freechips.rocketchip.subsystem.WithDTS("ucb-bar,chipyard", Nil) ++ // custom device name for DTS
  new freechips.rocketchip.system.BaseConfig)                       // "base" rocketchip system

class RocketConfig extends Config(
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++         // single rocket-core
  new AbstractConfig)

class FireSimRocketConfig extends Config(
  new WithDefaultFireSimBridges ++
  new WithDefaultMemModel ++
  new WithFireSimConfigTweaks ++
  new RocketConfig)
// DOC include end: firesimconfig

class FireSimRocket4GiBDRAMConfig extends Config(
  new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++
    new FireSimRocketConfig)
