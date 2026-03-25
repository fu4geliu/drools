package com.example.neurosymbolic.ir;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ir")
@Validated
public class RuleExtractionController {

    private final RuleExtractionService extractionService;

    public RuleExtractionController(RuleExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    /**
     * 使用 LLM 从监管文本中抽取 IR。
     *
     * 请求体示例：
     * {
     *   "title": "Basel III Market Risk",
     *   "domain": "Market Risk",
     *   "sourceDocument": "d457.pdf",
     *   "text": "... 监管条款原文 ...",
     *   "save": true
     * }
     */
    @PostMapping("/extract")
    public ResponseEntity<RuleExtractionResult> extract(@RequestBody RuleExtractionRequest request) {
        RuleExtractionResult result = extractionService.extract(request);
        HttpStatus status = result.isValid() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return new ResponseEntity<>(result, status);
    }
}

