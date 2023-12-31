package chipyard.harness

import chipyard.{GetSystemParameters, HasChipyardPRCI, OverrideIOBinder}
import chipyard.clocking.ClockWithFreq
import chisel3._
import chisel3.experimental.BaseModule
import chisel3.reflect.DataMirror
import org.chipsalliance.cde.config.{Config, Field, Parameters}
import freechips.rocketchip.diplomacy.LazyModule
import freechips.rocketchip.amba.axi4.AXI4Bundle
import freechips.rocketchip.system.SimAXIMem
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.PlusArg
import sifive.blocks.devices.uart._
import icenet.{CanHavePeripheryIceNIC, NICIOvonly, NICKey, NicLoopback, SimNetwork}
import testchipip.{BlockDeviceIO, BlockDeviceModel, CanHavePeripheryBlockDevice, CanHavePeripheryCustomBootPin, CanHavePeripheryTLSerial, CanHaveTraceIOModuleImp, ClockedAndResetIO, ClockedIO, SerialIO, SerialTLKey, SerialWidthAdapter, SimBlockDevice, SimDromajoBridge, SimSPIFlashModel, SimTSI, TSI, TSIHarness, TraceOutputTop, UARTAdapter, UARTToSerial}

import scala.reflect.ClassTag

case object HarnessBinders extends Field[Map[String, (Any, HasHarnessInstantiators, Seq[Data]) => Unit]](
  Map[String, (Any, HasHarnessInstantiators, Seq[Data]) => Unit]().withDefaultValue((t: Any, th: HasHarnessInstantiators, d: Seq[Data]) => ())
)

object ApplyHarnessBinders {
  def apply(th: HasHarnessInstantiators, sys: LazyModule, portMap: Map[String, Seq[Data]])(implicit p: Parameters): Unit = {
    val pm = portMap.withDefaultValue(Nil)
    p(HarnessBinders).foreach { case (s, f) =>
      f(sys, th, pm(s))
      f(sys.module, th, pm(s))
    }
  }
}

// The ClassTags here are necessary to overcome issues arising from type erasure
class HarnessBinder[T, S <: HasHarnessInstantiators, U <: Data](composer: ((T, S, Seq[U]) => Unit) => (T, S, Seq[U]) => Unit)(implicit systemTag: ClassTag[T], harnessTag: ClassTag[S], portTag: ClassTag[U]) extends Config((site, here, up) => {
  case HarnessBinders => up(HarnessBinders, site) + (systemTag.runtimeClass.toString ->
    ((t: Any, th: HasHarnessInstantiators, ports: Seq[Data]) => {
      val pts = ports.collect({case p: U => p})
      require (pts.length == ports.length, s"Port type mismatch between IOBinder and HarnessBinder: ${portTag}")
      val upfn = up(HarnessBinders, site)(systemTag.runtimeClass.toString)
      th match {
        case th: S =>
          t match {
            case system: T => composer(upfn)(system, th, pts)
            case _ =>
          }
        case _ =>
      }
    })
    )
})

class OverrideHarnessBinder[T, S <: HasHarnessInstantiators, U <: Data](fn: => (T, S, Seq[U]) => Unit)
                                                                       (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
  extends HarnessBinder[T, S, U]((upfn: (T, S, Seq[U]) => Unit) => fn)

class ComposeHarnessBinder[T, S <: HasHarnessInstantiators, U <: Data](fn: => (T, S, Seq[U]) => Unit)
                                                                      (implicit tag: ClassTag[T], thtag: ClassTag[S], ptag: ClassTag[U])
  extends HarnessBinder[T, S, U]((upfn: (T, S, Seq[U]) => Unit) => (t, th, p) => {
    upfn(t, th, p)
    fn(t, th, p)
  })

// DOC include start: WithUARTAdapter
class WithUARTAdapter extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: HasHarnessInstantiators, ports: Seq[UARTPortIO]) => {
    UARTAdapter.connect(ports)(system.p)
  }
})
// DOC include end: WithUARTAdapter

class WithSimAXIMMIO extends OverrideHarnessBinder({
  (system: CanHaveMasterAXI4MMIOPort, th: HasHarnessInstantiators, ports: Seq[ClockedAndResetIO[AXI4Bundle]]) => {
    val p: Parameters = GetSystemParameters(system)
    (ports zip system.mmioAXI4Node.edges.in).map { case (port, edge) =>
      val mmio_mem = LazyModule(new SimAXIMem(edge, size = p(ExtBus).get.size)(p))
      withClockAndReset(port.clock, port.reset) {
        Module(mmio_mem.module).suggestName("mmio_mem")
      }
      mmio_mem.io_axi4.head <> port.bits
    }
  }
})

class WithTieOffInterrupts extends OverrideHarnessBinder({
  (system: HasExtInterruptsModuleImp, th: HasHarnessInstantiators, ports: Seq[UInt]) => {
    ports.foreach { _ := 0.U }
  }
})

class WithTieOffL2FBusAXI extends OverrideHarnessBinder({
  (system: CanHaveSlaveAXI4Port, th: HasHarnessInstantiators, ports: Seq[ClockedIO[AXI4Bundle]]) => {
    ports.foreach({ p =>
      p.bits := DontCare
      p.bits.aw.valid := false.B
      p.bits.w.valid := false.B
      p.bits.b.ready := false.B
      p.bits.ar.valid := false.B
      p.bits.r.ready := false.B
    })
  }
})


class WithSerialTLTiedOff extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessInstantiators, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = GetSystemParameters(system)
    ports.map({ port =>
      val bits = port.bits
      port.clock := false.B.asClock
      port.bits.out.ready := false.B
      port.bits.in.valid := false.B
      port.bits.in.bits := DontCare
    })
  }
})

class WithSimTSIOverSerialTL extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessInstantiators, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = GetSystemParameters(system)
    ports.map({ port =>
      val bits = port.bits
      port.clock := th.harnessBinderClock
      val ram = TSIHarness.connectRAM(system.serdesser.get, bits, th.harnessBinderReset)
      val success = SimTSI.connect(Some(ram.module.io.tsi), th.harnessBinderClock, th.harnessBinderReset.asBool)
      when (success) { th.success := true.B }
    })
  }
})

class WithTraceIOPunchthrough extends OverrideIOBinder({
  (system: CanHaveTraceIOModuleImp) => {
    val ports: Option[TraceOutputTop] = system.traceIO.map { t =>
      val trace = IO(DataMirror.internal.chiselTypeClone[TraceOutputTop](t)).suggestName("trace")
      trace <> t
      trace
    }
    (ports.toSeq, Nil)
  }
})

class WithCustomBootPinPlusArg extends OverrideHarnessBinder({
  (system: CanHavePeripheryCustomBootPin, th: HasHarnessInstantiators, ports: Seq[Bool]) => {
    val pin = PlusArg("custom_boot_pin", width=1)
    ports.foreach(_ := pin)
  }
})

class WithClockAndResetFromHarness extends OverrideHarnessBinder({
  (system: HasChipyardPRCI, th: HasHarnessInstantiators, ports: Seq[Data]) => {
    implicit val p = GetSystemParameters(system)
    val clocks = ports.collect { case c: ClockWithFreq => c }
    ports.map ({
      case c: ClockWithFreq => {
        val clock = th.harnessClockInstantiator.requestClockMHz(s"clock_${c.freqMHz.toInt}MHz", c.freqMHz)
        c.clock := clock
      }
      case r: AsyncReset => r := th.harnessBinderReset.asAsyncReset
    })
  }
})
