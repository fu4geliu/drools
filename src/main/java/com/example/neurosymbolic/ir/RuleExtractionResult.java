package com.example.neurosymbolic.ir;

import com.example.neurosymbolic.ir.model.RuleDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of LLM-based IR extraction, including validation outcome.
 */
public class RuleExtractionResult {

    private boolean valid;
    private RuleDocument document;
    private List<String> errors = new ArrayList<>();
    private String rawJson;

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public RuleDocument getDocument() {
        return document;
    }

    public void setDocument(RuleDocument document) {
        this.document = document;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }
}

