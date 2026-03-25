package com.example.neurosymbolic.decision;

import java.util.Map;

/**
 * Request DTO for decision execution.
 */
public class DecisionRequest {

    /**
     * Identifier of the IR document / rule set to use.
     * Currently对应保存时使用的 metadata.title。
     */
    private String ruleSetId;

    /**
     * Input values keyed by input name.
     */
    private Map<String, Object> inputs;

    public String getRuleSetId() {
        return ruleSetId;
    }

    public void setRuleSetId(String ruleSetId) {
        this.ruleSetId = ruleSetId;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }
}

