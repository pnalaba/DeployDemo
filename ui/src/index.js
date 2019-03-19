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
              {HoverText["multiarmed_bandit"].map((name, index) => (
                <p key={index}>{name}</p>
              ))}
            </div>
          </ReactHover.Hover>
        </ReactHover>
        <input type="submit" value="SUBMIT" />
      </form>
    );
  }
}

class DatafileSelector extends React.Component {
  render() {
    return (
      <form>
        <b>Select batch file to start scoring ... </b>
        <label>
          {" "}
          Avaliable batch files in repository:{" "}
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

class CanaryChart extends React.Component {
  componentDidMount() {
    var chart = makeLineChart(
      this.props.xName,
      this.props.yObjs,
      this.props.axisLabels,
      this.props.xAxisDateFormatStr
    );
    chart.bind("#" + this.props.chart_id);
    this.chart = chart;
    window.setTimeout(chart.update_svg_size, 1);
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
      "/mapr/my.cluster.com/user/mapr/projects/SparkStreaming/stream_test/";

    this.state = {
      server: "http://" + window.location.hostname + ":" + SERVER_PORT,
      datafile_options: [],
      datafile: "",
      metric_sample_period: 3,
      champion_metric_data: [],
      canary_data: [],
      elastic_server: "http://" + window.location.hostname + ":9200"
    };
    this.getMetricCallback = this.getMetricCallback.bind(this);
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
            />
          </li>

          <li>
            <label>Champion/Challenger Deployment Mode </label>
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
                <div className="hover" style={{ height: "200px" }}>
                  {HoverText["champion_challenger"].map((name, index) => (
                    <p key={index}>{name}</p>
                  ))}
                </div>
              </ReactHover.Hover>
            </ReactHover>

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
              axisLabels={{ xAxis: "Date", yAxis: "Normalized" }}
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
            <CanaryChart
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
            <label>
              A/B Testing Deployment mode Proportions:{" "}
              <input type="text" value="0.8,0.1,0.1" readOnly />
            </label>
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
                  {HoverText["ab_testing"].map((name, index) => (
                    <p key={index}>{name}</p>
                  ))}
                </div>
              </ReactHover.Hover>
            </ReactHover>

            <MetricChart
              xName="date"
              yObjs={{
                randomForest: { column: "randomForest" },
                neuralNet: { column: "multiLayerPercepteron" },
                logisticRegression: { column: "logisticRegression" },
                kmeansSilhouette: {
                  column: "silhouette",
                  linestyle: "dashed"
                }
              }}
              axisLabels={{ xAxis: "Date", yAxis: "Normalized" }}
              data={this.state.champion_metric_data}
              chart_id="abchart"
              xAxisDateFormatStr="%x %X"
              metric_url={this.state.metric_url}
              metric_sample_period={this.state.metric_sample_period}
              getMetricCallback={this.getMetricCallback}
            />
          </li>

          <li>
            <p>
              <b> Select one deployment option... </b>
            </p>
            <DeployOptions />
            <h2>Deploying the solution ... </h2>
            <br />
          </li>

          <li>
            <p>
              <b>5. Tracking performance over time with F1 statistic ...</b>
              <img src={bulb} alt="Logo" />{" "}
            </p>
            <LineChart
              axes
              axisLabels={{ x: "date", y: "data stability" }}
              margin={{ top: 10, right: 10, bottom: 50, left: 50 }}
              width={800}
              interpolate={"cardinal"}
              height={350}
              data={f1_data}
            />
          </li>
        </ol>
      </div>
    );
  }
}

/*
var canary_model_data = [
  [
    { x: 1, y: 1.0 },
    { x: 2, y: 1.1 },
    { x: 3, y: 0.8 },
    { x: 4, y: 1.5 },
    { x: 5, y: 1.4 },
    { x: 6, y: 1.1 },
    { x: 7, y: 1.3 },
    { x: 8, y: 1.5 },
    { x: 9, y: 1.7 },
    { x: 10, y: 1.7 },
    { x: 11, y: 1.9 },
    { x: 12, y: 1.7 }
  ]
]; */

var f1_data = [
  [
    { x: 1, y: 0.6 },
    { x: 2, y: 0.62 },
    { x: 3, y: 0.63 },
    { x: 4, y: 0.61 },
    { x: 5, y: 0.68 },
    { x: 6, y: 0.58 },
    { x: 7, y: 0.44 },
    { x: 8, y: 0.56 },
    { x: 9, y: 0.66 },
    { x: 10, y: 0.65 },
    { x: 11, y: 0.68 },
    { x: 12, y: 0.62 },
    { x: 13, y: 0.65 },
    { x: 14, y: 0.68 },
    { x: 15, y: 0.65 }
  ],
  [
    { x: 1, y: 0.6 },
    { x: 2, y: 0.61 },
    { x: 3, y: 0.63 },
    { x: 4, y: 0.64 },
    { x: 5, y: 0.65 },
    { x: 6, y: 0.64 },
    { x: 7, y: 0.63 },
    { x: 8, y: 0.62 },
    { x: 9, y: 0.65 },
    { x: 11, y: 0.62 },
    { x: 12, y: 0.63 },
    { x: 12, y: 0.67 },
    { x: 13, y: 0.63 },
    { x: 14, y: 0.62 },
    { x: 15, y: 0.61 }
  ],
  [
    { x: 1, y: 0.55 },
    { x: 2, y: 0.52 },
    { x: 3, y: 0.49 },
    { x: 4, y: 0.54 },
    { x: 5, y: 0.56 },
    { x: 6, y: 0.57 },
    { x: 7, y: 0.52 },
    { x: 8, y: 0.54 },
    { x: 9, y: 0.55 },
    { x: 10, y: 0.58 },
    { x: 11, y: 0.61 },
    { x: 12, y: 0.62 },
    { x: 13, y: 0.63 },
    { x: 14, y: 0.63 },
    { x: 15, y: 0.65 }
  ]
];

ReactDOM.render(<Calculator />, document.getElementById("root"));
