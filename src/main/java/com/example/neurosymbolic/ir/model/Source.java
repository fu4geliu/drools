package com.example.neurosymbolic.ir.model;

public class Source {

    private String section;
    private String text;
    private String document;
    private SourceSpan span;

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public SourceSpan getSpan() {
        return span;
    }

    public void setSpan(SourceSpan span) {
        this.span = span;
    }
}

