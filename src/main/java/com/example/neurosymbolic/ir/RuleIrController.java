package com.example.neurosymbolic.ir;

import com.example.neurosymbolic.drools.DrlPersisterService;
import com.example.neurosymbolic.ir.model.RuleDocument;
import com.example.neurosymbolic.ir.repository.RuleIrRepository;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
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
public class RuleIrController {

    private static final Logger log = Logger.getLogger(RuleIrController.class.getName());

    private final RuleIrValidationService validationService;
    private final RuleIrMapper mapper;
    private final RuleIrRepository repository;
    private final DrlPersisterService drlPersister;

    public RuleIrController(RuleIrValidationService validationService,
                            RuleIrMapper mapper,
                            RuleIrRepository repository,
                            DrlPersisterService drlPersister) {
        this.validationService = validationService;
        this.mapper = mapper;
        this.repository = repository;
        this.drlPersister = drlPersister;
    }

    /**
     * 校验传入的 IR JSON 是否符合 rule-ir.schema.json。
     *
     * 请求体：任意 JSON，期望是 IR 文档。
     * 响应体：{"valid": true/false, "errors": [...]}。
     */
    @PostMapping("/validate")
    public ResponseEntity<RuleIrValidationResult> validate(@RequestBody JsonNode irNode) {
        RuleIrValidationResult result = validationService.validate(irNode);
        HttpStatus status = result.isValid() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return new ResponseEntity<>(result, status);
    }

    /**
     * 校验并保存 IR 文档。
     *
     * 路径参数 id 代表规则文档的业务标识（例如 "basel-iii-mar22"）。
     * 仅当通过 JSON Schema 校验时才会保存到仓库。
     */
    @PostMapping
    public ResponseEntity<?> save(@RequestBody JsonNode irNode) {
        // 先做 JSON Schema 校验
        RuleIrValidationResult validationResult = validationService.validate(irNode);
        if (!validationResult.isValid()) {
            return ResponseEntity.badRequest().body(validationResult);
        }

        // 映射为强类型模型
        RuleDocument document = mapper.toRuleDocument(irNode);

        // 使用 metadata.title 作为一个简单的 ID（后续可扩展为显式 ID 体系）
        String id = document.getMetadata() != null ? document.getMetadata().getTitle() : "default";
        repository.save(id, document);
        try {
            Path drlPath = drlPersister.persist(id, document);
            log.info("DRL written to " + drlPath.toAbsolutePath());
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to write DRL to disk for ruleSetId=" + id, e);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }
}

