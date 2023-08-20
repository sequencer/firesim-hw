package chipyard.config

import freechips.rocketchip.subsystem.{InSubsystem, TilesLocated}
import org.chipsalliance.cde.config.Config
import testchipip.{TracePortKey, TracePortParams}

class WithTraceIO extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => up(TilesLocated(InSubsystem), site) map {
    case other => other
  }
  case TracePortKey => Some(TracePortParams())
})