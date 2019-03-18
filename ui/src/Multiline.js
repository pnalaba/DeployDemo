import * as d3 from "d3-3";
import React, { Component } from "react";
//import "../styles/Multiline.css";
//import elastic_data from "../data/data.js";

export class Multiline extends Component {
  componentDidMount() {
    var chart = makeLineChart(
      this.props.xName,
      this.props.yObjs,
      this.props.axisLabels
    );
    chart.bind("#" + this.props.chart_id);
    chart.render(this.props.data);
    this.chart = chart;
  }

  redraw(data) {
    console.log("redraw :" + data);
    //this.chart.render(data);
  }

  render() {
    return <div id={this.props.chart_id} className="chart-wrapper" />;
  }
}

export function makeLineChart(xName, yObjs, axisLables, xAxisDateFormatStr) {
  var chartObj = {};
  var color = d3.scale.category10();
  //global variables
  chartObj.rendered = false;
  var focus = null;
  var xAxisElem = null;
  var yAxisElem = null;
  var legend = null;
  //grouping tasks that are not data dependent into init
  function init() {
    chartObj.xAxisLable = axisLables.xAxis;
    chartObj.yAxisLable = axisLables.yAxis;
    /*
     *      yObjsects format:
     *           {y1:{column:'',name:'name',color:'color'},y2}
     *                */

    //chartObj.data = dataset;
    chartObj.margin = { top: 15, right: 60, bottom: 120, left: 50 };
    chartObj.width = 650 - chartObj.margin.left - chartObj.margin.right;
    chartObj.height = 480 - chartObj.margin.top - chartObj.margin.bottom;

    // So we can pass the x and y as strings when creating the function
    chartObj.xFunct = function(d) {
      return d[xName];
    };

    // For each yObjs argument, create a yFunction
    function getYFn(column) {
      return function(d) {
        return d[column];
      };
    }

    //// Object instead of array
    chartObj.yFuncts = [];
    for (var y in yObjs) {
      yObjs[y].name = y;
      yObjs[y].yFunct = getYFn(yObjs[y].column); //Need this  list for the ymax function
      chartObj.yFuncts.push(yObjs[y].yFunct);
    }

    ////Formatter functions for the axes
    chartObj.formatAsNumber = d3.format(".0f");
    chartObj.formatAsDecimal = d3.format(".2f");
    chartObj.formatAsCurrency = d3.format("$.2f");
    chartObj.formatAsFloat = function(d) {
      if (d % 1 !== 0) {
        return d3.format(".2f")(d);
      } else {
        return d3.format(".0f")(d);
      }
    };

    chartObj.xFormatter =
      xAxisDateFormatStr == null
        ? chartObj.formatAsNumber
        : function(d) {
            return d3.time.format(xAxisDateFormatStr)(d);
          };
    chartObj.yFormatter = chartObj.formatAsFloat;

    chartObj.bisectYear = d3.bisector(chartObj.xFunct).left; //< Can be overridden in definition

    /*
      // Get the max of every yFunct
      chartObj.max = function(fn) {
        return d3.max(chartObj.data, fn);
      };
    */
    chartObj.yScale = d3.scale
      .linear()
      .range([chartObj.height, 0])
      //.domain([0, d3.max(chartObj.yFuncts.map(chartObj.max))]);
      .domain([0, 1.0]);

    chartObj.formatAsYear = d3.format("");

    chartObj.yAxis = d3.svg
      .axis()
      .scale(chartObj.yScale)
      .orient("left")
      .tickFormat(chartObj.yFormatter); //< Can be overridden in definition

    chartObj.xScale = d3.time.scale().range([0, chartObj.width]);
  }
  function getYScaleFn(yObj) {
    return function(d) {
      return chartObj.yScale(yObjs[yObj].yFunct(d));
    };
  }
  function mouseover_null_fn() {
    focus.style("display", null);
  }
  function mouseout_transition_fn() {
    focus
      .transition()
      .delay(700)
      .style("display", "none");
  }
  function mouseout_none_fn() {
    focus.style("display", "none");
  }
  function mousemove() {
    var x0 = chartObj.xScale.invert(d3.mouse(this)[0]),
      i = chartObj.bisectYear(chartObj.data, x0, 1),
      d0 = chartObj.data[i - 1],
      d1 = chartObj.data[i];
    try {
      var d = x0 - chartObj.xFunct(d0) > chartObj.xFunct(d1) - x0 ? d1 : d0;
    } catch (e) {
      return;
    }
    var minY = chartObj.height;
    for (var y in yObjs) {
      yObjs[y].tooltip.attr(
        "transform",
        "translate(" +
          chartObj.xScale(chartObj.xFunct(d)) +
          "," +
          chartObj.yScale(yObjs[y].yFunct(d)) +
          ")"
      );
      yObjs[y].tooltip
        .select("text")
        .text(chartObj.yFormatter(yObjs[y].yFunct(d)));
      minY = Math.min(minY, chartObj.yScale(yObjs[y].yFunct(d)));
    }

    focus
      .select(".focus.line")
      .attr(
        "transform",
        "translate(" + chartObj.xScale(chartObj.xFunct(d)) + ")"
      )
      .attr("y1", minY);
    focus
      .select(".focus.year")
      .style("font-weight", "bold")
      .text(xName + ": " + chartObj.xFormatter(chartObj.xFunct(d)));
  }

  init();

  //chartObj.svg;

  // Change chart size according to window size
  chartObj.update_svg_size = function() {
    chartObj.width =
      parseInt(chartObj.chartDiv.style("width"), 10) -
      (chartObj.margin.left + chartObj.margin.right);

    var style_height = parseInt(chartObj.chartDiv.style("height"), 10);

    chartObj.height =
      style_height - (chartObj.margin.top + chartObj.margin.bottom);

    /* Update the range of the scale with new width/height */
    chartObj.xScale.range([0, chartObj.width]);
    chartObj.yScale.range([chartObj.height, 0]);

    if (!chartObj.svg) {
      return false;
    }

    if (!chartObj.rendered) {
      return false;
    }

    /* Else Update the axis with the new scale */
    chartObj.svg
      .select(".x.axis")
      .attr("transform", "translate(0," + chartObj.height + ")")
      .call(chartObj.xAxis);

    chartObj.svg
      .select(".x.axis")
      .selectAll(".tick > text")
      .attr("transform", "translate(0," + chartObj.height + ")");

    chartObj.svg.select(".x.axis .label").attr("x", chartObj.width / 2);

    chartObj.svg
      .selectAll(".x > .tick > text")
      .attr("transform", "rotate(-65)")
      .style("text-anchor", "end");

    chartObj.svg.select(".y.axis").call(chartObj.yAxis);
    chartObj.svg.select(".y.axis .label").attr("x", -chartObj.height / 2);

    /* Force D3 to recalculate and update the line */
    for (var y in yObjs) {
      yObjs[y].path.attr("d", yObjs[y].line);
    }

    d3.selectAll(".focus.line").attr("y2", chartObj.height);

    chartObj.chartDiv
      .select("svg")
      .attr(
        "width",
        chartObj.width + (chartObj.margin.left + chartObj.margin.right)
      )
      .attr(
        "height",
        chartObj.height + (chartObj.margin.top + chartObj.margin.bottom)
      );

    chartObj.svg
      .select(".overlay")
      .attr("width", chartObj.width)
      .attr("height", chartObj.height);
    return chartObj;
  };

  chartObj.bind = function(selector) {
    chartObj.mainDiv = d3.select(selector);
    // Add all the divs to make it centered and responsive
    chartObj.mainDiv
      .append("div")
      .attr("class", "inner-wrapper")
      .append("div")
      .attr("class", "outer-box")
      .append("div")
      .attr("class", "inner-box");
    var chartSelector = selector + " .inner-box";
    chartObj.chartDiv = d3.select(chartSelector);

    chartObj.update_svg_size();
    //Create SVG element
    var size_obj = { width: chartObj.width, height: chartObj.height };
    console.log(
      "after update_svg_size chartObj size : " + JSON.stringify(size_obj)
    );
    chartObj.svg = chartObj.chartDiv
      .append("svg")
      .attr("class", "chart-area")
      .attr(
        "width",
        chartObj.width + (chartObj.margin.left + chartObj.margin.right)
      )
      .attr(
        "height",
        chartObj.height + (chartObj.margin.top + chartObj.margin.bottom)
      )
      .append("g")
      .attr(
        "transform",
        "translate(" + chartObj.margin.left + "," + chartObj.margin.top + ")"
      );
    for (var y in yObjs) {
      yObjs[y].path = chartObj.svg.append("path");
    }
    //Draw tooltips
    focus = chartObj.svg
      .append("g")
      .attr("class", "focus")
      .style("display", "none");

    // Create elements for Axes
    xAxisElem = chartObj.svg
      .append("g")
      .attr("class", "x axis")
      .attr("transform", "translate(0," + chartObj.height + ")");

    yAxisElem = chartObj.svg.append("g").attr("class", "y axis");
    //assuming here that yaxis doesn't change with time
    yAxisElem
      .call(chartObj.yAxis)
      .append("text")
      .attr("class", "label")
      .attr("transform", "rotate(-90)")
      .attr("y", -42)
      .attr("x", -chartObj.height / 2)
      .attr("dy", ".71em")
      .style("text-anchor", "middle")
      .text(chartObj.yAxisLable);
    //
    // Year label
    focus
      .append("text")
      .attr("class", "focus year")
      .attr("x", 9)
      .attr("y", 7);
    // Focus line
    focus
      .append("line")
      .attr("class", "focus line")
      .attr("y1", 0)
      .attr("y2", chartObj.height);

    //Draw legend
    //Also, assuming here that legend doesn't need to change with time
    legend = chartObj.mainDiv.append("div").attr("class", "legend");
    for (y in yObjs) {
      var series = legend.append("div");
      series
        .append("div")
        .attr("class", "series-marker")
        .style("background-color", color(y));
      series.append("p").text(y);
      yObjs[y].legend = series;
    }

    for (y in yObjs) {
      yObjs[y].tooltip = focus.append("g");
      yObjs[y].tooltip.append("circle").attr("r", 5);
      yObjs[y].tooltip
        .append("rect")
        .attr("x", 8)
        .attr("y", "-5")
        .attr("width", 22)
        .attr("height", "0.75em");
      yObjs[y].tooltip
        .append("text")
        .attr("x", 9)
        .attr("dy", ".35em");
    }

    // Overlay to capture hover
    chartObj.svg
      .append("rect")
      .attr("class", "overlay")
      .attr("width", chartObj.width)
      .attr("height", chartObj.height)
      .on("mouseover", mouseover_null_fn)
      .on("mouseout", mouseout_none_fn)
      .on("mousemove", mousemove);
    //
    //Create axis
    chartObj.xAxis = d3.svg
      .axis()
      .scale(chartObj.xScale)
      .orient("bottom")
      .tickFormat(chartObj.xFormatter); //< Can be overridden in definition

    d3.select(window).on("resize." + chartSelector, chartObj.update_svg_size);

    return chartObj;
  };

  // Render the chart
  chartObj.render = function(dataset) {
    //Create scale functions
    chartObj.data = dataset;
    chartObj.xScale.domain(d3.extent(chartObj.data, chartObj.xFunct)); //< Can be overridden in definition

    // Build line building functions
    for (var yObj in yObjs) {
      yObjs[yObj].line = d3.svg
        .line()
        .interpolate("cardinal")
        .x(function(d) {
          return chartObj.xScale(chartObj.xFunct(d));
        })
        .y(getYScaleFn(yObj));
    }

    // Draw Lines
    for (var y in yObjs) {
      yObjs[y].path
        .datum(chartObj.data)
        .attr("class", "line")
        .attr("d", yObjs[y].line)
        .style("stroke", color(y))
        .attr("data-series", y)
        .on("mouseover", mouseover_null_fn)
        .on("mouseout", mouseout_transition_fn)
        .on("mousemove", mousemove);
    }

    // Draw Axis
    var xAxisElem1 = xAxisElem.call(chartObj.xAxis);

    xAxisElem1
      .selectAll(".tick > text")
      .attr("transform", "rotate(-65)")
      .style("text-anchor", "end");

    chartObj.svg
      .select(".x.axis")
      .selectAll(".label")
      .data(["date"])
      .enter()
      .append("text")
      .attr("class", "label")
      .attr("x", chartObj.width / 2)
      .attr("y", 30)
      .style("text-anchor", "middle")
      .style("font-weight", "bold")
      .text(function(d) {
        return d;
      });

    chartObj.rendered = true;

    return chartObj;
  };
  return chartObj;
}
