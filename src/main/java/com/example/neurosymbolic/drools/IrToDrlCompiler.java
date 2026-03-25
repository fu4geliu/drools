package com.example.neurosymbolic.drools;

import com.example.neurosymbolic.ir.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Compile the IR {@link RuleDocument} into executable Drools DRL.
 * <p>
 * 编写 DRL 时：从 $ctx.getInputs() 绑定变量，不使用未绑定变量；根据 IR 的 formula 生成可执行 RHS，不保留 TODO。
 */
public class IrToDrlCompiler {

    /**
     * Compile a {@link RuleDocument} into DRL.
     *
     * @param document   the IR document
     * @param packageName drools package name, e.g. "com.example.neurosymbolic.rules"
     * @return DRL source
     */
    public String compile(RuleDocument document, String packageName) {
        StringBuilder sb = new StringBuilder();

        // Package & header comments
        sb.append("package ").append(packageName).append(";\n\n");

        // Import the generic context fact
        sb.append("import ").append(RuleContext.class.getName()).append(";\n\n");

        Metadata metadata = document.getMetadata();
        if (metadata != null) {
            sb.append("// IR Metadata\n");
            if (metadata.getTitle() != null) {
                sb.append("//   title: ").append(escapeComment(metadata.getTitle())).append("\n");
            }
            if (metadata.getDomain() != null) {
                sb.append("//   domain: ").append(escapeComment(metadata.getDomain())).append("\n");
            }
            if (metadata.getSourceDocument() != null) {
                sb.append("//   source_document: ").append(escapeComment(metadata.getSourceDocument())).append("\n");
            }
            if (metadata.getVersion() != null) {
                sb.append("//   version: ").append(escapeComment(metadata.getVersion())).append("\n");
            }
            sb.append("\n");
        }

        if (document.getRules() != null) {
            for (Rule rule : document.getRules()) {
                appendRule(sb, rule);
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private void appendRule(StringBuilder sb, Rule rule) {
        List<String> inputNames = new ArrayList<>();
        if (rule.getInputs() != null) {
            for (RuleInput in : rule.getInputs()) {
                if (in.getName() != null && !in.getName().isEmpty()) {
                    inputNames.add(in.getName());
                }
            }
        }

        String ruleName = (rule.getRuleId() != null ? rule.getRuleId() : "") +
                (rule.getRuleName() != null ? " - " + rule.getRuleName() : "");

        sb.append("rule \"").append(escapeString(ruleName.trim())).append("\"\n");
        sb.append("    when\n");
        // 不按 ruleId 过滤，执行时传入的 context 的 ruleId 为 ruleSetId（文档 title），与规则内 rule_id 不一致；统一用 RuleContext() 以便触发
        sb.append("        $ctx : RuleContext()\n");

        // 条件中的标识符替换为从 $ctx.getInputs() 取值，避免未绑定变量
        if (rule.getConditions() != null) {
            for (Condition condition : rule.getConditions()) {
                if (condition.getExpression() != null && !condition.getExpression().isEmpty()) {
                    String boundExpr = bindInputsInExpression(condition.getExpression(), rule.getInputs());
                    sb.append("        eval(").append(boundExpr).append(")\n");
                }
            }
        }

        sb.append("    then\n");

        if (rule.getSource() != null) {
            if (rule.getSource().getSection() != null) {
                sb.append("        // source.section: ").append(escapeComment(rule.getSource().getSection())).append("\n");
            }
            if (rule.getSource().getText() != null) {
                sb.append("        // source.text: ").append(escapeComment(rule.getSource().getText())).append("\n");
            }
        }

        // 从 $ctx.getInputs() 绑定局部变量，避免 RHS 中直接使用未绑定变量
        for (RuleInput in : rule.getInputs() != null ? rule.getInputs() : List.<RuleInput>of()) {
            String name = in.getName();
            if (name == null || name.isEmpty()) continue;
            String type = in.getType() != null ? in.getType().toUpperCase() : "DECIMAL";
            switch (type) {
                case "INTEGER":
                    sb.append("        int ").append(safeVarName(name)).append(" = $ctx.getInputs().get(\"").append(escapeString(name)).append("\") != null ? ((Number)$ctx.getInputs().get(\"").append(escapeString(name)).append("\")).intValue() : 0;\n");
                    break;
                case "BOOLEAN":
                    sb.append("        boolean ").append(safeVarName(name)).append(" = Boolean.TRUE.equals($ctx.getInputs().get(\"").append(escapeString(name)).append("\"));\n");
                    break;
                case "STRING":
                    sb.append("        String ").append(safeVarName(name)).append(" = $ctx.getInputs().get(\"").append(escapeString(name)).append("\") != null ? String.valueOf($ctx.getInputs().get(\"").append(escapeString(name)).append("\")) : \"\";\n");
                    break;
                case "DECIMAL":
                case "DATE":
                default:
                    sb.append("        double ").append(safeVarName(name)).append(" = $ctx.getInputs().get(\"").append(escapeString(name)).append("\") != null ? ((Number)$ctx.getInputs().get(\"").append(escapeString(name)).append("\")).doubleValue() : 0.0;\n");
                    break;
            }
        }

        // 根据 computation.steps 的 formula 生成可执行 RHS，可引入新变量
        Computation computation = rule.getComputation();
        if (computation instanceof ComputationSteps steps && steps.getSteps() != null) {
            for (ComputationSteps.ComputationStep step : steps.getSteps()) {
                String formula = step.getFormula();
                if (formula == null || formula.isEmpty()) continue;
                emitFormulaRhs(sb, formula, rule.getInputs(), rule.getOutputs());
            }
        } else if (computation instanceof ComputationLookup lookup) {
            if (lookup.getLookup_ref() != null) {
                sb.append("        // lookup_ref: ").append(escapeComment(lookup.getLookup_ref())).append(" (invoke externally if needed)\n");
            }
            if (rule.getOutputs() != null) {
                for (RuleOutput out : rule.getOutputs()) {
                    if (out.getName() != null) {
                        sb.append("        $ctx.getOutputs().put(\"").append(escapeString(out.getName())).append("\", null);\n");
                    }
                }
            }
        } else if (rule.getOutputs() != null) {
            for (RuleOutput out : rule.getOutputs()) {
                if (out.getName() != null) {
                    sb.append("        $ctx.getOutputs().put(\"").append(escapeString(out.getName())).append("\", null);\n");
                }
            }
        }

        sb.append("end\n");
    }

    /** 将条件表达式中的输入名替换为从 $ctx.getInputs() 取值的表达式，避免未绑定变量 */
    private String bindInputsInExpression(String expression, List<RuleInput> inputs) {
        if (expression == null || inputs == null) return expression;
        String result = expression;
        for (RuleInput in : inputs) {
            String name = in.getName();
            if (name == null || name.isEmpty()) continue;
            String type = in.getType() != null ? in.getType().toUpperCase() : "DECIMAL";
            String replacement;
            switch (type) {
                case "INTEGER":
                    replacement = "($ctx.getInputs().get(\"" + escapeString(name) + "\") != null ? ((Number)$ctx.getInputs().get(\"" + escapeString(name) + "\")).intValue() : 0)";
                    break;
                case "BOOLEAN":
                    replacement = "Boolean.TRUE.equals($ctx.getInputs().get(\"" + escapeString(name) + "\"))";
                    break;
                case "STRING":
                    replacement = "(String)$ctx.getInputs().get(\"" + escapeString(name) + "\")";
                    break;
                default:
                    replacement = "($ctx.getInputs().get(\"" + escapeString(name) + "\") != null ? ((Number)$ctx.getInputs().get(\"" + escapeString(name) + "\")).doubleValue() : 0.0)";
                    break;
            }
            result = result.replaceAll("\\b" + Pattern.quote(name) + "\\b", java.util.regex.Matcher.quoteReplacement(replacement));
        }
        return result;
    }

    private static String safeVarName(String name) {
        if (name == null) return "v";
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /** 解析 formula（如 discounted_amount = amount * 0.8），生成赋值；若左侧为规则 output 则 put 到 ctx.outputs */
    private void emitFormulaRhs(StringBuilder sb, String formula, List<RuleInput> inputs, List<RuleOutput> outputs) {
        formula = formula.trim();
        int eq = formula.indexOf('=');
        if (eq <= 0) return;
        String left = formula.substring(0, eq).trim();
        String right = formula.substring(eq + 1).trim();
        if (left.isEmpty() || right.isEmpty()) return;
        String outName = left;
        String expr = right;
        for (RuleInput in : inputs != null ? inputs : List.<RuleInput>of()) {
            String name = in.getName();
            if (name == null) continue;
            String varName = safeVarName(name);
            expr = expr.replaceAll("\\b" + Pattern.quote(name) + "\\b", varName);
        }
        String varName = safeVarName(outName);
        sb.append("        double ").append(varName).append(" = ").append(expr).append(";\n");
        boolean isDeclaredOutput = outputs != null && outputs.stream()
                .anyMatch(o -> outName.equals(o.getName()));
        if (isDeclaredOutput) {
            sb.append("        $ctx.getOutputs().put(\"").append(escapeString(outName)).append("\", ").append(varName).append(");\n");
        }
    }

    private String escapeString(String value) {
        return value.replace("\"", "\\\"");
    }

    private String escapeComment(String value) {
        return value.replace("\n", " ").replace("\r", " ");
    }
}

