import React from "react";
import ReactDOM from "react-dom";
import "./index.css";
import "./Multiline.css";
import { LineChart } from "react-easy-chart";
//import mlp from "./nnet.png";
//import rforest from "./randomForest.png";
import bulb from "./bulb.png";
import ReactHover from "react-hover";
import HoverText from "./hovertext.js";
import ModelSelector from "./ModelSelector.js";
import Metrics from "./Metrics.js";
import MetricsAB from "./MetricsAB.js";
import MetricChart from "./MetricChart.js";
import { makeLineChart } from "./Multiline.js";

const optionsCursorTrueWithMargin = {
  followCursor: true,
  shiftX: 20,
  shiftY: 0
};

class DeployOptions extends React.Component {
  render() {
    return (
      <form>
        <label>
          {" "}
          <input name="armed" type="checkbox" /> Multi-Armed Bandit ---> Bandit
          Parameters: <input type="text" value=" " readOnly />
        </label>
        <div style={{ display: "inline-flex" }}>
          <ReactHover
            options={optionsCursorTrueWithMargin}
            style={{ display: "inline-block" }}
          >
            <ReactHover.Trigger
              type="trigger"
              style={{ display: "inline-block" }}
            >
              <img src={bulb} alt="Logo" />
            </ReactHover.Trigger>
            <ReactHover.Hover type="hover">
              <div className="hover">
                <b>{HoverText["multiarmed_bandit"].title}</b>
                {HoverText["multiarmed_bandit"].description.map(
                  (name, index) => (
                    <p key={index}>{name}</p>
                  )
                )}
              </div>
            </ReactHover.Hover>
          </ReactHover>
        </div>
        <input type="submit" value="SUBMIT" />
      </form>
    );
  }
}

class DatafileSelector extends React.Component {
  render() {
    return (
      <form>
        <b>Select batch file to start scoring : </b>
        <label>
          <select value={this.props.value} onChange={this.props.handleChange}>
            {this.props.options.map(item => (
              <option value={item} key={item}>
                {item}
              </option>
            ))}
          </select>
        </label>
      </form>
    );
  }
}

class SimpleChart extends React.Component {
  componentDidMount() {
    var chart = makeLineChart(
      this.props.xName,
      this.props.yObjs,
      this.props.axisLabels,
      this.props.xAxisDateFormatStr
    );
    chart.bind("#" + this.props.chart_id);
    this.chart = chart;
    window.setTimeout(chart.update_svg_size, 3000);
  }

  render() {
    if (this.chart) {
      this.chart.render(this.props.data);
    }
    return (
      <div>
        <div id={this.props.chart_id} className="chart-wrapper" />
      </div>
    );
  }
}

