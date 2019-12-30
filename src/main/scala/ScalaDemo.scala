package scalademo

import scala.io.StdIn
import scala.concurrent._
import scala.io.StdIn
import java.util.Properties
import java.io.File

import scala.util.{Random,Success,Failure}
import akka.actor.ActorSystem
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
import akka.util.ByteString
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql._
import org.apache.spark.rdd.RDD

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
//
import scala.collection.mutable._
import org.apache.spark.{SparkConf, SparkContext}
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import spray.json.DefaultJsonProtocol._
//
import org.apache.spark._
import org.apache.spark.sql._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.log4j.{Level, Logger}
//
import scala.language.postfixOps
import scalademo.log
import scalademo.ArgsConfig




class ScalademoSettings(config: com.typesafe.config.Config) {
  config.checkValid(ConfigFactory.defaultReference(), "demo")
  /* read base settings from reference.conf file*/
 val appName:String = config.getString("demo.app_name")
 val dataDir:String = config.getString("demo.data_dir")
 val clusterName:String = config.getString("demo.cluster_name")
 val port:Int = config.getInt("demo.port")
}

//
class ScalademoContext(config: com.typesafe.config.Config) {
  val settings = new ScalademoSettings(config)

  def this() {
    this(ConfigFactory.load())
  }

  def printSettings() {
    log.info("app_name="+settings.appName)
    log.info("port=" + settings.port)
    log.info("data_dir=" + settings.dataDir)
    log.info("cluster_name=" + settings.clusterName)
  }
}

//from ui request
case class DfLoadReq (
  val filepath: String,
  val hasHeader: String  = "false",
  val sep: String = ",",
  val inferSchema: String = "false")


object ScalaDemo {
  def apply(args: ArgsConfig) : ScalaDemo = new ScalaDemo(args)
}

class ScalaDemo(args: ArgsConfig) {

  implicit val system = ActorSystem("scalademo")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher


  var spark : SparkSession = null 
  var sc : SparkContext = null
  var df : DataFrame = null
  var load_info_df : DfLoadReq = null //DfLoadReq from the http call  that caused df to be loaded
  var train_df:DataFrame = null 
  var test_df:DataFrame = null
  var load_info_train_df : DfLoadReq = null //assigned to load_info_df of the source df


  val context = new ScalademoContext()
  context.printSettings()

  val DATA_DIR = context.settings.dataDir //this would be something like /user/mapr/....
  val MAPR_DATA_DIR = "/mapr/"+context.settings.clusterName+DATA_DIR //this would be something like /mapr/my.cluster.com/user/mapr/....

  if (args.spark||args.yarn) {
    log.info("Starting Spark Context...")
    println("Getting spark session...")
    spark = if (args.yarn) SparkSession.builder.appName(context.settings.appName).master("yarn").getOrCreate() else
    SparkSession.builder.appName(context.settings.appName).master("local[*]").getOrCreate()
  println("Successfully got spark session: "+spark)
  sc = spark.sparkContext
  val dummy_count = sc.parallelize(1 to 100).count()
  log.info(s"Got sparkContext $sc and dummy_count=$dummy_count")
  log.info(s"sc.getExecutorMemoryStatus = ${sc.getExecutorMemoryStatus}")
  }

  //format for unmarshalling and marshalling
  implicit val filePathFormat = jsonFormat4(DfLoadReq)
  //function to extrapct post parameters
  def loadDataframe(req: DfLoadReq) : DataFrame = {
    //if df already corresponds to the file pointed to by req, nothing to do
    if (df != null && load_info_df != null && load_info_df== req)  { 
      return df
    }
    //in case you get a new call
    if (df != null) df.unpersist()
    df = spark.read.format("csv")
      .option("header", req.hasHeader)
      .option("sep",req.sep)
      .option("inferSchema",req.inferSchema)
      .load(req.filepath)

      df.persist() //cache the dataframe
      load_info_df = req //store the corresponding req for comparison later
      return df
  }

  //split Data, df taken in from previous cache, 
  def getTestTrainDataframes(df: DataFrame, target:String, split: Array[Double]=Array(0.8,0.2), columns: Array[String]=Array(), SEED : Long = -1 ): Array[DataFrame] = {
    //If load_info_train_df matches with load_info_df, it means our train_df already corresponds to current df
    //so return current class members
    if (train_df != null && test_df != null && load_info_train_df != null && load_info_train_df==load_info_df)  { 
      return Array(train_df,test_df)
    }

    if (train_df != null) train_df.unpersist()
    if (test_df != null) test_df.unpersist()

    //
  val transformer = new Transform()
  val transformed_df = transformer.getTransformed(df,target, df.columns)

  transformer.saveToFile(DATA_DIR+"/transform")

  val trainTestArray  = if(SEED == -1) transformed_df.randomSplit(split) else transformed_df.randomSplit(split,SEED)

  train_df = trainTestArray(0) //set class member
  test_df = trainTestArray(1)  //set class member
  train_df.persist()
  test_df.persist()
  load_info_train_df = load_info_df

  return trainTestArray
  }


