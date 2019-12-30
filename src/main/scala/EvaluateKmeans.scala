package scalademo

import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.rdd.RDD
import org.apache.spark.ml.linalg.{Vectors,DenseVector}
import org.apache.spark.ml.classification._
import org.apache.spark.ml.evaluation.ClusteringEvaluator


//transformed_df
class EvaluateKmeans(transformed_df : DataFrame) {
	//
  val df = transformed_df
  val sparkSession = df.sparkSession
  import sparkSession.implicits._
  val evaluator = new ClusteringEvaluator()
	

  val silhouette = evaluator.evaluate(df)
}

