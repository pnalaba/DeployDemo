package streamdemo

import java.io.File
import java.io.IOException
import java.util.Properties
import scala.collection.JavaConversions._
import scala.collection.mutable._
import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.actor.Status
import scala.util.{Random,Success,Failure}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import org.apache.spark._
import org.apache.spark.ml.evaluation._
import org.apache.spark.ml.PipelineModel
import org.apache.spark.mllib._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.{SparkConf, SparkContext}

import spray.json._
import spray.json.DefaultJsonProtocol._
//import spray.json.RootJsonFormat

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.log4j.Level
import org.apache.log4j.Logger
import streamdemo._


//imports for elasticsearch REST Api
import org.apache.http.HttpHost
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.{IndexRequest,IndexResponse}
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.elasticsearch.index.query.QueryBuilders 
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.{XContentType,XContentBuilder,XContentFactory}



object StreamDemo {
	def apply(args: ArgsConfig) : StreamDemo = new StreamDemo(args)
}

class StreamDemo(args: ArgsConfig) {
	implicit val system = ActorSystem("streamdemo")
	implicit val materializer = ActorMaterializer()
	implicit val executionContext = system.dispatcher

	val log = Logger.getRootLogger()
	log.setLevel(Level.INFO)


  //Class members
	var spark : SparkSession = null 
	var sc : SparkContext = null
	var df : DataFrame = null
	var load_info_df : DfLoadReq = null
	var model : Map[String,PipelineModel] = new HashMap[String,PipelineModel]() 
	var evalAUC : BinaryClassificationEvaluator = null
	var clusteringEvaluator : ClusteringEvaluator = null
	var metricGetterChampion : ActorRef = null
	var elasticClient : RestHighLevelClient = null
  var available_models : Array[String] = null
  var selected_models : collection.immutable.Map[String,PipelineModel] = null
  var state : Map[String,String] = new HashMap[String,String]()

	val METRICS_INDEX_CHAMPION : String = "deploydemo_champion"
	val METRICS_INDEX_ABTESTING : String = "deploydemo_abtesting"
	val METRICS_DOC_TYPE : String = "auc"


	val context = new StreamdemoContext()
	context.printSettings()
	val DATA_DIR = context.settings.dataDir
  val MAPRFS_PREFIX = "/mapr/my.cluster.com"

	val MODEL_DIR = DATA_DIR+"models/"



	class StreamdemoSettings(config: com.typesafe.config.Config) {
		config.checkValid(ConfigFactory.defaultReference(), "demo")
		
		val port = config.getInt("demo.port")
		val dataDir= config.getString("demo.data_dir")
		val appName = config.getString("demo.app_name")
		val elastic_port = config.getInt("demo.elastic_port")
		//val elastic_nodes = config.getStringList("demo.elastic_nodes").toList
		val elastic_clustername = config.getString("demo.elastic_clustername")
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
		val dummy_count = sc.parallelize(1 to 5).count()
		log.info(s"Got sparkContext $sc and dummy_count=$dummy_count")
		log.info(s"sc.getExecutorMemoryStatus = ${sc.getExecutorMemoryStatus}")
		//load the ml models with pretrained parameters

    //get list of models in model_dir
    available_models = listDirs(MAPRFS_PREFIX+MODEL_DIR)
    log.info("available_models : "+available_models.mkString(":"))

    for (model_name <- available_models) {
      model += (model_name -> PipelineModel.read.load(MODEL_DIR+model_name))
    }
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

	def send_to_elastic(index:String, doctype:String, m: Map[String, String]) : IndexResponse =  { 
		val timestamp = System.currentTimeMillis/1000
		val format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
		val builder = XContentFactory.jsonBuilder()
		builder.startObject();
		for ((k,v) <- m) {
		    builder.field(k, v)
		}
		builder.field("date",format.format(new java.util.Date()))
		builder.endObject()
		val indexRequest = new IndexRequest(index,doctype,timestamp.toString)
		        .source(builder)
		elasticClient.index(indexRequest)
	}

  def elastic_delete_docs_from_index(index:String, doctype:String) : String = {
    val del_request = new DeleteByQueryRequest(index);
    del_request.setConflicts("proceed");
    del_request.setQuery(QueryBuilders.matchAllQuery())
    val bulkResponse = elasticClient.deleteByQuery(del_request, RequestOptions.DEFAULT)
    return s"Deleted ${bulkResponse.getDeleted()}  of ${bulkResponse.getTotal()}\n"
  }



