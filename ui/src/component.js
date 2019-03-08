import * as React from "react";
import * as d3 from "d3";
import { drawChart } from "./drawChart";
export class MultiLineChart extends React.Component {
  constructor(props) {
    super(props);
  }
  componentDidMount() {
    // Hardcoded data
    drawChart(
      this.props.data,
      this.props.legendAxisY,
      this.props.height,
      this.props.dateFormat
    );
  }
  render() {
    return React.createElement("svg", { id: "chart" });
  }
}
