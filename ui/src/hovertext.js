var HoverText = {
  champion_challenger: {
    title: "Champion Challenger",
    description: [
      "Champion. The champion corresponds to the most effective model. For the initial execution of the champion challenger job step, there is no champion--only the first challenger and the corresponding list of challengers.",
      "Challenger. Challengers are compared against each other. The challenger that generates the best results then becomes the new champion."
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