	def getMetrics(total_df: DataFrame, strategy: String = "champion") = {
		val data = Array[Map[String,String]]()
		for (i <- 0 to 4) {
      val df = getSampleDf(total_df) //sample a smaller df to simulate an incoming stream
      if (strategy == "champion") data :+ getChampionMetric(df) //championMetric calculates metric for all models
      else if(strategy == "abtesting") data:+ getABMetric(df,Array(ABobj("randomForest",0.9),ABobj("logisticRegression",0.1)))
		}
		data
	}

	def getChampionMetric(df: DataFrame,toElastic: Boolean = false) = {
		val pred_rf = model("randomForest").transform(df)
		val pred_mlp = model("multiLayerPercepteron").transform(df)
    val pred_lr = model("logisticRegression").transform(df)
		val pred_kmeans = model("kmeans").transform(df)
		val auc_rf = evalAUC.evaluate(pred_rf)
		val auc_mlp = evalAUC.evaluate(pred_mlp)
    val auc_lr = evalAUC.evaluate(pred_lr)
		val silhouette = clusteringEvaluator.evaluate(pred_kmeans)
		val m = new HashMap[String,String]()
		m.put("randomForest",auc_rf.toString)
		m.put("multiLayerPercepteron",auc_mlp.toString)
		m.put("logisticRegression",auc_lr.toString)
		m.put("silhouette",silhouette.toString)
		if (toElastic) {
      val indexResponse = send_to_elastic(METRICS_INDEX_CHAMPION,METRICS_DOC_TYPE,m)
    }
		log.info(s"auc_rf $auc_rf auc_mlp $auc_mlp auc_lr $auc_lr  silhouette $silhouette")
    m.toMap
	}

  case class ABobj ( algo: String , weight: Double )

  def getABMetric(df: DataFrame, ab_split:Array[ABobj],m: HashMap[String, String] = new HashMap[String,String](), toElastic: Boolean = false) = {
    val split_dfs = df.randomSplit(ab_split.map(_.weight))
    for ( i <- 0 to ab_split.length-1) {
      val algo = ab_split(i).algo
      val sub_df = split_dfs(i)
      val pred = model(algo).transform(sub_df)
      val auc = evalAUC.evaluate(pred)
      m.put(algo,auc.toString)
    }
    val pred_kmeans = model("kmeans").transform(df)
		val silhouette = clusteringEvaluator.evaluate(pred_kmeans)
		m.put("silhouette",silhouette.toString)
		if (toElastic) { 
      val indexResponse = send_to_elastic(METRICS_INDEX_ABTESTING,METRICS_DOC_TYPE,m)
    }
		log.info(s"ABMetric silhouette $silhouette auc : $m")
    m.toMap
	}

  def getSampleDf(total_df: DataFrame) = {
		val SAMPLE_FRACTION = 0.1
		val MAX_SAMPLE_SIZE=100
		val total_df_size = total_df.count()
		val sample_fraction = scala.math.min(SAMPLE_FRACTION,MAX_SAMPLE_SIZE.toDouble/total_df_size.toDouble)
		total_df.sample(sample_fraction)
  }




	class MetricGetterChampion(df : DataFrame, delay: FiniteDuration = 30.second ) extends Actor {
		var cancellable : akka.actor.Cancellable = null
		def receive = {
			case "tick" => 
				//send another periodic tick after the specified delay
				cancellable = system.scheduler.scheduleOnce(delay, self, "tick")
				// do something
        val sampled_df = getSampleDf(df) //sample a smaller df to simulate an incoming stream
				getChampionMetric(sampled_df,true) //calculate metrics and send to elastic
      case "stop" =>
        context stop self
		}
	}


	class MetricGetterAB(df : DataFrame, delay: FiniteDuration = 30.second, ab_split : Array[ABobj] ) extends Actor {
		var cancellable : akka.actor.Cancellable = null
		def receive = {
			case "tick" => 
				//send another periodic tick after the specified delay
				cancellable = system.scheduler.scheduleOnce(delay, self, "tick")
				// do something
				getABMetric(df,ab_split,toElastic=true) //calculate metrics and send to elastic
      case "stop" =>
        context stop self
		}
	}



