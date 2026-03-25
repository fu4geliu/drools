package com.example.neurosymbolic.ir.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Marker base type for computation, implemented as either steps-based or lookup-based.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ComputationSteps.class, name = "STEPS"),
        @JsonSubTypes.Type(value = ComputationLookup.class, name = "LOOKUP")
})
public abstract class Computation {
}

