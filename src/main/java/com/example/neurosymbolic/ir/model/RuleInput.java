package com.example.neurosymbolic.ir.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RuleInput {

    private String name;
    private String type;

    @JsonProperty("enum_ref")
    private String enumRef;

    private Double[] range;

    private String unit;

    private Boolean nullable;

    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEnumRef() {
        return enumRef;
    }

    public void setEnumRef(String enumRef) {
        this.enumRef = enumRef;
    }

    public Double[] getRange() {
        return range;
    }

    public void setRange(Double[] range) {
        this.range = range;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

