package com.example.neurosymbolic.ir.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FunctionDef {

    private String name;

    private String description;

    private List<RuleInput> inputs;

    private RuleOutput output;

    @JsonProperty("implementation")
    private String implementation;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<RuleInput> getInputs() {
        return inputs;
    }

    public void setInputs(List<RuleInput> inputs) {
        this.inputs = inputs;
    }

    public RuleOutput getOutput() {
        return output;
    }

    public void setOutput(RuleOutput output) {
        this.output = output;
    }

    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }
}

