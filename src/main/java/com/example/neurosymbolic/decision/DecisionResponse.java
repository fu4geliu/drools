package com.example.neurosymbolic.decision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for decision execution.
 */
public class DecisionResponse {

    /**
     * Output values keyed by output name.
     */
    private Map<String, Object> outputs = new HashMap<>();

    /**
     * Names of Drools rules that fired during evaluation.
     */
    private List<String> firedRules = new ArrayList<>();

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs != null ? outputs : new HashMap<>();
    }

    public List<String> getFiredRules() {
        return firedRules;
    }

    public void setFiredRules(List<String> firedRules) {
        this.firedRules = firedRules != null ? firedRules : new ArrayList<>();
    }
}

