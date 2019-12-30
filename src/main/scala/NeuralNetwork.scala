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
import org.apache.spark.ml.linalg.DenseVector
import org.apache.spark.ml.PipelineModel


//
class NeuralNetwork  (
	BLOCK_SIZE: Int = 128, MAX_ITER: Int = 100  , SEED: Long = -1, layers:Array[Int] = Array( 3, 3, 2))   {

  val log = Logger.getRootLogger();

	var nnModel : PipelineModel = null

	def regress( train_df: DataFrame, test_df: DataFrame) : DataFrame = {
    train(train_df)
    transform(test_df)
  }

	def train( train_df: DataFrame) = {
    val featuresVec= train_df.select("features").take(1)(0).get(0).asInstanceOf[DenseVector]
		val nn = new MultilayerPerceptronClassifier()
		.setLabelCol("label")
    .setFeaturesCol("features")
    .setLayers(Array(featuresVec.size) ++ layers) //size of first layer = num input cols
    .setBlockSize(BLOCK_SIZE)
    .setMaxIter(MAX_ITER)

    if (SEED != -1 ) {
      nn.setSeed(SEED)
    }

    val pipeline = new Pipeline().setStages(Array(nn))

		nnModel = pipeline.fit(train_df)
  }

  def transform(test_df: DataFrame) : DataFrame = {
		nnModel.transform(test_df)
	}

  def saveToFile(filepath: String=""){
    //if filepath  is non-empty, write pipeline to that filepath
    if (filepath != "") {
      nnModel.write.overwrite().save(filepath)
    }
  }

}


