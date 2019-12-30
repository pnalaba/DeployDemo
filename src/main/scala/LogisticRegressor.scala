package scalademo

import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.ml._
import org.apache.spark.ml.classification._
/*import org.apache.spark.mllib.evaluation._*/
import scala.collection.mutable.Map
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.SparkContext._
import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.PipelineModel


//
class LogisticRegressor (
	MAX_ITER: Integer = 10,
	REG_PARAM: Double = 0.01 ) {

  val log = Logger.getRootLogger();


	var lrModel:  PipelineModel = null
	
	var myFeatures : Array[String] = null

  //regress trains on train_df, transforms test_df and returns transformed data
	def regress( train_df: DataFrame, test_df: DataFrame, features: Array[String])  = {
	//generates model
    train(train_df, features)
    //takes in {trained model, TestDF) => transformedDF //ready for scoring
    transform(test_df)
  }

  //train 
	def train( train_df: DataFrame, features: Array[String])  = {
		val lr = new LogisticRegression()
		.setMaxIter(MAX_ITER)
		.setRegParam(REG_PARAM)

    myFeatures = features //needed later for creating a dict from coefficients
    val pipeline = new Pipeline().setStages(Array(lr))
	  lrModel = pipeline.fit(train_df) //returns trained lr model
	}

	// we should get metrics out of this
  def transform(test_df: DataFrame) : DataFrame = {
		lrModel.transform(test_df) //return the transformed data
  }

  //
  def saveToFile(filepath: String=""){
    //if filepath  is non-empty, write pipeline to that filepath
    if (filepath != "") {
      lrModel.write.overwrite().save(filepath)
    }
  }

  /** Note -- areaUnderROC and roc do not depend on model.
   *  They depend on a transformed dataframe with columns "probability" and "label"
   *  So, moved it out this class */

	def coefficients() : Map[String, Double] = {
    val model = lrModel.stages(0).asInstanceOf[LogisticRegressionModel]
		val dict  = collection.mutable.Map[String, Double]()
		dict += ("Intercept" -> model.intercept)
		for ( pair <- myFeatures zip model.coefficients.toArray) {
			dict += pair
		}
		return dict
	}

}


