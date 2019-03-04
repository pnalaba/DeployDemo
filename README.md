# SparkFileStreaming
Loads the following pretrained ML models from files - 
### rf Random forest
### mlp MultiLayer Percepteron
### kmeans

There are http paths defined to specify a file to load as dataframe, to periodically
sample smaller dataframes from the original dataframe, run the sample through all the
ML models and get metrics. For now, metrics are printed to screen. 

TODO - save the metrics to a DB - elasticsearch or MaprDB

There is a http path to stop the metrics from being calculated.

Sample command line -

To run in yarn mode
spark-submit --master yarn target/scala-2.11/streamdemo-assembly-0.1.jar -y 

To run in standalone spark mode
spark-submit --master local[*] target/scala-2.11/streamdemo-assembly-0.1.jar -s

To specify port number to use for the http server
spark-submit --master yarn target/scala-2.11/streamdemo-assembly-0.1.jar -y -p 9802


Once server is running, http post requests can be made from a different terminal. Sample commands -

curl -H "Content-Type: application/json" -X POST -d '{"filepath" :"/user/mapr/projects/SparkStreaming/stream_test/sample500.csv", "hasHeader": "true", "inferSchema": "true", "sep":","}' http://0.0.0.0:9808/countlines

curl -H "Content-Type: application/json" -X POST -d '{"filepath" :"/user/mapr/projects/SparkStreaming/stream_test/sample500.csv", "hasHeader": "true", "inferSchema": "true", "sep":","}' http://0.0.0.0:9808/startMetrics

curl -H "Content-Type: application/json" -X POST -d '{"filepath" :"/user/mapr/projects/SparkStreaming/stream_test/sample500.csv", "hasHeader": "true", "inferSchema": "true", "sep":","}' http://0.0.0.0:9808/stopMetrics
