package com.example.neurosymbolic.decision;

import com.example.neurosymbolic.ir.RuleIrValidationResult;
import com.example.neurosymbolic.ir.RuleIrMapper;
import com.example.neurosymbolic.ir.RuleIrValidationService;
import com.example.neurosymbolic.ir.model.RuleDocument;
import com.example.neurosymbolic.ir.model.Rule;
import com.example.neurosymbolic.ir.repository.RuleIrRepository;
import com.example.neurosymbolic.z3.Z3RuleSmtService;
import com.example.neurosymbolic.z3.Z3RuleSmtService.Z3SolveResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DecisionZ3SolveService {

    private final RuleIrRepository repository;
    private final RuleIrValidationService validationService;
    private final RuleIrMapper mapper;
    private final Z3RuleSmtService z3RuleSmtService;
    private final DecisionExecutionService executionService;

    public DecisionZ3SolveService(RuleIrRepository repository,
                                  RuleIrValidationService validationService,
                                  RuleIrMapper mapper,
                                  Z3RuleSmtService z3RuleSmtService,
                                  DecisionExecutionService executionService) {
        this.repository = repository;
        this.validationService = validationService;
        this.mapper = mapper;
        this.z3RuleSmtService = z3RuleSmtService;
        this.executionService = executionService;
    }

    public DecisionSolveResponse solve(DecisionSolveRequest request) {
        DecisionSolveResponse response = new DecisionSolveResponse();

        if (request == null) {
            response.setSatisfiable(false);
            response.setErrors(List.of("Request body is empty."));
            return response;
        }

        String ruleSetId = request.getRuleSetId();
        Map<String, Object> inputs = request.getInputs();

        if (ruleSetId == null || ruleSetId.isBlank()) {
            response.setSatisfiable(false);
            response.setErrors(List.of("ruleSetId is required."));
            return response;
        }

        RuleDocument document = loadRuleDocument(ruleSetId);

        // Validate IR using the same JSON Schema validator
        JsonNode node = mapper.toJsonNode(document);
        removeNullFields(node);
        RuleIrValidationResult validationResult = validationService.validate(node);
        if (!validationResult.isValid()) {
            response.setSatisfiable(false);
            response.setErrors(validationResult.getErrors());
            return response;
        }

        List<Rule> allRules = document.getRules() != null ? document.getRules() : List.of();
        if (allRules.isEmpty()) {
            response.setSatisfiable(false);
            response.setErrors(List.of("No rules found in the IR document."));
            return response;
        }

        String mode = request.getMode() == null ? "ANY_RULES" : request.getMode().trim().toUpperCase();
        List<Rule> selectedRules = new ArrayList<>(allRules);

        if ("TARGET_RULE".equals(mode)) {
            String targetRuleId = request.getTargetRuleId();
            if (targetRuleId == null || targetRuleId.isBlank()) {
                response.setSatisfiable(false);
                response.setErrors(List.of("targetRuleId is required when mode == TARGET_RULE."));
                return response;
            }
            selectedRules = allRules.stream()
                    .filter(r -> targetRuleId.equals(r.getRuleId()))
                    .toList();
        }

        if (selectedRules.isEmpty()) {
            response.setSatisfiable(false);
            response.setErrors(List.of("No matching rules encoded for mode=" + mode));
            return response;
        }

        // Encode combined rule firing constraints
        String z3Mode = "ALL_RULES".equals(mode) ? "ALL" : "ANY";
        String objectiveVar = request.getObjectiveVar();
        String objectiveMode = request.getObjectiveMode();
        Z3SolveResult z3Result = z3RuleSmtService.solveInputs(selectedRules, inputs, z3Mode, objectiveVar, objectiveMode);

        response.setSatisfiable(z3Result.isSatisfiable());
        response.setSolvedInputs(z3Result.getSolvedInputs());
        response.setErrors(z3Result.getErrors());

        if (!response.isSatisfiable()) {
            return response;
        }

        if (request.isReturnOutputs()) {
            Map<String, Object> merged = new HashMap<>();
            if (inputs != null) {
                merged.putAll(inputs);
            }
            merged.putAll(response.getSolvedInputs());

            DecisionResponse execution = executionService.execute(ruleSetId, merged);
            response.setOutputs(execution.getOutputs());
            response.setFiredRules(execution.getFiredRules());
        }

        return response;
    }

    private RuleDocument loadRuleDocument(String ruleSetId) {
        Optional<RuleDocument> optional = repository.findById(ruleSetId);
        return optional.orElseThrow(() ->
                new IllegalArgumentException("No IR document found for ruleSetId=" + ruleSetId));
    }

    /**
     * Recursively remove all fields whose value is JSON null.
     */
    private void removeNullFields(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            List<String> toRemove = new ArrayList<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isNull()) {
                    toRemove.add(entry.getKey());
                } else {
                    removeNullFields(value);
                }
            }
            toRemove.forEach(obj::remove);
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                removeNullFields(child);
            }
        }
    }
}

