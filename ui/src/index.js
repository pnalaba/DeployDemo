import React from "react";
import ReactDOM from "react-dom";
import "./index.css";
import { LineChart, Legend } from "react-easy-chart";
import { CsvToHtmlTable } from "react-csv-to-table";
import mlp from "./nnet.png";
import rforest from "./randomForest.png";
import bulb from "./bulb.png";
import axios from "axios";
import * as d3 from "d3";

class DeployOptions extends React.Component {
  render() {
    return (
      <form>
        <label>
          {" "}
          <input name="champ" type="checkbox" /> Champion/Challenger --->
          Specify Champion: <input type="text" value="ensemblejwb_001" />
        </label>
        <img src={bulb} alt="Logo" />
        <br />
        <label>
          {" "}
          <input name="ab" type="checkbox" /> A/B Testing ---> Specify
          Proportions: <input type="text" value="0.8,0.1,0.1" />
        </label>
        <img src={bulb} alt="Logo" />
        <br />
        <label>
          {" "}
          <input name="armed" type="checkbox" /> Multi-Armed Bandit ---> Bandit
          Parameters: <input type="text" value=" " />
        </label>
        <img src={bulb} alt="Logo" />
        <br />
        <input type="submit" value="SUBMIT" />
      </form>
    );
  }
}

///// Get file input from user
class ModelSelector extends React.Component {
  render() {
    return (
      <p>
        <b>Select multiple models to deploy... </b>
        <form onSubmit={this.props.handleSubmit}>
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
        </form>
      </p>
    );
  }
}

class DatafileSelector extends React.Component {
  render() {
    return (
      <p>
        <b>Select batch file to start scoring ... </b>
        <form>
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
      </p>
    );
  }
}

class Metrics extends React.Component {
  render() {
    return (
      <p>
        <b> Sample dataframe, predict and evaluate</b>
        <form onSubmit={this.props.handleStart}>
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
          <input type="submit" value="Start" />
        </form>
        <form onSubmit={this.props.handleStop}>
          <input type="submit" value="Stop" />
        </form>
      </p>
    );
  }
}

class MetricReader extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      data: [],
      componentWidth: 700,
      url:
        "http://" +
        window.location.hostname +
        ":9200/streamdemo/_search?size=1000&pretty=true",
      samplePeriod: props.samplePeriod //in seconds
    };
    this.handleGetData = this.handleGetData.bind(this);
    this.getData = this.getData.bind(this);
    this.intervalHandle = null;
  }

  getData() {
    console.log("fetching " + this.state.url);
    fetch(this.state.url, {
      method: "GET",
      headers: {
        "Content-Type": "application/json"
      },

      mode: "cors"
    })
      .then(response => response.json())
      .then(data => {
        var objs = data.hits.hits;
        objs.sort((a, b) => parseInt(a._id) - parseInt(b._id));
        const source = objs.map(s => s._source);
        const silhouette = source.map(s => {
          return { x: s.timestamp, y: s.silhouette };
        });
        const auc_rf = source.map(s => {
          return { x: s.timestamp, y: s.auc_rf };
        });
        const auc_mlp = source.map(s => {
          return { x: s.timestamp, y: s.auc_mlp };
        });
        this.setState({ data: [silhouette, auc_rf, auc_mlp] });
        //console.log([silhouette]);
      })
      .catch(e => console.log(e));
  }

  handleGetData(event) {
    if (this.intervalHandler != null) {
      clearInterval(this.intervalHandle);
    }
    this.getData();
    this.intervalHandle = setInterval(
      this.getData,
      this.state.samplePeriod * 1000
    );
    event.preventDefault();
  }

  render() {
    return (
      <div>
        <form onSubmit={this.handleGetData}>
          <input type="submit" value="GetData" />
        </form>
        <LineChart
          data={this.state.data}
          datePattern={"%Y-%m-%d %H:%M:%S"}
          xType={"time"}
          width={this.state.componentWidth}
          height={this.state.componentWidth / 2}
          axisLabels={{ x: "time" }}
          yDomainRange={[0, 1]}
          axes
          grid
          style={{
            ".line0": {
              stroke: "green"
            }
          }}
        />
        <Legend data={this.state.data} />
      </div>
    );
  }
}

class Calculator extends React.Component {
  constructor(props) {
    var SERVER_PORT = 9808;
    super(props);
    this.state = {
      server: "http://" + window.location.hostname + ":" + SERVER_PORT,
      models: [],
      model_options: [],
      datafile_options: [],
      datafile: null,
      samplePeriod: 15
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
      samplePeriod: event.target.value
    });
    event.preventDefault();
  }

  handleMetricStart(event) {
    var url = this.state.server + "/startMetrics/" + this.state.samplePeriod;
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
    console.log("In handleMetricStop url = " + url);
    fetch(url)
      .then(response => response.text())
      .then(data => console.log("stopMetrics: " + data))
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

          <li>
            <Metrics
              value={this.state.samplePeriod}
              handleChange={x => this.handlePeriodChange(x)}
              handleStart={x => this.handleMetricStart(x)}
              handleStop={x => this.handleMetricStop(x)}
            />
          </li>

          <li>
            <MetricReader samplePeriod={this.state.samplePeriod} />
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
            <LineChart
              axes
              axisLabels={{ x: "date", y: "data stability" }}
              margin={{ top: 10, right: 10, bottom: 50, left: 50 }}
              width={800}
              interpolate={"cardinal"}
              height={350}
              data={canary_model_data}
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
    { x: 10, y: 0.62 },
    { x: 11, y: 0.63 },
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
