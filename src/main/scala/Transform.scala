package scalademo

import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.PipelineModel
import org.apache.log4j.{Level, Logger}

object Transform {
  def apply( ) : Transform = new Transform()
}

class Transform() {
  val log = Logger.getRootLogger();
  var model: PipelineModel = null


  /* Prep: Create a features column vector and make target column categorical 
   * and split date into Test, Train */
  def getTransformed(df: DataFrame, target: String, columns: Array[String]=Array()) : DataFrame = {
    val sparkSession = df.sparkSession
    import sparkSession.implicits._

    //user df.columns if no columns parameter specified
    val myColumns = if (columns.length > 0 ) columns else df.columns
    //remove target from myColumns
    val features = myColumns.filter(_ != target)

    val assembler = new VectorAssembler().
    setInputCols(features).
    setOutputCol("features")

    val labelIndexer = new StringIndexer().
    setInputCol(target).
    setOutputCol("label")

    val pipeline = new Pipeline().
    setStages(Array(assembler,labelIndexer))

    model = pipeline.fit(df)
    val transformed_df = model.transform(df.na.fill(0))

    return transformed_df
  }

  def saveToFile(filepath: String=""){
    //if filepath  is non-empty, write pipeline to that filepath
    if (model == null) {
      log.info("ERRORR : pipeline null")
    }
    if (filepath != "") {
      model.write.overwrite().save(filepath)
    }
  }




}
