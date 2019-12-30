package scalademo

import scala.io.StdIn
import scala.concurrent.Future
import scala.io.StdIn
import java.util.Properties

import scala.util.Random
import akka.actor.ActorSystem
import akka.actor.Status
import akka.pattern.{ask, pipe}
import scala.concurrent.duration._
import akka.util.Timeout
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.Done
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.dstream.{ConstantInputDStream, DStream}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql._
import org.apache.spark.mllib.clustering.KMeansModel
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.clustering.KMeans
import org.apache.spark.mllib._
import org.apache.spark.rdd.RDD

import scala.collection.mutable.HashMap
import org.apache.spark.{SparkConf, SparkContext}
import spray.json.DefaultJsonProtocol
import org.apache.spark._
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.graphx.util.GraphGenerators
import org.apache.spark.sql._
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import org.apache.log4j.{Level, Logger}

package object scalademo{
  val log = Logger.getLogger("DSModernization")
  log.setLevel(Level.INFO)

 case class ArgsConfig(port: Int = -1, spark: Boolean = true, yarn: Boolean = false,
   local: Boolean = false)

}
