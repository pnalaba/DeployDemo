import React from "react";
import { makeLineChart } from "./Multiline.js";
class MetricChart extends React.Component {
  constructor(props) {
    super(props);
    this.handleGetMetricData = this.handleGetMetricData.bind(this);
    this.handleStopMetricData = this.handleStopMetricData.bind(this);
    this.getMetricData = this.getMetricData.bind(this);
    this.state = { metric_data: [] };
    if (this.props.metric_data) {
      this.setState({ metric_data: this.props.metric_data });
    }
    this.metric_url =
      this.props.elastic_server +
      "/" +
      this.props.elastic_index +
      "/_search?size=1000&pretty=true";
  }
  componentDidMount() {
    var chart = makeLineChart(
      this.props.xName,
      this.props.yObjs,
      this.props.axisLabels,
      this.props.xAxisDateFormatStr
    );
    chart.bind("#" + this.props.chart_id);
    this.chart = chart;
    window.setTimeout(chart.update_svg_size, 1000);
  }

  handleGetMetricData(event) {
    event.preventDefault();
    if (this.getMetricIntervalHandle != null) {
      clearInterval(this.getMetricIntervalHandle);
    }
    this.getMetricData();
    this.getMetricIntervalHandle = setInterval(
      this.getMetricData,
      this.props.metric_sample_period * 1000
    );
  }

  handleStopMetricData(event) {
    event.preventDefault();
    if (this.getMetricIntervalHandle != null) {
      clearInterval(this.getMetricIntervalHandle);
    }
  }

  getMetricData() {
    fetch(this.metric_url, {
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
        const latest = objs;
        var obj_array = latest
          .map(s => s._source)
          .map(s => {
            var new_obj = {};
            const entries = Object.entries(s);
            for (const [key, value] of entries) {
              if (key === "date") {
                new_obj[key] = new Date(value);
              } else {
                //convert strings to numbers
                new_obj[key] = +value;
              }
            }
            return new_obj;
          });
        this.setState({ metric_data: obj_array });
        //this.chart.render(obj_array);
        //if a callback function was provided, call it
        if (this.props.getMetricCallback) {
          this.props.getMetricCallback(this.state.metric_data);
        }
      })
      .catch(e => console.log(e));
  }

  render() {
    if (this.chart && this.state.metric_data) {
      this.chart.render(this.state.metric_data);
    }

    return (
      <div>
        <form
          onSubmit={this.handleGetMetricData}
          style={{ display: "inline-block" }}
        >
          <input type="submit" value="GetData" />
        </form>
        <form
          onSubmit={this.handleStopMetricData}
          style={{ display: "inline-block" }}
        >
          <input type="submit" value="StopData" />
        </form>
        <div id={this.props.chart_id} className="chart-wrapper" />
      </div>
    );
  }
}

export default MetricChart;
