import React from "react";
import ReactDOM from "react-dom";
import "./index.css";
import "./Multiline.css";
import { LineChart } from "react-easy-chart";
//import mlp from "./nnet.png";
//import rforest from "./randomForest.png";
import bulb from "./bulb.png";
import axios from "axios";
import ReactHover from "react-hover";
import HoverText from "./hovertext.js";
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
          <input name="champ" type="checkbox" /> Champion/Challenger --->
          Specify Champion:{" "}
          <input type="text" value="ensemblejwb_001" readOnly />
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
            <div className="hover" style={{ height: "200px" }}>
              {HoverText["champion_challenger"].map((name, index) => (
                <p key={index}>{name}</p>
              ))}
            </div>
          </ReactHover.Hover>
        </ReactHover>
        <label>
          {" "}
          <input name="ab" type="checkbox" /> A/B Testing ---> Specify
          Proportions: <input type="text" value="0.8,0.1,0.1" readOnly />
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

///// Get file input from user
class ModelSelector extends React.Component {
  render() {
    return (
      <form onSubmit={this.props.handleSubmit}>
        <p>
          <b>Select multiple models to deploy... </b>
          <label>
            {" "}
            Avaliable models in repository:{" "}
            <select
              multiple={true}
              value={this.props.value}
              onChange={this.props.handleChange}
            >
              {this.props.options.map(item => (
                <option value={item} key={item}>
                  {item}
                </option>
              ))}
            </select>
          </label>
          <input type="submit" value="SUBMIT" />
        </p>
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

class Metrics extends React.Component {
  render() {
    return (
      <div>
        <form>
          <b> Sample dataframe, predict and evaluate</b>
          <label>
            {" "}
            Sample period :
            <input
              type="text"
              value={this.props.value}
              onChange={this.props.handleChange}
            />
            seconds
          </label>
        </form>
        <form
          onSubmit={this.props.handleStart}
          style={{ display: "inline-block" }}
        >
          <input type="submit" value="Start" />
        </form>
        <form
          onSubmit={this.props.handleStop}
          style={{ display: "inline-block" }}
        >
          <input type="submit" value="Stop" />
        </form>
        <div style={{ minWidth: "50px", display: "inline-block" }} />
        <form
          onSubmit={this.props.handleDelete}
          style={{ display: "inline-block" }}
        >
          <input type="submit" value="Delete old metrics" />
        </form>

        <div style={{ minHeight: "30px" }} />
      </div>
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
    chart.render(this.props.data);
    this.chart = chart;
  }

  componentDidUpdate(prevProps) {
    if (prevProps.data.length !== this.props.data.length) {
      console.log("Triggered componentDidUpdate");
      this.chart.render(this.props.data);
    }
  }

  componentWillReceiveProps(x) {
    console.log("props received: ", x);
  }

  shouldComponentUpdate(nextProps, nextState) {
    return true;
  }

  render() {
    console.log("Calling CanaryChart.render");
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

    function compareObjs(a, b) {
      if (a.date < b.date) return -1;
      else if (a.date > b.date) return 1;
      else return 0;
    }

    this.state = {
      server: "http://" + window.location.hostname + ":" + SERVER_PORT,
      models: [],
      model_options: [],
      datafile_options: [],
      datafile: "",
      metric_sample_period: 3,
      metric_data: [],
      metric_url:
        "http://" +
        window.location.hostname +
        ":9200/deploydemo/_search?size=1000&pretty=true"
    };
    var classHandle = this;
    axios
      .get(this.state.server + "/models")
      .then(response => {
        classHandle.setState({ model_options: response.data });
      })
      .catch(e => console.log(e));
    var url = this.state.server + "/dir";
    fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body:
        "/mapr/my.cluster.com/user/mapr/projects/SparkStreaming/stream_test",
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

  handleModelChange(event) {
    this.setState({
      models: Array.apply(null, event.target.options)
        .filter(o => o.selected)
        .map(o => o.value)
    });
  }

  handleModelSubmit(event) {
    var model_str = this.state.models.join(",");
    var url = this.state.server + "/selectModels/" + model_str;
    console.log("calling url : " + url);
    axios.get(url).catch(e => console.log(e));
    event.preventDefault();
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

  handleMetricStart(event) {
    var url =
      this.state.server + "/startMetrics/" + this.state.metric_sample_period;
    var body = JSON.stringify({
      filepath:
        "/user/mapr/projects/SparkStreaming/stream_test/" + this.state.datafile,
      hasHeader: "true",
      sep: ",",
      inferSchema: "true"
    });
    console.log("In handleMetricStart with url=" + url + "\n body=" + body);
    fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: body,
      mode: "cors"
    })
      .then(response => response.text())
      .then(data => console.log("startMetrics: " + data))
      .catch(e => console.log(e));
    event.preventDefault();
  }

  handleMetricStop(event) {
    var url = this.state.server + "/stopMetrics";
    //console.log("In handleMetricStop url = " + url);
    fetch(url)
      .then(response => response.text())
      .then(data => console.log("stopMetrics: " + data))
      .catch(e => console.log(e));
    event.preventDefault();
  }

  handleMetricDelete(event) {
    var url = this.state.server + "/deleteMetrics";
    console.log("In handleMetricStop url = " + url);
    fetch(url)
      .then(response => response.text())
      .then(data => console.log("deleteMetrics: " + data))
      .catch(e => console.log(e));
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
            <ModelSelector
              options={this.state.model_options}
              handleChange={x => this.handleModelChange(x)}
              handleSubmit={x => this.handleModelSubmit(x)}
            />
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
              value={this.state.metric_sample_period}
              handleChange={x => this.handlePeriodChange(x)}
              handleStart={x => this.handleMetricStart(x)}
              handleStop={x => this.handleMetricStop(x)}
              handleDelete={x => this.handleMetricDelete(x)}
            />
          </li>

          <li>
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
              data={this.state.metric_data}
              chart_id="metricchart"
              xAxisDateFormatStr="%x %X"
              metric_url={this.state.metric_url}
              metric_sample_period={this.state.metric_sample_period}
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

          <li />

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
              data={this.state.metric_data}
              chart_id="canarychart"
              xAxisDateFormatStr="%x %X"
            />
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
];

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