  	//not used ?
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
		//
		post {
			path("getSchema") {
				entity(as[DfLoadReq]) { obj => {
						log.info(s"route getSchema called")

        		implicit val timeout = Timeout(30 seconds)
						val count: Future[String] = Future[String] {
							if (sc != null) {
								val df = loadDataframe(obj)
								val schema = df.schema
								log.info(schema)
								schema.toString
							} else 
							s"You must enable spark to run this service"
						}
						onComplete(count) { 
							case Success(value) =>
								complete(value+"\n")
							case Failure(t) => complete("An error has occured: "+t.getMessage+"\n")
						}
					}// end of function with arg filePath
				} //end of entity(as[DfLoadReq])...
			}~ //end of path("countlines")
			//
			path("dfshow") {
				entity(as[DfLoadReq]) { obj => {
						log.info(s"route dfshow called")

        		implicit val timeout = Timeout(30 seconds)
						val count: Future[String] = Future[String] {
							if (sc != null) {
								val df = loadDataframe(obj)
								df.columns.toSeq.toString +"\n"+
								df.take(10)
								.map(x => x.toString)
								.toSeq.toString
							} else 
							s"You must enable spark to run this service"
						}
						onComplete(count) { 
							case Success(value) =>
								complete(value+"\n")
							case Failure(t) => complete("An error has occured: "+t.getMessage+"\n")
						}
					}// end of function with arg filePath
				} //end of entity(as[DfLoadReq])...
			}~ //end of path("dfShow")
		//targetcol
		path("logisticRegression" / """.*""".r ) { targetCol =>
			log.info(s"logisticRegression with targetCol $targetCol")

      		getTestTrainDataframes(df=df,target=targetCol)
			
			val lr = new LogisticRegressor()
      		val features = df.columns.filter( _ != targetCol)

			lr.train(train_df,features)
      		lr.saveToFile(DATA_DIR+"/models/logisticRegression")

      		//
    			val transformed_df  = lr.transform(test_df)
    			val coeff = lr.coefficients()
    	    val coeffJsonStr = coeff.toMap.toJson
    			val evaluate = new Evaluate(transformed_df)
    			val auc = evaluate.auc
    			val auprc = evaluate.auprc
    			val maxF1 = evaluate.maxF1
	    		val maxKS = evaluate.maxKS
	    		val rocStr =evaluate.rocString()

	        //
	    val jsonStr = s"""{ "estimator":"logisticRegression" , "areaUnderROC" : $auc, "auprc" : $auprc, "maxF1" : "$maxF1", "maxKS" : "$maxKS", "roc": "$rocStr", "coefficients" : $coeffJsonStr}"""
	    complete(jsonStr+"\n")
			}~
		path("randomForest" / """.*""".r ) { targetCol =>
			log.info(s"randomForestClassifier with targetCol $targetCol")
      			getTestTrainDataframes(df=df,target=targetCol)
				val rf = new RandomForest()
				rf.train(train_df)
		      rf.saveToFile(DATA_DIR+"/models/randomForest")
		      //create transformedDF
		      val transformed_df = rf.transform(test_df)
		      //evaluate based on transformedDF
		      val evaluate = new Evaluate(transformed_df)
			    val auc = evaluate.auc
			    val auprc = evaluate.auprc
          val maxF1 = evaluate.maxF1
          val maxKS = evaluate.maxKS
	        val rocStr =evaluate.rocString().trim()
      //
      val jsonStr = s"""{ "estimator":"randomForest" , "areaUnderROC" : $auc, "auprc" : $auprc, "maxF1" : "$maxF1", "maxKS" : "$maxKS", "roc": "$rocStr"}"""
      complete(jsonStr+"\n")
      }~
      path("kMeans" / """.*""".r ) { targetCol =>
        implicit val timeout = Timeout(30 seconds)
        log.info(s"kMeans with targetCol $targetCol")
        getTestTrainDataframes(df=df,target=targetCol)
        val rf = new Kmeans()
        rf.train(train_df)
        rf.saveToFile(DATA_DIR+"/models/kmeans")
        //create transformedDF
        val transformed_df = rf.transform(test_df)
        //evaluate based on transformedDF
        val evaluate = new EvaluateKmeans(transformed_df)
        val silhouette = evaluate.silhouette
        val jsonStr = s"""{ "estimator":"KMeans" , "silhouette" : $silhouette}"""
        complete(jsonStr+"\n")
      }~
      path("neuralNetwork" / """.*""".r ) { targetCol =>
        implicit val timeout = Timeout(30 seconds)
        log.info(s"neuralNetwork with targetCol $targetCol")
        getTestTrainDataframes(df=df,target=targetCol)
        val rf = new NeuralNetwork()
        rf.train(train_df)
        rf.saveToFile(DATA_DIR+"/models/neuralNetwork")
        //create transformedDF
        val transformed_df = rf.transform(test_df)
        //evaluate based on transformedDF
        val evaluate = new Evaluate(transformed_df)
        val auc = evaluate.auc
        val auprc = evaluate.auprc
        val maxF1 = evaluate.maxF1
        val maxKS = evaluate.maxKS
        val rocStr =evaluate.rocString().trim()
        //
        val jsonStr = s"""{ "estimator":"neuralNetwork" , "areaUnderROC" : $auc, "auprc" : $auprc, "maxF1" : "$maxF1", "maxKS" : "$maxKS", "roc": "$rocStr"}"""
        complete(jsonStr+"\n")
      }

    }// post
  }
  val port = if (args.port != -1) args.port else context.settings.port
  //val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", port)

  //hack for running forever
  val f = for {bindingFuture <- Http().bindAndHandle(route, "0.0.0.0", port)
  waitOnFuture <- Promise[Done].future }
  yield waitOnFuture

  log.info(s"Starting Web Server listening on port $port\n")
  Await.result(f,Duration.Inf) 

} // ScalaDemoService



