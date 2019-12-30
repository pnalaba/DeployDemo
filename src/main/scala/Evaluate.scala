package scalademo

import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.rdd.RDD
import org.apache.spark.ml.linalg.{Vectors,DenseVector}
import org.apache.spark.ml.classification._
// will be deprecated soon
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
//import org.apache.spark.mllib.stat.{MultivariateStatisticalSummary, Statistics}


//transformed_df
class Evaluate(transformed_df : DataFrame) {
  //
  val df = transformed_df
  val sparkSession = df.sparkSession
  import sparkSession.implicits._
  val results = df.select("probability", "label")


  val scoreAndLabels= results.rdd.map(x => ( x(0).asInstanceOf[DenseVector].apply(0), 1.0-x(1).asInstanceOf[Double])) 
  val metrics = new BinaryClassificationMetrics(scoreAndLabels)
  

  val auc= metrics.areaUnderROC()
  val auprc= metrics.areaUnderPR()
  val roc = metrics.roc
  val roc2 = metrics.roc.map(r=> (r._1,r._2,r._2-r._1))
  val f1Score = metrics.fMeasureByThreshold
  val maxF1 = f1Score.reduce((x, y) => if(x._2 > y._2) x else y)  
  val maxKS = roc2.reduce((x, y) => if(x._3 > y._3) x else y)


  def rocString(): String = {
        val rocDF = roc.toDF
	//take only a representation of roc
        //rocdf to string
        rocDF.columns.toSeq.toString +"\n"+rocDF.take(1000).map(x => x.toString).toSeq.toString
  }
}

