package com.example.neurosymbolic.drools;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic fact passed to Drools rules, carrying inputs and outputs
 * for a particular IR rule evaluation.
 */
public class RuleContext {

    private String ruleId;
    private Map<String, Object> inputs = new HashMap<>();
    private Map<String, Object> outputs = new HashMap<>();

    public RuleContext() {
    }

    public RuleContext(String ruleId, Map<String, Object> inputs) {
        this.ruleId = ruleId;
        if (inputs != null) {
            this.inputs.putAll(inputs);
        }
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs != null ? inputs : new HashMap<>();
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Object> outputs) {
        this.outputs = outputs != null ? outputs : new HashMap<>();
    }
}

