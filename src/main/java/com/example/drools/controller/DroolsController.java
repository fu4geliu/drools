package com.example.drools.controller;

import com.example.drools.model.Order;
import com.example.drools.service.DroolsDemoService;
import com.example.drools.service.DroolsRuleReaderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Drools 测试接口，便于通过 HTTP 验证规则引擎。
 */
@RestController
@RequestMapping("/drools")
public class DroolsController {

    private final DroolsDemoService droolsDemoService;
    private final DroolsRuleReaderService droolsRuleReaderService;

    public DroolsController(DroolsDemoService droolsDemoService,
                           DroolsRuleReaderService droolsRuleReaderService) {
        this.droolsDemoService = droolsDemoService;
        this.droolsRuleReaderService = droolsRuleReaderService;
    }

    /**
     * 测试折扣规则
     * GET /drools/demo?amount=150
     * 规则：>=500 打 20%，>=100 打 10%，<100 无折扣
     */
    @GetMapping("/demo")
    public Order demo(@RequestParam(defaultValue = "100") double amount) {
        return droolsDemoService.applyRules(amount);
    }

    /**
     * 获取所有 Drools 规则文件内容，供 AI 或外部系统读取。
     * GET /drools/rules
     */
    @GetMapping("/rules")
    public String rules() {
        return droolsRuleReaderService.getAllRulesContentSafe();
    }
}