	/** returns list of subdirectories in a directory **/
	def listDirs(dirpath: String): Array[String] = {
		log.info(s"Retrieving names of subdirectories in directory $dirpath")
    (new File(dirpath)).listFiles.filter(_.isDirectory).map(_.getName)
	}



	/** returns list of files in a directory **/
	def listFiles(dirpath: String): Array[String] = {
		log.info(s"Retrieving names of files in directory $dirpath")
		val d = new File(dirpath)
		if (d.exists && d.isDirectory) {
			d.listFiles.map(_.getName)
		} else {
			Array[String]() 
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
		path("stopMetricsChampion") {
			log.info(s"route stopMetricsChampion called")
			metricGetterChampion ! "stop"
      state -= "metric_state"
			complete("stopped metricGetterChampion")
		}~		
		path("deleteMetricsChampion") {
			log.info(s"route deleteMetricsChampion called")
      val result:Future[String] = Future[String] {
        try {
        elastic_delete_docs_from_index(METRICS_INDEX_CHAMPION,METRICS_DOC_TYPE);
        } catch {
          case x:IOException  => "Got an IOException"
        }
      }
      onComplete(result) { value => 
			  complete(s"ret: $value\n")
      }
		}~		
    path("models") {
      complete(available_models)
    }~
    pathPrefix("selectModels" ) {
      pathPrefix(Segment){ s =>
        val modelnames = s.split(",")
        log.info("model :"+s)
        selected_models = modelnames.map(x => x->model(x)).toMap
        complete("ok")
      }
    }~
		post {
      path("dir" ) {
        entity(as[String]) { dirname =>
          log.info(s"Doing dirname : $dirname")
	        val saved: Future[Array[String]] = Future{ listFiles(dirname) }
	        onComplete(saved) {
	        	case Success(files) => {
	        		val filesStr = files.mkString(",")
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
      path("startMetricsChampion" /IntNumber ) { seconds =>
				entity(as[DfLoadReq]) { obj => {
						log.info(s"route startMetricsChampion called with sampling period =$seconds")
        		implicit val timeout = Timeout(60 seconds)
						val df = loadDataframe(obj)
						if (metricGetterChampion != null ) {
							metricGetterChampion ! "stop"
						}
						metricGetterChampion = system.actorOf(Props(new MetricGetterChampion(df, Duration(seconds,"seconds"))), name="metricGetterChampion")
						metricGetterChampion ! "tick"
            state.put("metric_state","sampling")
            state.put("sampling_period", seconds.toString) //set class member
						complete(s"started metricGetterChampion with sampling period=$seconds seconds")
					}// end of function with arg filePath
				} //end of entity(as[DfLoadReq])...
			}~		
			path("getMetric") {
				entity(as[DfLoadReq]) { obj => {
						log.info(s"route getMetric called")
        		implicit val timeout = Timeout(60 seconds)
						val result: Future[collection.immutable.Map[String,String]] = Future[collection.immutable.Map[String,String]] {
							val df = loadDataframe(obj)
							val metric = getChampionMetric(df)
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
				waitOnFuture <- Promise[akka.Done].future }
				yield waitOnFuture
	log.info(s"Starting Web Server listening on port $port\n")

	/** setup elasticsearch client **/
	elasticClient = new RestHighLevelClient(
		RestClient.builder(new HttpHost("10.20.30.66",9200, "http"))
	)
	import org.elasticsearch.action.admin.indices.get.GetIndexRequest

  //for all indices create mapping to specify a date field - grafana needs one
  val indices = Array(METRICS_INDEX_CHAMPION, METRICS_INDEX_ABTESTING)
  indices.foreach( index => {
    val getIndexRequest = new GetIndexRequest().indices(index)  
    if (! elasticClient.indices().exists(getIndexRequest)) {
    	val createIndexRequest = new CreateIndexRequest(index).mapping( METRICS_DOC_TYPE, s"""
    	{ 
    	  "$METRICS_DOC_TYPE": {
    			"properties": {
    				"date": {
    					"type":   "date",
    					"format": "yyyy-MM-dd HH:mm:ss"
    				}
    			}
    		}
    	} """,XContentType.JSON)
    	elasticClient.indices().create(createIndexRequest)
    }
  })




	Await.result(f,Duration.Inf) 


} // StreamDemoService
