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

    this.handleModelSubmit = this.handleModelSubmit.bind(this);
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
    event.preventDefault();
    var model_str = this.state.models.join(",");
    var url = this.props.server + "/selectModels/" + model_str;
    axios.get(url).catch(e => console.log(e));
  }

  render() {
    return (
      <form onSubmit={this.handleModelSubmit}>
        <p>
          <b>Pre-trained models ready for deployment : </b>
          <label>
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
        </p>
      </form>
    );
  }
}

export default ModelSelector;
