package com.example.neurosymbolic.decision;

import com.example.neurosymbolic.drools.IrToDrlCompiler;
import com.example.neurosymbolic.drools.RuleContext;
import com.example.neurosymbolic.ir.RuleIrMapper;
import com.example.neurosymbolic.ir.RuleIrValidationResult;
import com.example.neurosymbolic.ir.RuleIrValidationService;
import com.example.neurosymbolic.ir.model.RuleDocument;
import com.example.neurosymbolic.ir.repository.RuleIrRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.kie.api.runtime.KieSession;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.internal.utils.KieHelper;
import org.kie.api.io.ResourceType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Iterator;

/**
 * End-to-end execution: IR lookup -> validation -> DRL compile -> Drools execute.
 */
@Service
public class DecisionExecutionService {

    private final RuleIrRepository repository;
    private final RuleIrValidationService validationService;
    private final RuleIrMapper mapper;
    private final IrToDrlCompiler compiler;

    public DecisionExecutionService(RuleIrRepository repository,
                                    RuleIrValidationService validationService,
                                    RuleIrMapper mapper) {
        this.repository = repository;
        this.validationService = validationService;
        this.mapper = mapper;
        this.compiler = new IrToDrlCompiler();
    }

    public DecisionResponse execute(String ruleSetId, Map<String, Object> inputs) {
        RuleDocument document = loadRuleDocument(ruleSetId);

        // Validate IR using the existing JSON Schema validator
        JsonNode node = mapper.toJsonNode(document);
        // Strip null-valued fields so optional schema properties are treated as absent rather than null.
        removeNullFields(node);
        RuleIrValidationResult validationResult = validationService.validate(node);
        if (!validationResult.isValid()) {
            throw new IllegalStateException("IR document is invalid for ruleSetId=" + ruleSetId +
                    ", errors=" + validationResult.getErrors());
        }

        // Compile to DRL
        String drl = compiler.compile(document, "com.example.neurosymbolic.rules");

        // Prepare Drools session
        KieHelper helper = new KieHelper();
        helper.addContent(drl, ResourceType.DRL);
        KieSession kieSession = helper.build().newKieSession();

        try {
            RuleContext context = new RuleContext(ruleSetId, inputs);

            // Collect fired rule names
            List<String> fired = new ArrayList<>();
            AgendaEventListener listener = new AgendaEventListener() {
                @Override
                public void afterMatchFired(AfterMatchFiredEvent event) {
                    fired.add(event.getMatch().getRule().getName());
                }

                // Unused events can be left with empty implementations
                @Override public void matchCreated(MatchCreatedEvent event) {}
                @Override public void matchCancelled(MatchCancelledEvent event) {}
                @Override public void beforeMatchFired(BeforeMatchFiredEvent event) {}
                @Override public void agendaGroupPopped(AgendaGroupPoppedEvent event) {}
                @Override public void agendaGroupPushed(AgendaGroupPushedEvent event) {}
                @Override public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {}
                @Override public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {}
                @Override public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {}
                @Override public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {}
            };
            kieSession.addEventListener(listener);

            kieSession.insert(context);
            kieSession.fireAllRules();

            DecisionResponse response = new DecisionResponse();
            response.setOutputs(context.getOutputs());
            response.setFiredRules(fired);
            return response;
        } finally {
            kieSession.dispose();
        }
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

