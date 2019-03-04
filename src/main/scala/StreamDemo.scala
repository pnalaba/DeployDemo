package streamdemo

import scala.io.StdIn
import scala.concurrent._
import scala.io.StdIn
import java.util.Properties
import java.io.File

import scala.util.{Random,Success,Failure}
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
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib._
import org.apache.spark.rdd.RDD

import scala.collection.mutable._
import org.apache.spark.{SparkConf, SparkContext}
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
//import spray.json.RootJsonFormat

import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import streamdemo._
import org.ojai.DocumentStream;
import org.ojai.store.DocumentMutation;
import org.ojai.store.QueryCondition;
import com.mapr.db.MapRDB
import com.mapr.db.Table
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.log4j.{Level, Logger}

import scala.language.postfixOps
import org.apache.spark.ml.PipelineModel
import org.apache.spark.ml.evaluation._


object StreamDemo {
	def apply(args: ArgsConfig) : StreamDemo = new StreamDemo(args)
}

class StreamDemo(args: ArgsConfig) {
	implicit val system = ActorSystem("streamdemo")
	implicit val materializer = ActorMaterializer()
	implicit val executionContext = system.dispatcher

	val log = Logger.getRootLogger()
	log.setLevel(Level.INFO)


	var spark : SparkSession = null 
	var sc : SparkContext = null
	var df : DataFrame = null
	var load_info_df : DfLoadReq = null
	var model_rf : PipelineModel = null
	var model_mlp : PipelineModel = null
	var model_kmeans : PipelineModel = null
	var evalAUC : BinaryClassificationEvaluator = null
	var clusteringEvaluator : ClusteringEvaluator = null
	var metricGetter : ActorRef = null


	val context = new StreamdemoContext()
	context.printSettings()
	val DATA_DIR = context.settings.dataDir

	val MODEL_RF_DIR = DATA_DIR+"/testrf"
	val MODEL_MLP_DIR = DATA_DIR+"/testmlp"
	val MODEL_KMEANS_DIR = DATA_DIR+"/testkmeans"

	class StreamdemoSettings(config: com.typesafe.config.Config) {
		config.checkValid(ConfigFactory.defaultReference(), "demo")
		
		val port = config.getInt("demo.port")
		val dataDir= config.getString("demo.data_dir")
		val appName = config.getString("demo.app_name")
	}

	class StreamdemoContext(config: com.typesafe.config.Config) {
		val settings = new StreamdemoSettings(config)
		
		def this() {
			this(ConfigFactory.load())
		}

		def printSettings() {
			log.info("port=" + settings.port)
			log.info("data_dir=" + settings.dataDir)
		}
	}

	//from ui request
	case class DfLoadReq (
		val filepath: String,
		val hasHeader: String  = "false",
		val sep: String = ",",
		val inferSchema: String = "false")

	case class DataChunk(auc_rf: Double, auc_mlp: Double, silhouette : Double)
	object DataChunk {
		implicit val dataChunkJsonFormat: RootJsonFormat[DataChunk] = jsonFormat3(DataChunk.apply)
	}




	if (args.spark||args.yarn) {
		log.info("Starting Spark Context...")
		println(s"Getting spark session... yarn:${args.yarn} spark:${args.spark}")
		spark = if (args.yarn) {
			println("Starting spark on yarn")
			SparkSession.builder.appName(context.settings.appName).master("yarn").getOrCreate() 
		} else {
			SparkSession.builder.appName(context.settings.appName).master("local[*]").getOrCreate()
		}
		println("Successfully got spark session: "+spark)
		sc = spark.sparkContext
		val dummy_count = sc.parallelize(1 to 100).count()
		log.info(s"Got sparkContext $sc and dummy_count=$dummy_count")
		log.info(s"sc.getExecutorMemoryStatus = ${sc.getExecutorMemoryStatus}")
		//load the ml models with pretrained parameters
		model_rf = PipelineModel.read.load(MODEL_RF_DIR)
		model_mlp = PipelineModel.read.load(MODEL_MLP_DIR)
		model_kmeans = PipelineModel.read.load(MODEL_KMEANS_DIR)
		evalAUC = new BinaryClassificationEvaluator().setLabelCol("target").setMetricName("areaUnderROC").setRawPredictionCol("probability")
		clusteringEvaluator = new ClusteringEvaluator()
	}

	//format for unmarshalling and marshalling
	implicit val filePathFormat = jsonFormat4(DfLoadReq)


	/** Function - given filename etc, returns a persisted dataframe**/
	def loadDataframe(req: DfLoadReq) : DataFrame = {
		if (df != null && load_info_df != null && load_info_df== req)  { 
			return df
		}
		val df_ = spark.read.format("csv")
		.option("header", req.hasHeader)
		.option("sep",req.sep)
		.option("inferSchema",req.inferSchema)
		.load(req.filepath)


		df_.persist() //cache the dataframe
		df = df_ //set class member
		load_info_df = req
		return df_
	}


	def getMetrics(total_df: DataFrame) = {
		val data = Array[DataChunk]()
		for (i <- 0 to 4) {
			data :+ getMetric(total_df)
		}
		data
	}

