package com.example.neurosymbolic.ir;

import com.example.neurosymbolic.drools.DrlPersisterService;
import com.example.neurosymbolic.ir.model.Metadata;
import com.example.neurosymbolic.ir.model.RuleDocument;
import com.example.neurosymbolic.ir.repository.RuleIrRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uses an LLM (via Spring AI OpenAiChatClient) to extract a RuleDocument IR from raw regulatory text.
 */
@Service
public class RuleExtractionService {

    private static final Logger log = Logger.getLogger(RuleExtractionService.class.getName());

    private final OpenAiChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final RuleIrValidationService validationService;
    private final RuleIrMapper mapper;
    private final RuleIrRepository repository;
    private final DrlPersisterService drlPersister;

    public RuleExtractionService(OpenAiChatClient chatClient,
                                 ObjectMapper objectMapper,
                                 RuleIrValidationService validationService,
                                 RuleIrMapper mapper,
                                 RuleIrRepository repository,
                                 DrlPersisterService drlPersister) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.validationService = validationService;
        this.mapper = mapper;
        this.repository = repository;
        this.drlPersister = drlPersister;
    }

    public RuleExtractionResult extract(RuleExtractionRequest request) {
        String promptText = buildPrompt(request);
        Prompt prompt = new Prompt(new UserMessage(promptText));

        String content = chatClient.call(prompt).getResult().getOutput().getContent();

        RuleExtractionResult result = new RuleExtractionResult();
        result.setRawJson(content);

        try {
            String json = stripJsonFromMarkdown(content);
            JsonNode node = objectMapper.readTree(json);

            // Ensure metadata is populated / overridden from request for auditability
            if (node.has("metadata") && node.get("metadata").isObject()) {
                ObjectNode metadataNode = (ObjectNode) node.get("metadata");
                if (request.getTitle() != null) metadataNode.put("title", request.getTitle());
                if (request.getDomain() != null) metadataNode.put("domain", request.getDomain());
                if (request.getSourceDocument() != null) metadataNode.put("source_document", request.getSourceDocument());
                if (!metadataNode.has("version") || metadataNode.get("version").isNull()) metadataNode.put("version", "1.0");
                String today = LocalDate.now().toString();
                if (!metadataNode.has("created_at") || metadataNode.get("created_at").isNull()) metadataNode.put("created_at", today);
                if (!metadataNode.has("updated_at") || metadataNode.get("updated_at").isNull()) metadataNode.put("updated_at", today);
                if (!metadataNode.has("tags")) metadataNode.putArray("tags");
                if (!metadataNode.has("notes") || metadataNode.get("notes").isNull()) metadataNode.put("notes", "");
            }

            RuleIrValidationResult validationResult = validationService.validate(node);
            result.setValid(validationResult.isValid());
            if (!validationResult.isValid()) {
                result.setErrors(validationResult.getErrors());
                return result;
            }

            RuleDocument document = mapper.toRuleDocument(node);
            if (document.getMetadata() == null) {
                Metadata metadata = new Metadata();
                metadata.setTitle(request.getTitle());
                metadata.setDomain(request.getDomain());
                metadata.setSourceDocument(request.getSourceDocument());
                document.setMetadata(metadata);
            }
            if (document.getMetadata().getCreatedAt() == null) {
                document.getMetadata().setCreatedAt(LocalDate.now());
            }

            result.setDocument(document);

            if (request.isSave()) {
                String id = document.getMetadata().getTitle();
                repository.save(id, document);
                try {
                    Path drlPath = drlPersister.persist(id, document);
                    log.info("DRL written to " + drlPath.toAbsolutePath());
                } catch (IOException e) {
                    log.log(Level.WARNING, "Failed to write DRL to disk for ruleSetId=" + id, e);
                }
            }

            return result;
        } catch (IOException e) {
            result.setValid(false);
            result.getErrors().add("Failed to parse LLM JSON: " + e.getMessage());
            return result;
        }
    }

    private static String stripJsonFromMarkdown(String content) {
        if (content == null) return "";
        String s = content.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf("\n");
            if (start > 0) s = s.substring(start + 1);
            int end = s.lastIndexOf("```");
            if (end > 0) s = s.substring(0, end);
        }
        return s.trim();
    }

    private String buildPrompt(RuleExtractionRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个金融监管规则抽取助手，需要将监管文本转换为结构化的规则中间表示 (IR)。")
                .append("请严格按照给定的 JSON 结构输出，不要添加任何解释性文字或 Markdown，仅输出 JSON。\n\n");

        sb.append("元数据（metadata）：\n");
        sb.append("- title: ").append(request.getTitle()).append("\n");
        sb.append("- domain: ").append(request.getDomain()).append("\n");
        sb.append("- source_document: ").append(request.getSourceDocument()).append("\n\n");

        sb.append("目标 JSON 顶层结构如下（字段名必须一致）：\n");
        sb.append("{\n");
        sb.append("  \"metadata\": { \"title\": string, \"domain\": string, \"source_document\": string, \"version\": \"1.0\", \"created_at\": string, \"updated_at\": string, \"tags\": [], \"notes\": string },\n");
        sb.append("  \"rules\": [ { \"rule_id\": string, \"rule_name\": string, \"category\": string, \"source\": { \"section\": string, \"text\": string, \"document\": string, \"span\": { \"page\": 1, \"start_offset\": 0, \"end_offset\": number } }, \"applicability\": string, \"exceptions\": [], \"inputs\": [...], \"conditions\": [...], \"computation\": { \"type\": \"STEPS\", \"steps\": [ { \"step_id\": string, \"description\": string, \"formula\": string } ] } 或 { \"type\": \"LOOKUP\", \"lookup_ref\": string }, \"outputs\": [...], \"test_cases\": [ { \"name\": string, \"description\": string, \"input\": {}, \"expected_output\": {} } ] } ],\n");
        sb.append("  \"enums\": { },\n");
        sb.append("  \"functions\": [ ]\n");
        sb.append("}\n\n");

        sb.append("监管原文如下，请从中抽取 1-3 条具有代表性的规则，填充到 rules 数组中：\n");
        sb.append(request.getText() != null ? request.getText() : "");
        sb.append("\n\n");

        sb.append("要求：\n");
        sb.append("1. metadata 必须包含 title, domain, source_document, version, created_at, updated_at, tags, notes。\n");
        sb.append("2. 每条规则必须包含 rule_id, rule_name, category, source (含 section, text, document, span), applicability, exceptions, inputs, conditions, computation (type 为 STEPS 或 LOOKUP), outputs, test_cases。\n");
        sb.append("3. conditions 数组中每个元素只能是 { \"expression\": \"可执行表达式\", \"description\": \"说明\" }，不要使用 field/operator/value 等字段。例如：{ \"expression\": \"amount > 10\", \"description\": \"金额大于10\" }。\n");
        sb.append("4. inputs/outputs 的 type 只能使用：DECIMAL, INTEGER, STRING, BOOLEAN, DATE, ENUM。\n");
        sb.append("5. test_cases 每条约定 input 与 expected_output 对象。\n");
        sb.append("6. 严格输出合法 JSON，不要用 ```json 包裹，不要注释或多余文本。\n");

        return sb.toString();
    }
}
