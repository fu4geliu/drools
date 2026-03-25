package com.example.neurosymbolic.ir;

/**
 * Request payload for LLM-based rule IR extraction.
 */
public class RuleExtractionRequest {

    /**
     * Rule set identifier / title, will be used as metadata.title and repository key.
     */
    private String title;

    /**
     * Business / regulatory domain, e.g. "Market Risk".
     */
    private String domain;

    /**
     * Source document identifier, e.g. "d457.pdf" or URL.
     */
    private String sourceDocument;

    /**
     * Raw regulatory text to extract rules from.
     */
    private String text;

    /**
     * Whether to persist the extracted IR into the repository.
     */
    private boolean save;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSourceDocument() {
        return sourceDocument;
    }

    public void setSourceDocument(String sourceDocument) {
        this.sourceDocument = sourceDocument;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isSave() {
        return save;
    }

    public void setSave(boolean save) {
        this.save = save;
    }
}