	def getMetric(total_df: DataFrame) = {
		val SAMPLE_FRACTION = 0.1
		val MAX_SAMPLE_SIZE=100
		val total_df_size = total_df.count()
		val sample_fraction = scala.math.min(SAMPLE_FRACTION,MAX_SAMPLE_SIZE.toDouble/total_df_size.toDouble)
		val df = total_df.sample(sample_fraction)
		val pred_rf = model_rf.transform(df)
		val pred_mlp = model_mlp.transform(df)
		val pred_kmeans = model_kmeans.transform(df)
		val data = Array[DataChunk]()
		val auc_rf = evalAUC.evaluate(pred_rf)
		val auc_mlp = evalAUC.evaluate(pred_mlp)
		val silhouette = clusteringEvaluator.evaluate(pred_kmeans)
		log.info(s"auc_rf $auc_rf auc_mlp $auc_mlp silhouette $silhouette")
		DataChunk(auc_rf,auc_mlp,silhouette)
	}


	class MetricGetter(df : DataFrame, delay: FiniteDuration = 30.second) extends Actor {
		var cancellable : akka.actor.Cancellable = null
		def receive = {
			case "tick" => 
				//send another periodic tick after the specified delay
				cancellable = system.scheduler.scheduleOnce(delay, self, "tick")
				// do something
				log.info("tick")		
				getMetric(df)
			case "stop" => 
				cancellable.cancel()
		}
	}



	/** returns list of files in a directory **/
	def listFiles(dirpath: String): Future[List[String]] = {
		log.info(s"Retrieving names of files in directory $dirpath")
		val d = new File(dirpath)
		if (d.exists && d.isDirectory) {
			Future {d.listFiles.map(_.getPath).toList}
		} else {
			Future { List[String]() }
		}
	}

	val route: Route = cors() {
		path("") {
			getFromResource("app/index.html")
		}~
		pathPrefix("") {
			getFromResourceDirectory("app")
		}~
		pathPrefix("test") {
			path("spark" / IntNumber) { nsamples =>
				log.info("testing spark")
				val pi: Future[String] = Future[String] {
					if (sc != null) {
						val n = nsamples
						val count = sc.parallelize(1 to n).map { i =>
							val x = Math.random()
							val y = Math.random()
							if (x * x + y * y < 1) 1 else 0
						}.reduce(_ + _)

						val res = s"Pi is roughly + ${4.0 * count/n}"
						res
					} else
					s"You must enable spark to run this service"
				}
				onComplete(pi) { value =>
					complete(s"ret: $value")
				}
			}
		}~
		path("stopMetrics") {
			log.info(s"route stopMetrics called")
			metricGetter ! "stop"
			complete("stopped metricGetter")
		}~		
		post {
			path("dir") {
				entity(as[DfLoadReq]) { obj =>
					val saved: Future[List[String]] = listFiles(obj.filepath)
					onComplete(saved) {
						case Success(files) => {
							val filesStr = files.mkString("\n")+"\n"
							complete(filesStr)
						}
						case Failure(t) => complete("An error has occured: "+t.getMessage)
					}
				}
			}~
			path("countlines") {
				entity(as[DfLoadReq]) { obj => {
						log.info(s"route counlines called")
        		implicit val timeout = Timeout(30 seconds)
						val count: Future[String] = Future[String] {
							if (sc != null) {
								val df = loadDataframe(obj)
								val linecount = df.count()
								s"linecount = $linecount"
							} else
							s"You must enable spark to run this service"
						}
						onComplete(count) { 
							case Success(value) =>
								complete(s"ret: $value\n")
							case Failure(t) => complete("An error has occured: "+t.getMessage+"\n")
						}
					}// end of function with arg filePath
				} //end of entity(as[DfLoadReq])...
			}~		
			path("startMetrics") {
				entity(as[DfLoadReq]) { obj => {
						log.info(s"route startMetrics called")
        		implicit val timeout = Timeout(60 seconds)
						val df = loadDataframe(obj)
						if (metricGetter == null ) {
							metricGetter = system.actorOf(Props(new MetricGetter(df)), name="metricGetter")
						}
						metricGetter ! "tick"
						complete("started metricGetter")
					}// end of function with arg filePath
				} //end of entity(as[DfLoadReq])...
			}~		
			path("getMetric") {
				entity(as[DfLoadReq]) { obj => {
						log.info(s"route getMetric called")
        		implicit val timeout = Timeout(60 seconds)
						val result: Future[DataChunk] = Future[DataChunk] {
							val df = loadDataframe(obj)
							val metric = getMetric(df)
							metric
						}
						onComplete(result) { 
							case Success(value) =>
								complete(value)
							case Failure(t) => complete("An error has occured: "+t.getMessage+"\n")
						}
					}// end of function with arg filePath
				} //end of entity(as[DfLoadReq])...
			}
		}	
	} // Route



	val port = if (args.port != -1) args.port else context.settings.port
	//val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", port)

	//hack for running forever
	val f = for {bindingFuture <- Http().bindAndHandle(route, "0.0.0.0", port)
				waitOnFuture <- Promise[Done].future }
				yield waitOnFuture

	log.info(s"Starting Web Server listening on port $port\n")
	Await.result(f,Duration.Inf) 


} // StreamDemoService
