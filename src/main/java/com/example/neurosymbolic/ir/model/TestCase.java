package com.example.neurosymbolic.ir.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class TestCase {

    private String name;
    private String description;

    @JsonProperty("input")
    private Map<String, Object> input;

    @JsonProperty("expected_output")
    private Map<String, Object> expectedOutput;

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

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Map<String, Object> getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(Map<String, Object> expectedOutput) {
        this.expectedOutput = expectedOutput;
    }
}

