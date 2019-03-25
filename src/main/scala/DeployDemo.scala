package deploydemo

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
import akka.http.scaladsl.unmarshalling.PredefinedFromStringUnmarshallers.CsvSeq
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
import org.apache.spark.ml.Pipeline
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
import deploydemo._


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



object DeployDemo {
	def apply(args: ArgsConfig) : DeployDemo = new DeployDemo(args)
}

class DeployDemo(args: ArgsConfig) {
	implicit val system = ActorSystem("deploydemo")
	implicit val materializer = ActorMaterializer()
	implicit val executionContext = system.dispatcher

	val log = Logger.getLogger("deploydemo")
	log.setLevel(Level.INFO)


  //Class members
	var spark : SparkSession = null 
	var sc : SparkContext = null
	var df : DataFrame = null
	var load_info_df : Map[String,String] = null
	var model : Map[String,PipelineModel] = new HashMap[String,PipelineModel]() 
  var transformer : PipelineModel = null
	var evalAUC : BinaryClassificationEvaluator = null
	var clusteringEvaluator : ClusteringEvaluator = null
	var metricGetterChampion : ActorRef = null
	var metricGetterAB : ActorRef = null
	var metricGetterMultiArm : ActorRef = null
	var elasticClient : RestHighLevelClient = null
  var available_models : Array[String] = null
  var selected_models : collection.immutable.Map[String,PipelineModel] = null
  var state : Map[String,String] = new HashMap[String,String]()

	val METRICS_INDEX_CHAMPION : String = "deploydemo_champion"
	val METRICS_INDEX_ABTESTING : String = "deploydemo_abtesting"
	val METRICS_INDEX_MULTIARM : String = "deploydemo_multiarm"
	val METRICS_DOC_TYPE : String = "auc"


	val context = new StreamdemoContext()
	context.printSettings()
	val DATA_DIR = context.settings.dataDir
  val MAPRFS_PREFIX = context.settings.maprfsPrefix

	val MODEL_DIR = DATA_DIR+"/models"



	class StreamdemoSettings(config: com.typesafe.config.Config) {
		config.checkValid(ConfigFactory.defaultReference(), "demo")
		
