// import Mill dependency
import mill._
import mill.scalalib.publish._
import mill.define.Sources
import mill.modules.Util
import scalalib._
// support BSP
import mill.bsp._
// input build.sc from each repositories.
import $file.dependencies.chisel.build
import $file.dependencies.firrtl.build
import $file.dependencies.treadle.build
import $file.dependencies.cde.build
import $file.dependencies.`berkeley-hardfloat`.build
import $file.dependencies.`rocket-chip`.common
import $file.dependencies.`chisel-testers2`.build

// Global Scala Version
object ivys {
  val sv = "2.13.10"
  val upickle = ivy"com.lihaoyi::upickle:1.3.15"
  val oslib = ivy"com.lihaoyi::os-lib:0.7.8"
  val pprint = ivy"com.lihaoyi::pprint:0.6.6"
  val utest = ivy"com.lihaoyi::utest:0.7.10"
  val jline = ivy"org.scala-lang.modules:scala-jline:2.12.1"
  val scalatest = ivy"org.scalatest::scalatest:3.2.2"
  val scalatestplus = ivy"org.scalatestplus::scalacheck-1-14:3.1.1.1"
  val scalacheck = ivy"org.scalacheck::scalacheck:1.14.3"
  val scopt = ivy"com.github.scopt::scopt:3.7.1"
  val playjson =ivy"com.typesafe.play::play-json:2.9.4"
  val breeze = ivy"org.scalanlp::breeze:1.1"
  val parallel = ivy"org.scala-lang.modules:scala-parallel-collections_3:1.0.4"
  val spire = ivy"org.typelevel::spire:0.17.0"
  val mainargs = ivy"com.lihaoyi::mainargs:0.4.0"
}

// For modules not support mill yet, need to have a ScalaModule depend on our own repositories.
trait CommonModule extends ScalaModule {
  override def scalaVersion = ivys.sv

  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(
    mychisel3.plugin.jar()
  ) }

  override def scalacOptions = T {
    super.scalacOptions() ++ Agg(s"-Xplugin:${mychisel3.plugin.jar().path}", "-Ymacro-annotations")
  }

  override def moduleDeps: Seq[ScalaModule] = Seq(mychisel3)
}


// Chips Alliance

object myfirrtl extends dependencies.firrtl.build.firrtlCrossModule(ivys.sv) {
  override def millSourcePath = os.pwd / "dependencies" / "firrtl"
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivys.pprint
  )
  override val checkSystemAntlr4Version = false
  override val checkSystemProtocVersion = false
  override val protocVersion = os.proc("protoc", "--version").call().out.text.dropRight(1).split(' ').last
  override val antlr4Version = os.proc("antlr4").call().out.text.split('\n').head.split(' ').last
}

object mychisel3 extends dependencies.chisel.build.chisel3CrossModule(ivys.sv) {
  override def millSourcePath = os.pwd / "dependencies" / "chisel"

  def firrtlModule: Option[PublishModule] = Some(myfirrtl)

  def treadleModule: Option[PublishModule] = Some(mytreadle)

  def chiseltestModule: Option[PublishModule] = Some(mychiseltest)

  override def scalacOptions = T {
    super.scalacOptions() ++ Agg("-Ymacro-annotations")
  }
}

object mytreadle extends dependencies.treadle.build.treadleCrossModule(ivys.sv) {
  override def millSourcePath = os.pwd /  "dependencies" / "treadle"

  def firrtlModule: Option[PublishModule] = Some(myfirrtl)
}

object mycde extends dependencies.cde.build.cde(ivys.sv) with PublishModule {
  override def millSourcePath = os.pwd /  "dependencies" / "cde" / "cde"
}

object myrocketchip extends dependencies.`rocket-chip`.common.CommonRocketChip {

  override def scalacOptions = T {
    Seq(s"-Xplugin:${mychisel3.plugin.jar().path}")
  }

  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(
    mychisel3.plugin.jar()
  ) }

  override def millSourcePath = os.pwd /  "dependencies" / "rocket-chip"

  override def scalaVersion = ivys.sv

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  def hardfloatModule: PublishModule = myhardfloat

  def cdeModule: PublishModule = mycde
}

