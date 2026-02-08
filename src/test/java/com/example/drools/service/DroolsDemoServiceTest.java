package com.example.drools.service;

import com.example.drools.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drools 规则引擎测试：验证规则加载与执行。
 */
@SpringBootTest
class DroolsDemoServiceTest {

    @Autowired
    DroolsDemoService droolsDemoService;

    @Test
    void 小额订单无折扣() {
        Order order = droolsDemoService.applyRules(50);
        assertThat(order.getDiscount()).isEqualTo(0);
        assertThat(order.getLevel()).isEqualTo("NORMAL");
        assertThat(order.getFinalAmount()).isEqualTo(50);
    }

    @Test
    void 普通大额订单10折扣() {
        Order order = droolsDemoService.applyRules(150);
        assertThat(order.getDiscount()).isEqualTo(10);
        assertThat(order.getLevel()).isEqualTo("STANDARD");
        assertThat(order.getFinalAmount()).isEqualTo(135);  // 150 * 0.9
    }

    @Test
    void VIP大额订单20折扣() {
        Order order = droolsDemoService.applyRules(600);
        assertThat(order.getDiscount()).isEqualTo(20);
        assertThat(order.getLevel()).isEqualTo("VIP");
        assertThat(order.getFinalAmount()).isEqualTo(480);  // 600 * 0.8
    }
}
