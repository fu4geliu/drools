package com.example.neurosymbolic.ir.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Strongly-typed representation of the rule IR document,
 * aligned with rule-ir.schema.json.
 */
public class RuleDocument {

    private Metadata metadata;

    private List<Rule> rules;

    /**
     * Map from enum domain name to entries (e.g. CreditQuality -> { AAA: 0.5, ... }).
     */
    private Map<String, Map<String, Object>> enums;

    @JsonProperty("functions")
    private List<FunctionDef> functions;

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public Map<String, Map<String, Object>> getEnums() {
        return enums;
    }

    public void setEnums(Map<String, Map<String, Object>> enums) {
        this.enums = enums;
    }

    public List<FunctionDef> getFunctions() {
        return functions;
    }

    public void setFunctions(List<FunctionDef> functions) {
        this.functions = functions;
    }
}

