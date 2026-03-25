package com.example.neurosymbolic.ir;

import com.example.neurosymbolic.ir.model.RuleDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Helper to map between raw JsonNode and strongly-typed RuleDocument.
 * This keeps controller code simple and centralizes mapping logic.
 */
@Component
public class RuleIrMapper {

    private final ObjectMapper objectMapper;

    public RuleIrMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RuleDocument toRuleDocument(JsonNode node) {
        return objectMapper.convertValue(node, RuleDocument.class);
    }

    public JsonNode toJsonNode(RuleDocument document) {
        return objectMapper.valueToTree(document);
    }
}