		val port = config.getInt("demo.port")
		val dataDir= config.getString("demo.data_dir")
    val maprfsPrefix = config.getString("demo.maprfs_prefix")
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
      log.info("maprfs_prefix="+settings.maprfsPrefix)
		}
	}


  val DfLoad_DEFAULTS = Map("hasHeader" ->"true", "sep" -> ",", "inferSchema" -> "true")



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
    //
    //load tranformer

    transformer = PipelineModel.read.load(DATA_DIR+"/transform")
    //get list of models in model_dir
    available_models = listDirs(MAPRFS_PREFIX+MODEL_DIR)
    log.info("available_models : "+available_models.mkString(":"))

    for (model_name <- available_models) {
      model += (model_name -> PipelineModel.read.load(MODEL_DIR+"/"+model_name))
    }
		evalAUC = new BinaryClassificationEvaluator().setLabelCol("label").setMetricName("areaUnderROC").setRawPredictionCol("probability")
    clusteringEvaluator = new ClusteringEvaluator()

	}






	/** Function - given filename etc, returns a persisted dataframe**/
	def loadDataframe(req : Map[String,String]) : DataFrame = {
		if (df != null && load_info_df != null && (load_info_df.toSet diff req.toSet).size == 0 )  { 
			return df
		}
    log.info(s"In loadDataframe filepath=${req("filepath")} req=$req\n")
		df = spark.read.format("csv")
		.option("header", req("hasHeader"))
		.option("sep",req("sep"))
		.option("inferSchema",req("inferSchema"))
		.load(req("filepath"))

    log.info("In loadDataframe ,df.schema="+df.schema)


		df.persist() //cache the dataframe
		load_info_df = req
		return df
	}

	def send_to_elastic(index:String, doctype:String, m: Map[String, Double]) : IndexResponse =  { 
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
    val transformed_df = transformer.transform(df)
		val pred_rf = model("randomForest").transform(transformed_df)
		val pred_mlp = model("neuralNetwork").transform(transformed_df)
    val pred_lr = model("logisticRegression").transform(transformed_df)
		val pred_kmeans = model("kmeans").transform(transformed_df)
		val auc_rf = evalAUC.evaluate(pred_rf)
		val auc_mlp = evalAUC.evaluate(pred_mlp)
    val auc_lr = evalAUC.evaluate(pred_lr)
		val silhouette = clusteringEvaluator.evaluate(pred_kmeans)
		val m = new HashMap[String,Double]()
		m.put("randomForest",auc_rf)
		m.put("neuralNetwork",auc_mlp)
		m.put("logisticRegression",auc_lr)
		m.put("silhouette",silhouette)
		if (toElastic) {
      val indexResponse = send_to_elastic(METRICS_INDEX_CHAMPION,METRICS_DOC_TYPE,m)
    }
		log.info(s"ChampionMetric : $m")
    m.toMap
	}

  case class ABobj ( algo: String , weight: Double )

  def getABMetric(input_df: DataFrame, ab_split:Array[ABobj],m: HashMap[String, Double] = new HashMap[String,Double](), toElastic: Boolean = false) = {
    val df = transformer.transform(input_df)
    val split_dfs = df.randomSplit(ab_split.map(_.weight))
    for ( i <- 0 to ab_split.length-1) {
      val algo = ab_split(i).algo
      val sub_df = split_dfs(i)
      val pred = model(algo).transform(sub_df)
      val auc = evalAUC.evaluate(pred)
      m.put(algo,auc)
    }

    val pred_kmeans = model("kmeans").transform(df)
		val silhouette = clusteringEvaluator.evaluate(pred_kmeans)
		m.put("silhouette",silhouette)
		if (toElastic) { 
      val indexResponse = send_to_elastic(METRICS_INDEX_ABTESTING,METRICS_DOC_TYPE,m)
    }
		//log.info(s"ABMetric  : $m")
    m.toMap
	}


  def getMultiArmMetric(input_df: DataFrame, ab_split:Array[ABobj],m: HashMap[String, Double] = new HashMap[String,Double](), toElastic: Boolean = false) = {
    val df = transformer.transform(input_df)
    val split_dfs = df.randomSplit(ab_split.map(_.weight))
    for ( i <- 0 to ab_split.length-1) {
      val algo = ab_split(i).algo
      val sub_df = split_dfs(i)
      val pred = model(algo).transform(sub_df)
      val auc = evalAUC.evaluate(pred)
      m.put(algo,auc)
      m.put(algo+"_split",ab_split(i).weight)
    }
    val pred_kmeans = model("kmeans").transform(df)
		val silhouette = clusteringEvaluator.evaluate(pred_kmeans)
		m.put("silhouette",silhouette)
		if (toElastic) { 
      val indexResponse = send_to_elastic(METRICS_INDEX_MULTIARM,METRICS_DOC_TYPE,m)
    }
		log.info(s"MultArmMetric  : $m")
    m.toMap
	}

  def getSampleDf(total_df: DataFrame) = {
		val SAMPLE_FRACTION = 0.5
		val MAX_SAMPLE_SIZE=500
		val total_df_size = total_df.count()
		val sample_fraction = scala.math.min(SAMPLE_FRACTION,MAX_SAMPLE_SIZE.toDouble/total_df_size.toDouble)
		total_df.sample(sample_fraction)
  }




	class MetricGetterChampion(df : DataFrame, delay: FiniteDuration = 30.second ) extends Actor {
    var injectBadData : Boolean = false
    var bad_df: DataFrame = null 
		var cancellable : akka.actor.Cancellable = null
    def setBadData(df : DataFrame) {
      bad_df = df
      injectBadData = true
    }

		def receive = {
			case "tick" => 
				//send another periodic tick after the specified delay
				cancellable = system.scheduler.scheduleOnce(delay, self, "tick")
				// do something
        val sampled_df = if (injectBadData && bad_df != null) bad_df else  getSampleDf(df) //sample a smaller df to simulate an incoming stream
        injectBadData = false //we already should have injected bad data, so revert
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
        val sampled_df = getSampleDf(df) //sample a smaller df to simulate an incoming stream
				getABMetric(sampled_df,ab_split,toElastic=true) //calculate metrics and send to elastic
      case "stop" =>
        context stop self
		}
	}


	class MetricGetterMultiArm(df : DataFrame, delay: FiniteDuration = 30.second, var ab_split : Array[ABobj] ) extends Actor {
    //create new map with algos as keys
    var ab_split_map = ab_split.map({ case ABobj(algo,w) => (algo,w)}).toMap 
    //For each algo create a new Map with auc and count as keys
    var scorecard = ab_split_map.map({ case (algo,v) => algo -> Map("auc" -> (0.0).asInstanceOf[Double], "count" -> 0.asInstanceOf[Double])})
		var cancellable : akka.actor.Cancellable = null
		def receive = {
			case "tick" => 
				//send another periodic tick after the specified delay
				cancellable = system.scheduler.scheduleOnce(delay, self, "tick")
				// do something
        val sampled_df = getSampleDf(df) //sample a smaller df to simulate an incoming stream

				val current_auc = getMultiArmMetric(sampled_df,ab_split,toElastic=true) //calculate metrics and send to elastic
        val sampled_df_len = sampled_df.count()
        var auc_sum: Double = 0.0
        //calculating new_auc = (old_auc*count + auc_of_sample*sample_count)/(count+sample_count)
        scorecard = scorecard.map({ case (algo, m) => {
          val count = m("count")
          val sample_count = sampled_df_len*ab_split_map(algo)
          val auc:Double = ( m("auc")*count+current_auc(algo)*sample_count)/(count+sample_count)
          auc_sum += auc
          (algo,Map("auc" -> auc, "count" -> (count+sample_count)))
        }})
        //calculating new split ratio => higher auc gets higher split percentage
        ab_split = ab_split.map({case ABobj(a,s) => ABobj(a,scorecard(a)("auc")/auc_sum) })

        
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
      if (metricGetterChampion != null ) {
			  metricGetterChampion ! "stop"
      }
      state -= "champion_state"
			complete("stopped metricGetterChampion")
		}~		
		path("stopMetricsAB") {
			log.info(s"route stopMetricsAB called")
      if (metricGetterAB != null) {
			  metricGetterAB ! "stop"
      }
      state -= "ab_state"
			complete("stopped metricGetterAB")
		}~		
		path("stopMetricsMultiArm") {
			log.info(s"route stopMetricsMultiArm called")
      if (metricGetterMultiArm != null) {
			  metricGetterMultiArm ! "stop"
      }
      state -= "multiarm_state"
			complete("stopped metricGetterMultiArm")
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
		path("deleteMetricsAB") {
			log.info(s"route deleteMetricsAB called")
      val result:Future[String] = Future[String] {
        try {
        elastic_delete_docs_from_index(METRICS_INDEX_ABTESTING,METRICS_DOC_TYPE);
        } catch {
          case x:IOException  => "Got an IOException"
        }
      }
      onComplete(result) { value => 
			  complete(s"ret: $value\n")
      }
		}~		
		path("deleteMetricsMultiArm") {
			log.info(s"route deleteMetricsMultiArm called")
      val result:Future[String] = Future[String] {
        try {
        elastic_delete_docs_from_index(METRICS_INDEX_MULTIARM,METRICS_DOC_TYPE);
        } catch {
          case x:IOException  => "Got an IOException"
        }
      }
      onComplete(result) { value => 
			  complete(s"ret: $value\n")
      }
		}~		
      path("injectBadDataChampion") { 
				log.info(s"route injectBadDataChampion called")
				//metricGetterChampion.setBadData(df)
				complete(s"injected metricGetterChampion with badData")
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
				entity(as[JsObject]) { obj => {
						log.info(s"route counlines called with type of req=${obj.fields}\n")
            val reqMap = DfLoad_DEFAULTS ++ obj.fields.map({ case (k,v) => (k,v.toString.replace("\"",""))})
        		implicit val timeout = Timeout(30 seconds)
						val count: Future[String] = Future[String] {
							if (sc != null) {
								val df = loadDataframe(reqMap)
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
      path("getSchema") {
        entity(as[JsObject]) { obj => {
          log.info(s"route getSchema called with req=${obj.fields}")
          val reqMap = DfLoad_DEFAULTS ++ obj.fields.map({ case (k,v) => (k,v.toString.replace("\"",""))})

          implicit val timeout = Timeout(30 seconds)
          val count: Future[String] = Future[String] {
            if (sc != null) {
              val df = loadDataframe(reqMap)
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
      }~ //end of path("getSchema")
      path("startMetricsChampion" /IntNumber ) { seconds =>
				entity(as[JsObject]) { obj => {
						log.info(s"route startMetricsChampion called with sampling period =$seconds req=${obj.fields}")
        		implicit val timeout = Timeout(60 seconds)
            val reqMap = DfLoad_DEFAULTS ++ obj.fields.map({ case (k,v) => (k,v.toString.replace("\"",""))})
						val df = loadDataframe(reqMap)
						if (metricGetterChampion != null ) {
							metricGetterChampion ! "stop"
						}
						metricGetterChampion = system.actorOf(Props(new MetricGetterChampion(df, Duration(seconds,"seconds"))), name="metricGetterChampion")
						metricGetterChampion ! "tick"
            state.put("champion_state","sampling")
            state.put("champion_sampling_period", seconds.toString) //set class member
						complete(s"started metricGetterChampion with sampling period=$seconds seconds")
					}// end of function with arg filePath
				} //end of entity(as[DfLoadReq])...
			}~		
      (path("startMetricsAB" /IntNumber ) & parameters("split".as(CsvSeq[Double])) & parameters("algos".as(CsvSeq[String])) ) { (seconds , split, algos) => 
				entity(as[JsObject]) { obj => {
            val ab_split = (algos zip split).map({case (a,w) => ABobj(a,w)}).toArray
				    log.info(s"route startMetricsAB called with seconds=$seconds ab_split=${ab_split.mkString(", ")} req=${obj.fields}\n")
        		implicit val timeout = Timeout(60 seconds)
            val reqMap = DfLoad_DEFAULTS ++ obj.fields.map({ case (k,v) => (k,v.toString.replace("\"",""))})
						val df = loadDataframe(reqMap)
						if (metricGetterAB != null ) {
							metricGetterAB ! "stop"
						}
						metricGetterAB = system.actorOf(Props(new MetricGetterAB(df, Duration(seconds,"seconds"),ab_split)), name="metricGetterAB")
						metricGetterAB ! "tick"
            state.put("ab_state","sampling")
            state.put("ab_sampling_period", seconds.toString) //set class member

				    complete(s"route startMetricsAB called with seconds=$seconds ab_split=${ab_split.mkString(", ")}\n")
          }
        }
			}~		
      (path("startMetricsMultiArm" /IntNumber ) & parameters("split".as(CsvSeq[Double])) & parameters("algos".as(CsvSeq[String])) ) { (seconds , split, algos) => 
				entity(as[JsObject]) { obj => {
            val ab_split = (algos zip split).map({case (a,w) => ABobj(a,w)}).toArray
				    log.info(s"route startMetricsMultiArm called with seconds=$seconds ab_split=${ab_split.mkString(", ")} req=${obj.fields}\n")
        		implicit val timeout = Timeout(60 seconds)
            val reqMap = DfLoad_DEFAULTS ++ obj.fields.map({ case (k,v) => (k,v.toString.replace("\"",""))})
						val df = loadDataframe(reqMap)
						if (metricGetterMultiArm != null ) {
							metricGetterMultiArm ! "stop"
						}
						metricGetterMultiArm = system.actorOf(Props(new MetricGetterMultiArm(df, Duration(seconds,"seconds"),ab_split)), name="metricGetterMultiArm")
						metricGetterMultiArm ! "tick"
            state.put("multiarm_state","sampling")
            state.put("multiarm_sampling_period", seconds.toString) //set class member

				    complete(s"route startMetricsMultiArm called with seconds=$seconds ab_split=${ab_split.mkString(", ")}\n")
          }
        }
			}~		
			path("getMetric") {
				entity(as[JsObject]) { obj => {
						log.info(s"route getMetric called with req=${obj.fields}")
        		implicit val timeout = Timeout(60 seconds)
						val result: Future[collection.immutable.Map[String,Double]] = Future[collection.immutable.Map[String,Double]] {
              val reqMap = DfLoad_DEFAULTS ++ obj.fields.map({ case (k,v) => (k,v.toString.replace("\"",""))})
							val df = loadDataframe(reqMap)
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
  val indices = Array(METRICS_INDEX_CHAMPION, METRICS_INDEX_ABTESTING, METRICS_INDEX_MULTIARM)
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


} //class DeployDemo
