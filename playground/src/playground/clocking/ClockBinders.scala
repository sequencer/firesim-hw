package playground.clocking

import chisel3._
import freechips.rocketchip.prci._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import barstools.iocell.chisel._
import playground.HasChipyardPRCI
import playground.iobinders.{GetSystemParameters, OverrideLazyIOBinder}

class ClockWithFreq(val freqMHz: Double) extends Bundle {
  val clock = Clock()
}

// This passes all clocks through to the TestHarness
class WithPassthroughClockGenerator extends OverrideLazyIOBinder({
  (system: HasChipyardPRCI) => {
    // Connect the implicit clock
    implicit val p = GetSystemParameters(system)
    val implicitClockSinkNode = ClockSinkNode(Seq(ClockSinkParameters(name = Some("implicit_clock"))))
    system.connectImplicitClockSinkNode(implicitClockSinkNode)
    InModuleBody {
      val implicit_clock = implicitClockSinkNode.in.head._1.clock
      val implicit_reset = implicitClockSinkNode.in.head._1.reset
      system.asInstanceOf[BaseSubsystem].module match { case l: LazyModuleImp => {
        l.clock := implicit_clock
        l.reset := implicit_reset
      }}
    }

    // This aggregate node should do nothing
    val clockGroupAggNode = ClockGroupAggregateNode("fake")
    val clockGroupsSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
    system.allClockGroupsNode := clockGroupAggNode := clockGroupsSourceNode

    InModuleBody {
      val reset_io = IO(Input(AsyncReset()))
      require(clockGroupAggNode.out.size == 1)
      val (bundle, edge) = clockGroupAggNode.out(0)

      val clock_ios = (bundle.member.data zip edge.sink.members).map { case (b, m) =>
        require(m.take.isDefined, s"""Clock ${m.name.get} has no requested frequency
                                     |Clocks: ${edge.sink.members.map(_.name.get)}""".stripMargin)
        val freq = m.take.get.freqMHz
        val clock_io = IO(Input(new ClockWithFreq(freq))).suggestName(s"clock_${m.name.get}")
        b.clock := clock_io.clock
        b.reset := reset_io
        clock_io
      }.toSeq
      ((clock_ios :+ reset_io), Nil)
    }
  }
})
