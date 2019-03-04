package streamdemo

import java.lang.Exception

import scala.concurrent.Future
import scala.util.control.{Exception, NonFatal}
import scala.io.Source
import scala.util.parsing.json._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.apache.log4j.{Level, Logger}

case class ArgsConfig(port: Int = -1, spark: Boolean = true, yarn: Boolean = false,
   local: Boolean = false)


object Boot {

  def main(args: Array[String]): Unit = {
    val level = Level.WARN
    Logger.getLogger("org").setLevel(level)
    Logger.getLogger("akka").setLevel(level)

    val parser = new scopt.OptionParser[ArgsConfig]("streamdemo") {
      head("streamdemo", "1.0")

      opt[Int]('p', "port").action( (x,c) =>
        c.copy(port = x) ).text("port")

      opt[Unit]('s',"spark").action( (x,c) =>
        c.copy(spark = true) ).text ("enable spark")

      opt[Unit]('y',"yarn").action( (x,c) =>
        c.copy(yarn = true) ).text ("enable yarn mode")

      opt[Unit]('l',"local").action( (x,c) =>
        c.copy(local = true) ).text ("run in local mode with no support for maprdb and maprfs")

      help("help").text("write the help output")
      version("version").text("version")
      note("write some notes here.")
    }

    parser.parse(args, ArgsConfig()) match {
      case Some(config) => val service = StreamDemo(config)
      case None => println("error parsing input parameters")
    }

  } // main
} // Boot
