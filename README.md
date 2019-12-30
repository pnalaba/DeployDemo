# demo for scala webserver with spark

To build

```
sbt compile assembly
```

To run the application on yarn cluster - example running on port 3902

```
spark-submit --master yarn --jars target/scala-2.11/scalademo-core-assembly-0.1-deps.jar target/scala-2.11/scalademo-core_2.11-0.1.jar -y -p 3902
```
To run the application on yarn cluster - example running on default port specified in src/main/resources/reference.conf file

```
spark-submit --master local[*] --jars target/scala-2.11/scalademo-core-assembly-0.1-deps.jar target/scala-2.11/scalademo-core_2.11-0.1.jar -s
```



To test, open the following url in the browser

```
http://hostname:PORT/test/spark/10000
```

it should show something like - "ret: Success(Pi is roughly + 3.1296)"

Examples to test post request handler -

To list files in a directory

```
curl -H "Content-Type: application/json" -X POST -d '{"filepath" :"/home/mapr"}' http://hostname:POST/dir
```

To count lines in a csv file

```
curl -H "Content-Type: application/json" -X POST -d '{"filepath" :"/user/mapr/projects/recommendationEngine/test.data", "hasHeader": "false", "sep": "\t", "inferSchema": "true"}' http://hostname:PORT/countlines

=> returns
ret: linecount = 100000
```

To show schema of a csv file

```
curl -H "Content-Type: application/json" -X POST -d '{"filepath" : "/user/mapr/ml-demo/Server/data/diabetes.csv", "hasHeader": "true", "sep": ",", "inferSchema" : "true"}' http://hostname:PORT/getSchema

=> returns
StructType(StructField(Pregnancies,IntegerType,true), StructField(Glucose,IntegerType,true), StructField(BloodPressure,IntegerType,true), StructField(SkinThickness,IntegerType,true), StructField(Insulin,IntegerType,true), StructField(BMI,DoubleType,true), StructField(DiabetesPedigreeFunction,DoubleType,true), StructField(Age,IntegerType,true), StructField(Outcome,IntegerType,true))
```

To show first 20 lines of csv file

```
curl -H "Content-Type: application/json" -X POST -d '{"filepath" : "/user/mapr/ml-demo/Server/data/diabetes.csv", "hasHeader": "true", "sep": ",", "inferSchema" : "true"}' http://hostname:PORT/dfshow

=> returns
WrappedArray(Pregnancies, Glucose, BloodPressure, SkinThickness, Insulin, BMI, DiabetesPedigreeFunction, Age, Outcome)
WrappedArray([6,148,72,35,0,33.6,0.627,50,1], [1,85,66,29,0,26.6,0.351,31,0], [8,183,64,0,0,23.3,0.672,32,1], [1,89,66,23,94,28.1,0.167,21,0], [0,137,40,35,168,43.1,2.288,33,1], [5,116,74,0,0,25.6,0.201,30,0], [3,78,50,32,88,31.0,0.248,26,1], [10,115,0,0,0,35.3,0.134,29,0], [2,197,70,45,543,30.5,0.158,53,1], [8,125,96,0,0,0.0,0.232,54,1])

```
