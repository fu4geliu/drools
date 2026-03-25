# Neuro-Symbolic

面向金融合规的「监管文本 -> 规则中间表示(IR) -> 可执行规则(Drools DRL) -> 可验证/可反推(约束求解,Z3)」闭环。

## 技术栈

- Java 17 + Spring Boot 3.2
- Drools 8.42（IR -> DRL 编译、执行输出）
- Spring AI（LLM 抽取 IR）
- JSON Schema（`rule-ir.schema.json` 校验）
- Z3（逆向求解缺失输入：部分赋值 -> 求使规则触发的未知量）

## 项目结构（高层）

- `com.example.neurosymbolic.ir`：IR 模型、IR 解析/校验
- `com.example.neurosymbolic.drools`：`IrToDrlCompiler`（IR -> DRL）、`DrlPersisterService`（落盘审计）
- `com.example.neurosymbolic.decision`：`/decision/execute`（Drools 正向推理）与 `/decision/solve`（Z3 反向求解）
- `com.example.neurosymbolic.z3`：`Z3RuleSmtService`（把条件表达式编码成 Z3 约束并求模型）

## 启动方式

1. 配置 `src/main/resources/application.yml` 中的 Spring AI 配置（当前为 OpenAI-compatible 接口）。
   - 注意：`api-key` 不要提交到仓库，建议改用环境变量/secret 管理。
2. 启动：
   - `mvn spring-boot:run`

## REST API

> IR 仓库当前为内存实现（`InMemoryRuleIrRepository`），**应用重启后已保存的 `ruleSetId` 会丢失**。每次测试需要先 `POST /ir` 保存 IR。

### 1. LLM 抽取 IR

`POST /ir/extract`

请求体（示例字段）：
- `title`：规则集标题（保存时用作 `ruleSetId`）
- `domain`
- `sourceDocument`
- `text`：监管原文
- `save`：是否保存到仓库并落盘 DRL

### 2. 校验 IR（JSON Schema）

`POST /ir/validate`

- 请求体：任意 JSON（期望是 IR 文档）
- 响应：`valid` + `errors`

### 3. 保存 IR + 落盘 DRL

`POST /ir`

- 请求体：IR JSON
- 校验通过后以 `metadata.title` 作为 `ruleSetId` 保存到仓库
- 同时生成 `target/generated-rules/*.drl`（仅用于查看/审计，`/decision/execute` 仍使用内存编译 DRL）

### 4. 正向执行（Drools）

`POST /decision/execute`

请求体：
```json
{
  "ruleSetId": "Demo RuleSet 1",
  "inputs": { "amount": 15 }
}
```

响应：
```json
{
  "outputs": { "discounted_amount": 12.0 },
  "firedRules": [ "R001 - ... " ]
}
```

### 5. 逆向求解（Z3：部分输入求缺失值）

`POST /decision/solve`

请求体：
```json
{
  "ruleSetId": "TestLTV_1",
  "inputs": {
    "property_value": 5000000,
    "first_time_buyer": true,
    "has_special_approval": false,
    "loan_amount": null
  },
  "mode": "ANY_RULES",
  "objectiveVar": "loan_amount",
  "objectiveMode": "MAX",
  "returnOutputs": false
}
```

语义：
- `inputs` 中缺失 key 或值为 `null`：该变量视为“未知”，由 Z3 求解
- `exceptions`：编码为 `NOT(exceptions...)`（与 Drools “例外不触发”语义一致）
- `mode`：
  - `ANY_RULES`：选中的规则中至少一条触发（OR）
  - `ALL_RULES`：所有选中的规则都触发（AND）
  - `TARGET_RULE`：仅对 `targetRuleId` 对应的那条规则触发
- `objectiveVar/objectiveMode`（可选）：
  - 当前支持 `MAX`：最大化某个输入变量（例如 `loan_amount`）
- `returnOutputs=true`：求解出缺失输入后，再调用现有 `/decision/execute` 跑一次 Drools，返回 `outputs` 与 `firedRules`

响应体：
```json
{
  "satisfiable": true,
  "solvedInputs": { "loan_amount": 4000000.0 },
  "outputs": {},
  "firedRules": [],
  "errors": []
}
```

## Z3 条件表达式支持范围（当前实现）

`conditions.expression` / `exceptions.expression` 当前解析支持的子集：
- 比较：`> >= < <= ==`（也支持单等号 `=` 作为 `==`）
- 逻辑：`and/or`（关键字）、`&&/||`
- 括号
- 算术：`+ - * /`
- 标识符：必须与 IR 的 `inputs[].name` 匹配
- 布尔字面量：`true/false`

若表达式超出该子集，可能会出现解析失败或求解不符合预期。

## 用你给的两个测试用例验证

