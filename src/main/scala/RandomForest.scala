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
import org.apache.spark.ml.PipelineModel


//
class RandomForest  (
	MAX_BINS: Integer = 500, NUM_TREES: Integer = 100, SEED: Long = -1)   {

  val log = Logger.getRootLogger();

	var rfModel: PipelineModel  = null

	def regress( train_df: DataFrame, test_df: DataFrame) : DataFrame = {
    train(train_df)
    transform(test_df)
  }

	def train( train_df: DataFrame) = {
		val rf = new RandomForestClassifier()
		.setLabelCol("label")
    .setFeaturesCol("features")
    .setMaxBins(MAX_BINS)
    .setNumTrees(NUM_TREES)

    if (SEED != -1 ) {
      rf.setSeed(SEED)
    }
    val pipeline = new Pipeline().setStages(Array(rf))

		rfModel = pipeline.fit(train_df)
  }

  def transform(test_df: DataFrame) : DataFrame = {
		rfModel.transform(test_df)
	}

  def saveToFile(filepath: String=""){
    //if filepath  is non-empty, write pipeline to that filepath
    if (filepath != "") {
      rfModel.write.overwrite().save(filepath)
    }
  }


}


