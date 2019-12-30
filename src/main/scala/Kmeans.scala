package scalademo

import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.ml._
import org.apache.spark.ml.classification._
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.SparkContext._
import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.clustering.KMeans
import org.apache.spark.ml.clustering.KMeansModel
import org.apache.spark.ml.PipelineModel


//
class Kmeans  ( //Note : naming it different from the spark clas KMeans
	K: Integer = 5, NUM_TREES: Integer = 100, SEED: Long = -1)   {

  val log = Logger.getRootLogger();

	var kmeansModel: PipelineModel = null

	def regress( train_df: DataFrame, test_df: DataFrame) : DataFrame = {
    train(train_df)
    transform(test_df)
  }

	def train( train_df: DataFrame) = {
		val kmeans = new KMeans()
    .setFeaturesCol("features")
    .setPredictionCol("prediction")
    .setK(K)

    if (SEED != -1 ) {
      kmeans.setSeed(SEED)
    }

    val pipeline = new Pipeline().setStages(Array(kmeans))
		kmeansModel = pipeline.fit(train_df)
  }

  def transform(test_df: DataFrame) : DataFrame = {
		kmeansModel.transform(test_df)
	}

  def saveToFile(filepath: String=""){
    //if filepath  is non-empty, write pipeline to that filepath
    if (filepath != "") {
      kmeansModel.write.overwrite().save(filepath)
    }
  }


}


