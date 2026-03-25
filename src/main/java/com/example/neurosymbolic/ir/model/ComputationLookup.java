package com.example.neurosymbolic.ir.model;

public class ComputationLookup extends Computation {

    private String type;

    private String lookup_ref;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLookup_ref() {
        return lookup_ref;
    }

    public void setLookup_ref(String lookup_ref) {
        this.lookup_ref = lookup_ref;
    }
}

