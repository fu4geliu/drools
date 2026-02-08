package com.example.drools.model;

/**
 * 订单事实对象，用于 Drools 规则测试。
 * 规则将根据 amount 计算 discount。
 */
public class Order {
    private double amount;
    private double discount;  // 百分比，如 10 表示 10%
    private String level;     // 规则可能设置的等级标识

    public Order(double amount) {
        this.amount = amount;
        this.discount = 0;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public double getFinalAmount() {
        return amount * (1 - discount / 100);
    }

    @Override
    public String toString() {
        return String.format("Order{amount=%.2f, discount=%.1f%%, level=%s, final=%.2f}",
                amount, discount, level, getFinalAmount());
    }
}
