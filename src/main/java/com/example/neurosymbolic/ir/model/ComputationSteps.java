package com.example.neurosymbolic.ir.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ComputationSteps extends Computation {

    private String type;

    private List<ComputationStep> steps;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<ComputationStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ComputationStep> steps) {
        this.steps = steps;
    }

    public static class ComputationStep {

        @JsonProperty("step_id")
        private String stepId;

        private String description;

        private String formula;

        public String getStepId() {
            return stepId;
        }

        public void setStepId(String stepId) {
            this.stepId = stepId;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getFormula() {
            return formula;
        }

        public void setFormula(String formula) {
            this.formula = formula;
        }
    }
}

