package com.example.drools.service;

import com.example.drools.model.Order;
import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drools 演示服务：加载规则、执行规则引擎。
 * 用于测试 Drools 是否正常工作，便于后续集成。
 */
@Service
public class DroolsDemoService {

    private static final Logger log = LoggerFactory.getLogger(DroolsDemoService.class);

    private final KieContainer kieContainer;

    public DroolsDemoService() {
        KieServices kieServices = KieServices.Factory.get();
        this.kieContainer = kieServices.getKieClasspathContainer();
        log.info("Drools KieContainer 加载完成");
    }

    /**
     * 根据订单金额执行折扣规则，返回处理后的订单。
     */
    public Order applyRules(double amount) {
        Order order = new Order(amount);
        KieSession kieSession = kieContainer.newKieSession("demoSession");
        try {
            kieSession.insert(order);
            int fired = kieSession.fireAllRules();
            log.debug("执行规则数: {}", fired);
        } finally {
            kieSession.dispose();
        }
        return order;
    }
}
