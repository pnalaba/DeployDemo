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