> 建议在测试前先保证服务已启动：`mvn spring-boot:run`。

### 测试用例 1：资本充足率边界（期望 `tier1_capital >= 700`）

1) 保存 IR（`ruleSetId = TestCapitalAdequacy_1`）

可直接把下面 JSON 发给 `POST /ir`：
```json
{
  "metadata": { "title": "TestCapitalAdequacy_1", "domain": "Capital Adequacy" },
  "rules": [
    {
      "rule_id": "R_CAP_1",
      "rule_name": "Capital adequacy ratio compliance",
      "category": "CAPITAL",
      "source": { "section": "CAP1", "text": "Capital adequacy ratio regulation." },
      "applicability": "Applies when bank is subject to policy exemption checks.",
      "conditions": [
        {
          "expression": "((tier1_capital + tier2_capital) / risk_weighted_assets) >= 0.08 and core_tier1_ratio >= 0.05",
          "description": "Capital adequacy and core tier1 ratio conditions."
        }
      ],
      "exceptions": [
        { "expression": "is_policy_bank == true", "description": "Policy banks are exempt." }
      ],
      "inputs": [
        { "name": "tier1_capital", "type": "DECIMAL" },
        { "name": "tier2_capital", "type": "DECIMAL" },
        { "name": "risk_weighted_assets", "type": "DECIMAL" },
        { "name": "core_tier1_ratio", "type": "DECIMAL" },
        { "name": "is_policy_bank", "type": "BOOLEAN" }
      ],
      "computation": {
        "type": "STEPS",
        "steps": [ { "step_id": "s1", "formula": "dummy_output = tier1_capital" } ]
      },
      "outputs": [ { "name": "dummy_output", "type": "DECIMAL" } ],
      "test_cases": []
    }
  ],
  "enums": {},
  "functions": []
}
```

2) 逆向求解（固定其它输入，求 `tier1_capital` 最大满足阈值）

请求（`tier1_capital` 设为 `null`）：
```json
{
  "ruleSetId": "TestCapitalAdequacy_1",
  "inputs": {
    "tier2_capital": 100,
    "risk_weighted_assets": 10000,
    "core_tier1_ratio": 0.06,
    "is_policy_bank": false,
    "tier1_capital": null
  },
  "mode": "ANY_RULES",
  "returnOutputs": false
}
```

期望：
- `satisfiable: true`
- `solvedInputs.tier1_capital` 应满足 `>= 700`

### 测试用例 2：LTV 上限（期望 `loan_amount = 4000000`）

1) 保存 IR（`ruleSetId = TestLTV_1`）
```json
{
  "metadata": { "title": "TestLTV_1", "domain": "Mortgage / LTV" },
  "rules": [
    {
      "rule_id": "R_LTV_1",
      "rule_name": "LTV constraint",
      "category": "LTV",
      "source": { "section": "LTV1", "text": "LTV regulatory constraint." },
      "applicability": "Applies to mortgage loans.",
      "conditions": [
        {
          "expression": "loan_amount / property_value <= 0.70 || (first_time_buyer = true and loan_amount / property_value <= 0.80)",
          "description": "LTV upper bounds for general and first-time buyers."
        }
      ],
      "exceptions": [
        { "expression": "has_special_approval == true", "description": "Special approval exempts the rule." }
      ],
      "inputs": [
        { "name": "loan_amount", "type": "DECIMAL" },
        { "name": "property_value", "type": "DECIMAL" },
        { "name": "first_time_buyer", "type": "BOOLEAN" },
        { "name": "has_special_approval", "type": "BOOLEAN" }
      ],
      "computation": {
        "type": "STEPS",
        "steps": [ { "step_id": "s1", "formula": "dummy_output = loan_amount" } ]
      },
      "outputs": [ { "name": "dummy_output", "type": "DECIMAL" } ],
      "test_cases": []
    }
  ],
  "enums": {},
  "functions": []
}
```

2) 逆向求解并最大化 `loan_amount`
```json
{
  "ruleSetId": "TestLTV_1",
  "inputs": {
    "property_value": 5000000,
    "first_time_buyer": true,
    "has_special_approval": false,
    "loan_amount": null
  },
  "mode": "ANY_RULES",
  "objectiveVar": "loan_amount",
  "objectiveMode": "MAX",
  "returnOutputs": false
}
```

期望：
- `satisfiable: true`
- `solvedInputs.loan_amount` 应为 `4000000.0`（对应 0.80 * 5,000,000）

## 当前限制 / 未来方向

- Z3 表达式解析只覆盖当前实现支持的子集（FEEL/函数/ENUM 仍是后续工作）
- MAX 目标当前是“可行性+边界搜索”的方式实现，未来可进一步用 Z3 原生 `Optimize` 做更鲁棒的优化

