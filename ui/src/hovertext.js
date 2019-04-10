var HoverText = {
  deploydemo: {
    title: "DeployDemo",
    description: [
      "Second stage of a two-part demo. In part one, model(s) were trained.",
      "In stage two, models that have been trained are put into production and monitored",
      "The user can select which models to deploy, the method for orchestration and how to"
    ]
  },
  champion_challenger: {
    title: "Champion Challenger",
    description: [
      "Champion. The champion corresponds to the most effective model. For the initial execution of the champion challenger job step, there is no champion--only the first challenger and the corresponding list of challengers.",
      "Challenger. Challengers are compared against each other. The challenger that generates the best results then becomes the new champion."
    ]
  },
  canary: {
    title: "Canary Model",
    description: [
      "Canary Model is a model that can detect shifts in input data distribution. It compares input data to training data distribution.",
      "If there is a significant change in input data distribution, we can expect model performance to change signficantly. We need to retrain models if input data distribution has changed consistently."
    ]
  },

  ab_testing: {
    title: "AB Testing",
    description: [
      "AB testing is essentially an experiment where two or more variants of a page are shown to users at random, and statistical analysis is used to determine which variation performs better for a given conversion goal."
    ]
  },
  multiarmed_bandit: {
    title: "Multi Arm Bandit",
    description: [
      "Multi-armed bandit solution is a more complex version of A/B testing that uses machine learning algorithms to dynamically allocate traffic to variations that are performing well, while allocating less traffic to variations that are underperforming"
    ]
  }
};
export default HoverText;
