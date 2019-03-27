# DeployDemo

# Configuration

Settings are specified in the file src/main/resource/reference.conf.

- port is the default port for http server - can be overridden by specifying -p <PORT> while launching
- data_dir is the root directory for model and data files that are read in spark.

Commands to setup data_dir :

```
mv data/model_files.tgz data_dir
cd data_dir
tar xzf model_files.tgz
```

ML model Pipelines currently in the model directory -

### rf Random forest

### mlp MultiLayer Percepteron

### logistic regression

### kmeans

There are http paths defined to specify a file to load as dataframe, to periodically
sample smaller dataframes from the original dataframe, run the sample through all the
ML models and get metrics. For now, metrics are printed to screen.

There is a http path to stop the metrics from being calculated.

# Sample command line -

## Server

To build :  
from top-level directory (directory where this readme file is present),

```
sbt package assemblyPackageDependecy
```

To run in yarn mode

```
spark-submit --master yarn --jars target/scala-2.11/deploydemo-assembly-0.1-deps.ja target/scala-2.11/deploydemo_2.11-0.1.jar -y
```

To run in standalone spark mode

```
spark-submit --master local[*] --jars target/scala-2.11/deploydemo-assembly-0.1-deps.j target/scala-2.11/deploydemo_2.11-0.1.jar -s
```

To specify port number to use for the http server

```
spark-submit --master yarn --jars target/scala-2.11/deploydemo-assembly-0.1-deps.ja target/scala-2.11/deploydemo_2.11-0.1.jar -y -p 9808
```

## UI
To start ui, in a different terminal

```
cd ui
npm start
```

# Sanity Check

Once server is running, http post requests can be made from a different terminal. Sample commands -

```
curl -H "Content-Type: application/json" -X POST -d '{"filepath" :"/user/mapr/projects/SparkStreaming/stream_test/sample500.csv", "hasHeader": "true", "inferSchema": "true", "sep":","}' http://0.0.0.0:9808/countlines

curl -H "Content-Type: application/json" -X POST -d '{"filepath" :"/user/mapr/projects/SparkStreaming/stream_test/sample500.csv", "hasHeader": "true", "inferSchema": "true", "sep":","}' http://0.0.0.0:9808/startMetrics

curl -H "Content-Type: application/json" -X POST -d '{"filepath" :"/user/mapr/projects/SparkStreaming/stream_test/sample500.csv", "hasHeader": "true", "inferSchema": "true", "sep":","}' http://0.0.0.0:9808/stopMetrics
```

To see elasticsearch data -

```
curl -H "Content-Type: application/json" -X GET 'localhost:9200/deploydemo/\_search?size=1000&pretty=true'
```
