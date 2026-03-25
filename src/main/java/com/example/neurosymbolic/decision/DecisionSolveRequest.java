package com.example.neurosymbolic.decision;

import java.util.Map;

/**
 * Request payload for reverse constraint solving:
 * find missing input values (unknowns) so that one/more rules can fire.
 *
 * Partial assignment semantics:
 * - if {@code inputs} is missing a key or the value is null => treated as unknown and solved by Z3.
 * - if value is non-null => treated as known and fixed by equality constraint.
 */
public class DecisionSolveRequest {

    /**
     * Identifier of the IR document / rule set to use.
     * Currently corresponds to metadata.title used as repository key.
     */
    private String ruleSetId;

    /**
     * Partial input assignment keyed by input name.
     */
    private Map<String, Object> inputs;

    /**
     * Solve mode:
     * - ANY_RULES: at least one rule fires
     * - ALL_RULES: all selected rules fire
     * - TARGET_RULE: only targetRuleId rule is encoded
     */
    private String mode = "ANY_RULES";

    /**
     * Only used when mode == TARGET_RULE.
     */
    private String targetRuleId;

    /**
     * Whether to run Drools after Z3 and return computed outputs + fired rule names.
     */
    private boolean returnOutputs = true;

    /**
     * Optional objective variable name to optimize during Z3 solving.
     * If null => no optimization (SAT only).
     */
    private String objectiveVar;

    /**
     * Objective direction: "MAX" or "MIN".
     * Only used when {@link #objectiveVar} is non-null.
     */
    private String objectiveMode = "MAX";

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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTargetRuleId() {
        return targetRuleId;
    }

    public void setTargetRuleId(String targetRuleId) {
        this.targetRuleId = targetRuleId;
    }

    public boolean isReturnOutputs() {
        return returnOutputs;
    }

    public void setReturnOutputs(boolean returnOutputs) {
        this.returnOutputs = returnOutputs;
    }

    public String getObjectiveVar() {
        return objectiveVar;
    }

    public void setObjectiveVar(String objectiveVar) {
        this.objectiveVar = objectiveVar;
    }

    public String getObjectiveMode() {
        return objectiveMode;
    }

    public void setObjectiveMode(String objectiveMode) {
        this.objectiveMode = objectiveMode;
    }
}

