package playground.config
import freechips.rocketchip.subsystem.{ControlBusKey, FrontBusKey, MemoryBusKey, PeripheryBusKey, SubsystemDriveAsyncClockGroupsKey, SystemBusKey}
import freechips.rocketchip.tilelink.HasTLBusParams
import org.chipsalliance.cde.config.{Config, Field}
import playground.clocking.ClockFrequencyAssignersKey

import scala.util.matching.Regex

class WithNoSubsystemDrivenClocks extends Config((site, here, up) => {
  case SubsystemDriveAsyncClockGroupsKey => None
})

class BusFrequencyAssignment[T <: HasTLBusParams](re: Regex, key: Field[T]) extends Config((site, here, up) => {
  case ClockFrequencyAssignersKey => up(ClockFrequencyAssignersKey, site) ++
    Seq((cName: String) => site(key).dtsFrequency.flatMap { f =>
      re.findFirstIn(cName).map {_ => (f.toDouble / (1000 * 1000)) }
    })
})
class WithInheritBusFrequencyAssignments extends Config(
  new BusFrequencyAssignment("subsystem_sbus_\\d+".r, SystemBusKey) ++
    new BusFrequencyAssignment("subsystem_pbus_\\d+".r, PeripheryBusKey) ++
    new BusFrequencyAssignment("subsystem_cbus_\\d+".r, ControlBusKey) ++
    new BusFrequencyAssignment("subsystem_fbus_\\d+".r, FrontBusKey) ++
    new BusFrequencyAssignment("subsystem_mbus_\\d+".r, MemoryBusKey)
)