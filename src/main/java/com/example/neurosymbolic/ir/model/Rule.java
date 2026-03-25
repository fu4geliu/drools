package com.example.neurosymbolic.ir.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Rule {

    @JsonProperty("rule_id")
    private String ruleId;

    @JsonProperty("rule_name")
    private String ruleName;

    private String category;

    private Source source;

    private String applicability;

    private List<Condition> conditions;

    private List<Condition> exceptions;

    private List<RuleInput> inputs;

    private Computation computation;

    private List<RuleOutput> outputs;

    @JsonProperty("test_cases")
    private List<TestCase> testCases;

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public String getApplicability() {
        return applicability;
    }

    public void setApplicability(String applicability) {
        this.applicability = applicability;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public List<Condition> getExceptions() {
        return exceptions;
    }

    public void setExceptions(List<Condition> exceptions) {
        this.exceptions = exceptions;
    }

    public List<RuleInput> getInputs() {
        return inputs;
    }

    public void setInputs(List<RuleInput> inputs) {
        this.inputs = inputs;
    }

    public Computation getComputation() {
        return computation;
    }

    public void setComputation(Computation computation) {
        this.computation = computation;
    }

    public List<RuleOutput> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<RuleOutput> outputs) {
        this.outputs = outputs;
    }

    public List<TestCase> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<TestCase> testCases) {
        this.testCases = testCases;
    }
}

