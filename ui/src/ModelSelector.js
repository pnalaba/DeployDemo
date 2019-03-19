import React from "react";
import axios from "axios";
class ModelSelector extends React.Component {
  constructor(props) {
    super(props);
    this.handleModelChange = this.handleModelChange.bind(this);
    this.state = {
      models: [],
      model_options: []
    };

    var classHandle = this;
    axios
      .get(this.props.server + "/models")
      .then(response => {
        classHandle.setState({ model_options: response.data });
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
    var url = this.props.server + "/selectModels/" + model_str;
    console.log("calling url : " + url);
    axios.get(url).catch(e => console.log(e));
    event.preventDefault();
  }

  render() {
    return (
      <form onSubmit={this.handleModelSubmit}>
        <p>
          <b>Select multiple models to deploy... </b>
          <label>
            {" "}
            Avaliable models in repository:{" "}
            <select
              multiple={true}
              value={this.state.models}
              onChange={this.handleModelChange}
            >
              {this.state.model_options.map(item => (
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

export default ModelSelector;