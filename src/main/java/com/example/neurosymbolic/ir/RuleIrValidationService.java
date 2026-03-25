package com.example.neurosymbolic.ir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RuleIrValidationService {

    private final ObjectMapper objectMapper;
    private final JsonSchema schema;

    public RuleIrValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema();
    }

    private JsonSchema loadSchema() {
        try {
            ClassPathResource resource = new ClassPathResource("schema/rule-ir.schema.json");
            try (InputStream is = resource.getInputStream()) {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                return factory.getSchema(is);
            }
        } catch (IOException e) {
            // JsonSchemaException in json-schema-validator 1.x does not support (String, Throwable) constructor.
            // Wrap the original IOException as the cause.
            throw new JsonSchemaException(e);
        }
    }

    public RuleIrValidationResult validate(JsonNode irNode) {
        Set<ValidationMessage> messages = schema.validate(irNode);
        boolean valid = messages.isEmpty();

        RuleIrValidationResult result = new RuleIrValidationResult();
        result.setValid(valid);
        if (!valid) {
            result.setErrors(messages.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList()));
        }
        return result;
    }

}

