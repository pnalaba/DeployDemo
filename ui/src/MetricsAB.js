import React from "react";
import axios from "axios";

class MetricsAB extends React.Component {
  constructor(props) {
    super(props);
    this.handleMetricStart = this.handleMetricStart.bind(this);
    this.handleMetricStop = this.handleMetricStop.bind(this);
    this.handleMetricDelete = this.handleMetricDelete.bind(this);
    this.handleWeightChange = this.handleWeightChange.bind(this);
    this.state = {
      models: [],
      weights: [0]
    };
    var classHandle = this;
    axios
      .get(this.props.server + "/models")
      .then(response => {
        var models = response.data.filter(model => model !== "kmeans");
        classHandle.setState({ models: models });
        classHandle.setState({
          weights: new Array(models.length).fill(
            Number(1.0 / models.length).toFixed(2)
          )
        });
      })
      .catch(e => console.log(e));
  }

  handleMetricStart(event) {
    event.preventDefault();
    if (this.state.weights.reduce((a, b) => a + b) <= 0) return;
    var url =
      this.props.server +
      "/" +
      this.props.start_route +
      "/" +
      this.props.sample_period +
      "?" +
      "split=" +
      this.state.weights.join() +
      "&" +
      "algos=" +
      this.state.models.join();
    var body = JSON.stringify({
      filepath: this.props.data_dir + this.props.datafile,
      hasHeader: "true",
      sep: ",",
      inferSchema: "true"
    });
    //console.log("In handleMetricStart with url=" + url + "\n body=" + body);
    fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: body,
      mode: "cors"
    })
      .then(response => response.text())
      .then(data => console.log(this.props.name + " : startMetrics: " + data))
      .catch(e => console.log(e));
  }

  handleMetricStop(event) {
    event.preventDefault();
    var url = this.props.server + "/" + this.props.stop_route;
    //console.log("In handleMetricStop url = " + url);
    fetch(url)
      .then(response => response.text())
      .then(data => console.log(this.props.name + " : stopMetrics: " + data))
      .catch(e => console.log(e));
  }

  handleMetricDelete(event) {
    var url = this.props.server + "/" + this.props.delete_route;
    console.log(this.props.name + " : handleMetricDelete url = " + url);
    fetch(url)
      .then(response => response.text())
      .then(data => console.log("deleteMetrics: " + data))
      .catch(e => console.log(e));
    event.preventDefault();
  }

  handleWeightChange(i) {
    var className = this;
    return function(event) {
      var weights = className.state.weights.slice();
      weights[i] = event.target.value;
      className.setState({ weights: weights });
    };
  }

  render() {
    return (
      <div>
        <form>
          <b> Select models and weights</b>
          <table>
            <tbody>
              {this.state.models.map((m, i) => (
                <tr key={m}>
                  <td key={m + "-name"}>
                    <label key={m}>{m} </label>
                  </td>
                  <td key={m + "-input"}>
                    <input
                      key={m}
                      type="text"
                      i={i}
                      value={this.state.weights[i]}
                      onChange={this.handleWeightChange(i)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </form>

        <form
          onSubmit={this.handleMetricStart}
          style={{ display: "inline-block" }}
        >
          <input
            type="submit"
            value="Start"
            className="btn btn-primary btn-sm"
          />
        </form>
        <form
          onSubmit={this.handleMetricStop}
          style={{ display: "inline-block" }}
        >
          <input
            type="submit"
            value="Stop"
            className="btn btn-primary btn-sm"
          />
        </form>
        <div style={{ minWidth: "50px", display: "inline-block" }} />
        <form
          onSubmit={this.handleMetricDelete}
          style={{ display: "inline-block" }}
        >
          <input
            type="submit"
            value="Delete old metrics"
            className="btn btn-primary btn-sm"
          />
        </form>

        <div style={{ minHeight: "30px" }} />
      </div>
    );
  }
}

export default MetricsAB;
