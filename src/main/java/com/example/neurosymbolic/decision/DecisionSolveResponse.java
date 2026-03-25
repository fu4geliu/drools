package com.example.neurosymbolic.decision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Response for Z3 reverse constraint solving.
 */
public class DecisionSolveResponse {

    private boolean satisfiable;
    private Map<String, Object> solvedInputs = new HashMap<>();
    private Map<String, Object> outputs = new HashMap<>();
    private List<String> firedRules = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public boolean isSatisfiable() {
        return satisfiable;
    }

    public void setSatisfiable(boolean satisfiable) {
        this.satisfiable = satisfiable;
    }

    public Map<String, Object> getSolvedInputs() {
        return solvedInputs;
    }

    public void setSolvedInputs(Map<String, Object> solvedInputs) {
        this.solvedInputs = solvedInputs != null ? solvedInputs : new HashMap<>();
    }

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

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
    }
}

