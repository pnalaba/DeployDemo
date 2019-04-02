import React from "react";

class Metrics extends React.Component {
  constructor(props) {
    super(props);
    this.handleMetricStart = this.handleMetricStart.bind(this);
    this.handleMetricStop = this.handleMetricStop.bind(this);
    this.handleMetricDelete = this.handleMetricDelete.bind(this);
  }

  handleMetricStart(event) {
    event.preventDefault();
    var url =
      this.props.server +
      "/" +
      this.props.start_route +
      "/" +
      this.props.sample_period;
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
      .then(data => console.log("startMetrics: " + data))
      .catch(e => console.log(e));
  }

  handleMetricStop(event) {
    event.preventDefault();
    var url = this.props.server + "/" + this.props.stop_route;
    //console.log("In handleMetricStop url = " + url);
    fetch(url)
      .then(response => response.text())
      .then(data => console.log("stopMetrics: " + data))
      .catch(e => console.log(e));
  }

  handleMetricDelete(event) {
    var url = this.props.server + "/deleteMetricsChampion";
    console.log("In handleMetricDelete url = " + url);
    fetch(url)
      .then(response => response.text())
      .then(data => console.log("deleteMetrics: " + data))
      .catch(e => console.log(e));
    event.preventDefault();
  }

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
              value={this.props.sample_period}
              onChange={this.props.handleChange}
            />
            seconds
          </label>
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

export default Metrics;
