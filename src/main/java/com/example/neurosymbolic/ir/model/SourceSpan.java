package com.example.neurosymbolic.ir.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SourceSpan {

    private Integer page;

    @JsonProperty("start_offset")
    private Integer startOffset;

    @JsonProperty("end_offset")
    private Integer endOffset;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(Integer startOffset) {
        this.startOffset = startOffset;
    }

    public Integer getEndOffset() {
        return endOffset;
    }

    public void setEndOffset(Integer endOffset) {
        this.endOffset = endOffset;
    }
}

