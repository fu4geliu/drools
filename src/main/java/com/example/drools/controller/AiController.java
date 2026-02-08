package com.example.drools.controller;

import com.example.drools.service.DroolsRuleReaderService;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiController {
    private final OpenAiChatClient chatClient;
    private final DroolsRuleReaderService droolsRuleReaderService;

    public AiController(OpenAiChatClient chatClient,
                        DroolsRuleReaderService droolsRuleReaderService) {
        this.chatClient = chatClient;
        this.droolsRuleReaderService = droolsRuleReaderService;
    }

    /**
     * 向 AI 提问。
     * GET /ai?question=xxx
     * GET /ai?question=xxx&withRules=true  -- 将 Drools 规则内容作为上下文一并发送给 AI
     */
    @GetMapping("/ai")
    public String askAi(@RequestParam String question,
                       @RequestParam(required = false, defaultValue = "false") boolean withRules) {
        String prompt = question;
        if (withRules) {
            String rulesContent = droolsRuleReaderService.getAllRulesContentSafe();
            prompt = buildPromptWithRules(question, rulesContent);
        }
        return chatClient.call(prompt);
    }

    private String buildPromptWithRules(String question, String rulesContent) {
        return """
                你是一个业务规则助手。以下是当前系统中的 Drools 规则定义，请基于这些规则回答用户问题。

                --- Drools 规则 ---
                %s
                --- 规则结束 ---

                用户问题：%s
                """.formatted(rulesContent, question);
    }
}