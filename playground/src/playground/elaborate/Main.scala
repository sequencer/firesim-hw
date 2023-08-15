package playground.elaborate

import chisel3.RawModule
import chisel3.stage.ChiselGeneratorAnnotation
import chisel3.stage.phases.Elaborate
import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation
import mainargs._
import org.chipsalliance.cde.config.{Config, Parameters}

object Main {
  @main def elaborate(
    @arg(name = "dir") dir:        String,
    @arg(name = "m") top:         String,
    @arg(name = "configs") config: String
  ) = {
    val parameters = config.split('+').foldRight(Parameters.empty) {
      case (currentName, config) =>
        val currentConfig = Class.forName(currentName).newInstance.asInstanceOf[Config]
        currentConfig ++ config
    }

    lazy val module = Class.forName(top)
      .getConstructor(classOf[Parameters])
      .newInstance(parameters)
      .asInstanceOf[RawModule]

    val annos = Seq(
      new Elaborate,
      new chisel3.hack.Convert
    ).foldLeft(
      Seq(
        TargetDirAnnotation(dir),
    ChiselGeneratorAnnotation (() => module)
  ): AnnotationSeq

  )
  {
    case (annos, phase) => phase.transform(annos)
  }
    .flatMap {
      case firrtl.stage.FirrtlCircuitAnnotation(circuit) =>
        os.write(os.Path(dir) / s"${module.name}.fir", circuit.serialize)
        None
      case _: chisel3.stage.DesignAnnotation[_] => None
      case a => Some(a)
    }
  os.write(os.Path(dir) / s"${module.name}.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
  freechips.rocketchip.util.ElaborationArtefacts.files.foreach { case (ext, contents) => os.write.over(os.Path(dir) / s"${config.mkString}.${ext}", contents()) }
}

def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