class Calculator extends React.Component {
  constructor(props) {
    var SERVER_PORT = 9808;
    super(props);
    //var nowStr = new Date()
    //.toISOString()
    //.replace(/([^T]+)T([^\.]+).*/g, "$1 $2");
    //
    this.data_dir =
      "/mapr/my.cluster.com/user/mapr/ml-demo/Server/stream_test/";

    this.state = {
      server: "http://" + window.location.hostname + ":" + SERVER_PORT,
      datafile_options: [],
      datafile: "",
      metric_sample_period: 3,
      champion_metric_data: [],
      canary_data: [],
      multiarm_data: [],
      elastic_server: "http://" + window.location.hostname + ":9200"
    };
    this.getMetricCallback = this.getMetricCallback.bind(this);
    this.getMetricMultiArmCallback = this.getMetricMultiArmCallback.bind(this);
    var classHandle = this;
    var url = this.state.server + "/dir";
    fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: this.data_dir,
      mode: "cors"
    })
      .then(response => response.text())
      .then(data => {
        var files = data.split(",");
        classHandle.setState({
          datafile_options: files,
          datafile: files ? files[0] : null
        });
      })
      .catch(e => console.log(e));
  }

  getMetricCallback(champion_metric_data) {
    this.setState({ canary_data: champion_metric_data });
  }

  getMetricMultiArmCallback(multiarm_data) {
    this.setState({ multiarm_data: multiarm_data });
  }

  handleFileChange(event) {
    this.setState({
      datafile: Array.apply(null, event.target.options)
        .filter(o => o.selected)
        .map(o => o.value)
    });
    event.preventDefault();
  }

  handlePeriodChange(event) {
    this.setState({
      metric_sample_period: event.target.value
    });
    event.preventDefault();
  }

  render() {
    return (
      <div>
        <h1> User-Driven, Model Deployment Demo </h1>
        <h4>
          {" "}
          Second stage of a two-part demo. In part one, model(s) were trained.{" "}
          <br />
          In stage two, models that have been trained are put into production
          and monitored
          <br />
          The user can select which models to deploy, the method for
          orchestration and how to
        </h4>
        <ol>
          <li>
            <ModelSelector server={this.state.server} />
          </li>

          <li>
            <DatafileSelector
              value={this.state.datafile}
              options={this.state.datafile_options}
              handleChange={x => this.handleFileChange(x)}
            />
          </li>

          <h5>
            ---- In this demo, we simulate an input data stream by sampling from
            a big dataset ----
          </h5>

          <li>
            <Metrics
              sample_period={this.state.metric_sample_period}
              handleChange={x => this.handlePeriodChange(x)}
              server={this.state.server}
              datafile={this.state.datafile}
              data_dir={this.data_dir}
              start_route="startMetricsChampion"
              stop_route="stopMetricsChampion"
              delete_route="deleteMetricsChampion"
              name="MetricsChampion"
            />
          </li>

          <li>
            <label>Champion/Challenger Deployment Mode </label>
            <div style={{ display: "inline-flex" }}>
              <ReactHover options={optionsCursorTrueWithMargin}>
                <ReactHover.Trigger type="trigger">
                  <img src={bulb} alt="Logo" />
                </ReactHover.Trigger>
                <ReactHover.Hover type="hover">
                  <div className="hover" style={{ height: "200px" }}>
                    <b>{HoverText["champion_challenger"].title}</b>
                    {HoverText["champion_challenger"].description.map(
                      (name, index) => (
                        <p key={index}>{name}</p>
                      )
                    )}
                  </div>
                </ReactHover.Hover>
              </ReactHover>
            </div>

            <MetricChart
              xName="date"
              yObjs={{
                kmeansSilhouette: {
                  column: "silhouette",
                  linestyle: "dashed"
                },
                randomForest: { column: "randomForest" },
                neuralNet: { column: "multiLayerPercepteron" },
                logisticRegression: { column: "logisticRegression" }
              }}
              axisLabels={{ xAxis: "Date", yAxis: "AUC" }}
              data={this.state.champion_metric_data}
              chart_id="championchart"
              xAxisDateFormatStr="%x %X"
              elastic_server={this.state.elastic_server}
              elastic_index="deploydemo_champion"
              metric_sample_period={this.state.metric_sample_period}
              getMetricCallback={this.getMetricCallback}
            />
          </li>

          <li>
            <p>
              <b>
                Canary Model monitors Incoming Features as an early-warning
                system for performance impacts...
              </b>
              <img src={bulb} alt="Logo" />{" "}
            </p>
            <SimpleChart
              xName="date"
              yObjs={{
                kmeansSilhouette: { column: "silhouette" }
              }}
              axisLabels={{ xAxis: "Date", yAxis: "data stability" }}
              data={this.state.canary_data}
              chart_id="canarychart"
              xAxisDateFormatStr="%x %X"
            />
          </li>

          <li>
            <label>A/B Testing Deployment mode</label>
            <div style={{ display: "inline-flex" }}>
              <ReactHover options={optionsCursorTrueWithMargin}>
                <ReactHover.Trigger type="trigger">
                  <img src={bulb} alt="Logo" />
                </ReactHover.Trigger>
                <ReactHover.Hover type="hover">
                  <div className="hover">
                    <b>{HoverText["ab_testing"].title}</b>
                    {HoverText["ab_testing"].description.map((name, index) => (
                      <p key={index}>{name}</p>
                    ))}
                  </div>
                </ReactHover.Hover>
              </ReactHover>
            </div>

            <MetricsAB
              sample_period={this.state.metric_sample_period}
              server={this.state.server}
              datafile={this.state.datafile}
              data_dir={this.data_dir}
              start_route="startMetricsAB"
              stop_route="stopMetricsAB"
              delete_route="deleteMetricsAB"
              name="metricsAB"
            />
            <MetricChart
              xName="date"
              yObjs={{
                kmeansSilhouette: {
                  column: "silhouette",
                  linestyle: "dashed"
                },
                randomForest: { column: "randomForest" },
                neuralNet: { column: "multiLayerPercepteron" },
                logisticRegression: { column: "logisticRegression" }
              }}
              axisLabels={{ xAxis: "Date", yAxis: "AUC" }}
              data={this.state.champion_metric_data}
              chart_id="chartAB"
              xAxisDateFormatStr="%x %X"
              elastic_server={this.state.elastic_server}
              elastic_index="deploydemo_abtesting"
              metric_sample_period={this.state.metric_sample_period}
            />
          </li>

          <li>
            <label>MultiArm Bandit Deployment mode</label>
            <div style={{ display: "inline-flex" }}>
              <ReactHover options={optionsCursorTrueWithMargin}>
                <ReactHover.Trigger type="trigger">
                  <img src={bulb} alt="Logo" />
                </ReactHover.Trigger>
                <ReactHover.Hover type="hover">
                  <div className="hover">
                    <b>{HoverText["multiarmed_bandit"].title}</b>
                    {HoverText["multiarmed_bandit"].description.map(
                      (name, index) => (
                        <p key={index}>{name}</p>
                      )
                    )}
                  </div>
                </ReactHover.Hover>
              </ReactHover>
            </div>

            <MetricsAB
              sample_period={this.state.metric_sample_period}
              server={this.state.server}
              datafile={this.state.datafile}
              data_dir={this.data_dir}
              start_route="startMetricsMultiArm"
              stop_route="stopMetricsMultiArm"
              delete_route="deleteMetricsMultiArm"
              name="metricsMultiArm"
            />
            <MetricChart
              xName="date"
              yObjs={{
                kmeansSilhouette: {
                  column: "silhouette",
                  linestyle: "dashed"
                },
                randomForest: { column: "randomForest" },
                neuralNet: { column: "multiLayerPercepteron" },
                logisticRegression: { column: "logisticRegression" }
              }}
              axisLabels={{ xAxis: "Date", yAxis: "AUC" }}
              data={this.state.champion_metric_data}
              chart_id="chartMultiArm"
              xAxisDateFormatStr="%x %X"
              elastic_server={this.state.elastic_server}
              elastic_index="deploydemo_multiarm"
              metric_sample_period={this.state.metric_sample_period}
              getMetricCallback={this.getMetricMultiArmCallback}
            />
            <SimpleChart
              xName="date"
              yObjs={{
                kmeansSilhouette: { column: "silhouette", linestyle: "dashed" },
                randomForest: { column: "randomForest_split" },
                neuralNet: { column: "multiLayerPercepteron_split" },
                logisticRegresison: { column: "logisticRegression_split" }
              }}
              axisLabels={{ xAxis: "Date", yAxis: "split" }}
              data={this.state.multiarm_data}
              chart_id="multiarm_splits_chart"
              xAxisDateFormatStr="%x %X"
            />
          </li>
        </ol>
      </div>
    );
  }
}

ReactDOM.render(<Calculator />, document.getElementById("root"));
