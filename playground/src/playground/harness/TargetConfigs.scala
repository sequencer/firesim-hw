package playground.harness



import org.chipsalliance.cde.config.Config

class WithFireSimHarnessClockBridgeInstantiator extends Config((site, here, up) => {
  case HarnessClockInstantiatorKey => () => new FireSimClockBridgeInstantiator
})