object inclusivecache extends CommonModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-inclusive-cache" / 'design / 'craft / "inclusivecache"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

object blocks extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip-blocks"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip)
}

// UCB
object mychiseltest extends dependencies.`chisel-testers2`.build.chiseltestCrossModule(ivys.sv) {
  override def scalaVersion = ivys.sv
  def chisel3Module: Option[PublishModule] = Some(mychisel3)
  def treadleModule: Option[PublishModule] = Some(mytreadle)
}

object myhardfloat extends dependencies.`berkeley-hardfloat`.build.hardfloat {
  override def millSourcePath = os.pwd /  "dependencies" / "berkeley-hardfloat"

  override def scalaVersion = ivys.sv

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(
    mychisel3.plugin.jar()
    ) }
  
  override def scalacOptions = T {
    Seq(s"-Xplugin:${mychisel3.plugin.jar().path}", "-Ymacro-annotations")
  }
}


object testchipip extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "testchipip"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)
}

object icenet extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "icenet"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, testchipip)
}


object mdf extends CommonModule with SbtModule {

  override def millSourcePath = os.pwd / "dependencies" / "plsi-mdf"

  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, blocks)

  override def ivyDeps = Agg(
    ivys.playjson
  )
}

object firesim extends CommonModule with SbtModule { fs =>

  override def millSourcePath = os.pwd / "dependencies" / "firesim" / "sim"

  object midas extends CommonModule with SbtModule {

    override def millSourcePath = fs.millSourcePath / "midas"

    override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, targetutils, mdf)

    object targetutils extends CommonModule with SbtModule
  }

  object lib extends CommonModule with SbtModule {
    override def millSourcePath = fs.millSourcePath / "firesim-lib"

    override def moduleDeps = super.moduleDeps ++ Seq(midas, testchipip, icenet)
  }

  override def moduleDeps = super.moduleDeps ++ Seq(lib, midas)
}

object barstools extends CommonModule with SbtModule { bt =>
  override def millSourcePath = os.pwd / "dependencies" / "barstools"

  object macros extends CommonModule with SbtModule {
    override def millSourcePath = bt.millSourcePath / "macros"
    override def moduleDeps = super.moduleDeps ++ Seq(mdf)
  }

  object iocell extends CommonModule with SbtModule {
    override def millSourcePath = bt.millSourcePath / "iocell"
  }

  object tapeout extends CommonModule with SbtModule {
    override def millSourcePath = bt.millSourcePath / "tapeout"
  }

  override def moduleDeps = super.moduleDeps ++ Seq(macros, iocell, tapeout)
}
// Dummy

object playground extends CommonModule {
  override def moduleDeps = super.moduleDeps ++ Seq(myrocketchip, inclusivecache, blocks, barstools, icenet, firesim)

  // add some scala ivy module you like here.
  override def ivyDeps = Agg(
    ivys.oslib,
    ivys.pprint,
    ivys.mainargs
  )

  def module: String = "playground.harness.FireSim"

  def configs: String = "playground.harness.FireSimRocket4GiBDRAMConfig"

  def elaborate = T {
    mill.modules.Jvm.runSubprocess(
      finalMainClass(),
      runClasspath().map(_.path),
      forkArgs(),
      forkEnv(),
      Seq(
        "--dir", T.dest.toString,
        "--m", module,
        "--configs", configs
      ),
      workingDir = os.pwd,
    )
    PathRef(T.dest)
  }

  def verilog = T {
    os.proc("firtool",
      elaborate().path / s"${module.split('.').last}.fir",
      "--disable-annotation-unknown",
      "-dedup",
      "-O=debug",
      "--split-verilog",
      "--preserve-values=named",
      "--output-annotation-file=mfc.anno.json",
      s"-o=${T.dest}"
    ).call(T.dest)
    PathRef(T.dest)
  }

}
