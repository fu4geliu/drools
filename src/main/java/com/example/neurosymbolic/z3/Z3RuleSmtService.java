package com.example.neurosymbolic.z3;

import com.example.neurosymbolic.ir.model.Condition;
import com.example.neurosymbolic.ir.model.Rule;
import com.example.neurosymbolic.ir.model.RuleDocument;
import com.example.neurosymbolic.ir.model.RuleInput;
import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
import com.microsoft.z3.RealExpr;
import com.microsoft.z3.Status;
import com.microsoft.z3.Solver;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Z3 satisfiability checks for IR rules.
 *
 * Current implementation focuses on:
 * - conditions[] AND (not exceptions[])
 * - expression subset:
 *   - numeric comparisons: >, >=, <, <=, ==
 *   - boolean connectors: &&, || (also supports keywords "and"/"or")
 *   - arithmetic in comparisons: +, -, *, /
 *   - identifiers: must match rule inputs' "name"
 *   - numeric literals: integers/decimals
 *
 * Limitations:
 * - no FEEL/SpEL parsing, no function calls, no ENUM handling yet
 * - computations (computation.steps formula) are not encoded yet
 */
@Service
public class Z3RuleSmtService {

    /**
     * Reverse constraint solving with optional objective optimization.
     *
     * Current supported objective:
     * - {@code objectiveMode="MAX"}: maximize the numeric value of {@code objectiveVar}
     *   assuming feasibility is monotonic (typically when the constraints impose upper bounds).
     *
     * Implementation uses repeated SAT checks with binary search on the objective variable.
     */
    public Z3SolveResult solveInputs(List<Rule> rules,
                                      Map<String, Object> partialInputs,
                                      String mode,
                                      String objectiveVar,
                                      String objectiveMode) {
        if (objectiveVar == null || objectiveVar.isBlank() || objectiveMode == null) {
            return solveInputs(rules, partialInputs, mode);
        }

        String normalizedObjectiveMode = objectiveMode.trim().toUpperCase();
        if (!"MAX".equals(normalizedObjectiveMode)) {
            // Only MAX is implemented for now.
            return solveInputs(rules, partialInputs, mode);
        }

        // If objectiveVar is already fixed (non-null), no need to optimize.
        Object alreadyProvided = partialInputs == null ? null : partialInputs.get(objectiveVar);
        if (alreadyProvided != null) {
            return solveInputs(rules, partialInputs, mode);
        }

        // First, solve normally to get a feasible starting point.
        Z3SolveResult base = solveInputs(rules, partialInputs, mode);
        if (!base.isSatisfiable()) {
            return base;
        }

        Object baseObj = base.getSolvedInputs().get(objectiveVar);
        if (baseObj == null) {
            // Should not happen, but fall back to SAT-only behavior.
            return base;
        }

        double lowFeasible = toDouble(baseObj);
        // Ensure we have a positive step for upper bound search
        double lowForSearch = Double.isNaN(lowFeasible) ? 0.0 : lowFeasible;
        double startCandidate = Math.max(1.0, lowForSearch * 2.0);

        double highCandidate = startCandidate;
        double highInfeasible = Double.NaN;
        double lowUpdated = lowForSearch;

        // 1) Exponential search: find the first infeasible point above low.
        double maxSearchBound = 1e12;
        int expIters = 0;
        int maxExpIters = 80;
        while (expIters < maxExpIters && highCandidate <= maxSearchBound) {
            boolean feasible = isSatisfiableWithFixedObjective(rules, partialInputs, mode, objectiveVar, highCandidate);
            if (feasible) {
                lowUpdated = highCandidate;
                highCandidate = highCandidate * 2.0;
                expIters++;
            } else {
                highInfeasible = highCandidate;
                break;
            }
        }

        if (Double.isNaN(highInfeasible)) {
            // Unbounded within our numeric search range
            base.getErrors().add("Objective appears unbounded within search limit up to " + maxSearchBound);
            // Still return a feasible solution (base is already SAT)
            return base;
        }

        // 2) Binary search between lowUpdated (feasible) and highInfeasible (infeasible).
        double lo = lowUpdated;
        double hi = highInfeasible;
        int binIters = 100;
        double eps = 1e-6;

        for (int i = 0; i < binIters && (hi - lo) > eps; i++) {
            double mid = (lo + hi) / 2.0;
            boolean feasible = isSatisfiableWithFixedObjective(rules, partialInputs, mode, objectiveVar, mid);
            if (feasible) {
                lo = mid;
            } else {
                hi = mid;
            }
        }

        double best = lo;
        double bestRounded = roundIfCloseToInteger(best);

        // 3) Re-solve with objectiveVar fixed to best (so that the rest of unknowns are consistent).
        Map<String, Object> fixedInputs = partialInputs == null ? new HashMap<>() : new HashMap<>(partialInputs);
        fixedInputs.put(objectiveVar, Double.valueOf(bestRounded));
        Z3SolveResult finalRes = solveInputs(rules, fixedInputs, mode);
        finalRes.setSolvedInputs(mergeSolvedInputs(finalRes.getSolvedInputs(), objectiveVar, bestRounded));
        return finalRes;
    }

    public Z3CheckResult checkRuleSatisfiable(RuleDocument document, Rule rule) {
        Z3CheckResult result = new Z3CheckResult();

        // Z3 context
        Map<String, String> cfg = new HashMap<>();
        cfg.put("model", "true");

        try (Context ctx = new Context(cfg)) {
            Map<String, RealExpr> vars = declareInputVars(ctx, rule);
            List<BoolExpr> constraints = new ArrayList<>();

            // conditions[] -> AND
            if (rule.getConditions() != null) {
                for (Condition condition : rule.getConditions()) {
                    if (condition == null || condition.getExpression() == null || condition.getExpression().isBlank()) continue;
                    try {
                        BoolExpr expr = new SimpleConditionZ3Parser(ctx, vars).parse(condition.getExpression());
                        constraints.add(expr);
                    } catch (RuntimeException e) {
                        result.getErrors().add("Failed to parse condition expression: " + condition.getExpression() + " ; " + e.getMessage());
                    }
                }
            }

            // exceptions[] -> NOT (exceptions are "where rule does NOT apply")
            if (rule.getExceptions() != null) {
                for (Condition exception : rule.getExceptions()) {
                    if (exception == null || exception.getExpression() == null || exception.getExpression().isBlank()) continue;
                    try {
                        BoolExpr exExpr = new SimpleConditionZ3Parser(ctx, vars).parse(exception.getExpression());
                        constraints.add(ctx.mkNot(exExpr));
                    } catch (RuntimeException e) {
                        result.getErrors().add("Failed to parse exception expression: " + exception.getExpression() + " ; " + e.getMessage());
                    }
                }
            }

            if (!result.getErrors().isEmpty()) {
                result.setSatisfiable(false);
                return result;
            }

            Solver solver = ctx.mkSolver();
            solver.add(constraints.toArray(new BoolExpr[0]));

            Status status = solver.check();
            if (status == Status.SATISFIABLE) {
                result.setSatisfiable(true);
                Model model = solver.getModel();
                Map<String, String> modelMap = new HashMap<>();
                for (Map.Entry<String, RealExpr> e : vars.entrySet()) {
                    Expr evaluated = model.evaluate(e.getValue(), false);
                    modelMap.put(e.getKey(), evaluated.toString());
                }
                result.setModel(modelMap);
            } else {
                result.setSatisfiable(false);
            }
            return result;
        }
    }

    /**
     * Solve missing input values so that selected rules are satisfied in a Drools-like way:
     * - rule firing constraint = AND(conditions...) AND NOT(exception_i...) for all exceptions
     *
     * This method is intended for "partial assignment" / what-if analysis:
     * - for inputs that are provided (non-null) in {@code partialInputs}, we add equality constraints.
     * - for inputs that are missing or null, we leave them unconstrained and return Z3 model values.
     *
     * Supported condition expression subset is the same as in {@link #checkRuleSatisfiable(RuleDocument, Rule)}.
     *
     * @param rules selected rules to encode
     * @param partialInputs input assignment, where missing/null values are treated as unknowns
     * @param mode "ANY" => at least one rule fires, "ALL" => all selected rules fire
     */
    public Z3SolveResult solveInputs(List<Rule> rules, Map<String, Object> partialInputs, String mode) {
        Z3SolveResult result = new Z3SolveResult();
        List<Rule> safeRules = rules == null ? List.of() : rules;

        if (safeRules.isEmpty()) {
            result.getErrors().add("No rules provided to Z3 solver.");
            result.setSatisfiable(false);
            return result;
        }

        Map<String, String> inputTypes = buildInputTypes(safeRules);

        // Z3 context
        Map<String, String> cfg = new HashMap<>();
        cfg.put("model", "true");

        try (Context ctx = new Context(cfg)) {
            Map<String, RealExpr> vars = declareInputVars(ctx, safeRules);
            List<String> unknowns = computeUnknownInputNames(vars.keySet(), partialInputs);
            List<BoolExpr> constraints = new ArrayList<>();

            // Encode rule firing constraints
            String normalizedMode = mode == null ? "ANY" : mode.trim().toUpperCase();
            BoolExpr combined;
            if ("ALL".equals(normalizedMode)) {
                combined = null;
                for (Rule r : safeRules) {
                    BoolExpr firing = buildRuleFiringConstraint(ctx, vars, r, result);
                    if (firing == null) continue;
                    combined = (combined == null) ? firing : ctx.mkAnd(combined, firing);
                }
            } else {
                // default ANY
                combined = null;
                for (Rule r : safeRules) {
                    BoolExpr firing = buildRuleFiringConstraint(ctx, vars, r, result);
                    if (firing == null) continue;
                    combined = (combined == null) ? firing : ctx.mkOr(combined, firing);
                }
            }

            if (!result.getErrors().isEmpty()) {
                result.setSatisfiable(false);
                return result;
            }

            if (combined != null) {
                constraints.add(combined);
            }

            // Add equality constraints for known inputs
            if (partialInputs != null) {
                for (Map.Entry<String, Object> e : partialInputs.entrySet()) {
                    String name = e.getKey();
                    if (name == null) continue;
                    if (!vars.containsKey(name)) continue;
                    Object value = e.getValue();
                    if (value == null) continue; // null => unknown

                    try {
                        // Model values are encoded as Real:
                        // - boolean true/false => 1/0
                        // - numbers => numeric literal
                        // - strings "true"/"false" => 1/0
                        String valueText;
                        if (value instanceof Boolean b) {
                            valueText = b ? "1" : "0";
                        } else {
                            String s = value.toString().trim();
                            if ("true".equalsIgnoreCase(s)) {
                                valueText = "1";
                            } else if ("false".equalsIgnoreCase(s)) {
                                valueText = "0";
                            } else {
                                valueText = s;
                            }
                        }
                        constraints.add(ctx.mkEq(vars.get(name), ctx.mkReal(valueText)));
                    } catch (RuntimeException ex) {
                        result.getErrors().add("Failed to bind known input '" + name + "' value=" + value + " ; " + ex.getMessage());
                    }
                }
            }

            if (!result.getErrors().isEmpty()) {
                result.setSatisfiable(false);
                return result;
            }

            Solver solver = ctx.mkSolver();
            solver.add(constraints.toArray(new BoolExpr[0]));

            Status status = solver.check();
            if (status == Status.SATISFIABLE) {
                result.setSatisfiable(true);
                Model model = solver.getModel();

                Map<String, Object> modelMap = new HashMap<>();
                for (String unknown : unknowns) {
                    RealExpr var = vars.get(unknown);
                    if (var == null) continue;
                    Expr evaluated = model.evaluate(var, false);
                    if (evaluated == null) continue;
                    String v = evaluated.toString();
                    String type = inputTypes.getOrDefault(unknown, "DECIMAL").toUpperCase();
                    modelMap.put(unknown, coerceZ3RealToJavaValue(v, type));
                }
                result.setSolvedInputs(modelMap);
            } else {
                result.setSatisfiable(false);
            }
            return result;
        }
    }

    private boolean isSatisfiableWithFixedObjective(List<Rule> rules,
                                                    Map<String, Object> partialInputs,
                                                    String mode,
                                                    String objectiveVar,
                                                    double candidate) {
        Map<String, Object> candidateInputs = partialInputs == null ? new HashMap<>() : new HashMap<>(partialInputs);
        // Use a plain decimal string to avoid scientific notation issues in mkReal(String).
        candidateInputs.put(objectiveVar, formatReal(candidate));
        return solveInputs(rules, candidateInputs, mode).isSatisfiable();
    }

    private static String formatReal(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        java.math.BigDecimal bd = new java.math.BigDecimal(Double.toString(v));
        return bd.stripTrailingZeros().toPlainString();
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    private static double roundIfCloseToInteger(double v) {
        long rounded = Math.round(v);
        if (Math.abs(v - rounded) < 1e-3) {
            return (double) rounded;
        }
        return v;
    }

    private static Map<String, Object> mergeSolvedInputs(Map<String, Object> baseSolved,
                                                          String objectiveVar,
                                                          double objectiveValue) {
        Map<String, Object> merged = baseSolved == null ? new HashMap<>() : new HashMap<>(baseSolved);
        merged.put(objectiveVar, objectiveValue);
        return merged;
    }

    private Map<String, RealExpr> declareInputVars(Context ctx, Rule rule) {
        Map<String, RealExpr> vars = new HashMap<>();
        if (rule == null || rule.getInputs() == null) return vars;

        for (RuleInput in : rule.getInputs()) {
            if (in == null || in.getName() == null || in.getName().isBlank()) continue;
            // For initial integration we model all arithmetic as Real.
            vars.put(in.getName(), ctx.mkRealConst(in.getName()));
        }
        return vars;
    }

    private Map<String, RealExpr> declareInputVars(Context ctx, List<Rule> rules) {
        Map<String, RealExpr> vars = new HashMap<>();
        if (rules == null) return vars;
        for (Rule rule : rules) {
            if (rule == null || rule.getInputs() == null) continue;
            for (RuleInput in : rule.getInputs()) {
                if (in == null || in.getName() == null || in.getName().isBlank()) continue;
                // For initial integration we model all arithmetic as Real.
                vars.putIfAbsent(in.getName(), ctx.mkRealConst(in.getName()));
            }
        }
        return vars;
    }

    public static class Z3CheckResult {
        private boolean satisfiable;
        private Map<String, String> model = new HashMap<>();
        private List<String> errors = new ArrayList<>();

        public boolean isSatisfiable() {
            return satisfiable;
        }

        public void setSatisfiable(boolean satisfiable) {
            this.satisfiable = satisfiable;
        }

        public Map<String, String> getModel() {
            return model;
        }

        public void setModel(Map<String, String> model) {
            this.model = model != null ? model : new HashMap<>();
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    public static class Z3SolveResult {
        private boolean satisfiable;
        private Map<String, Object> solvedInputs = new HashMap<>();
        private List<String> errors = new ArrayList<>();

        public boolean isSatisfiable() {
            return satisfiable;
        }

        public void setSatisfiable(boolean satisfiable) {
            this.satisfiable = satisfiable;
        }

        public Map<String, Object> getSolvedInputs() {
            return solvedInputs;
        }

        public void setSolvedInputs(Map<String, Object> solvedInputs) {
            this.solvedInputs = solvedInputs != null ? solvedInputs : new HashMap<>();
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Minimal parser from an IR condition string to Z3 BoolExpr.
     * See class javadoc for supported expression subset.
     */
    private static class SimpleConditionZ3Parser {
        private final Context ctx;
        private final Map<String, RealExpr> vars;
        private final Tokenizer tokenizer;

        public SimpleConditionZ3Parser(Context ctx, Map<String, RealExpr> vars) {
            this.ctx = ctx;
            this.vars = vars;
            this.tokenizer = new Tokenizer();
        }

        public BoolExpr parse(String input) {
            this.tokenizer.reset(input);
            BoolExpr expr = parseOr();
            tokenizer.expect(TokenType.EOF);
            return expr;
        }

        // orExpr := andExpr ( ( '||' | 'or' ) andExpr )*
        private BoolExpr parseOr() {
            BoolExpr left = parseAnd();
            while (tokenizer.peek(TokenType.OR)) {
                tokenizer.next();
                BoolExpr right = parseAnd();
                left = ctx.mkOr(left, right);
            }
            return left;
        }

        // andExpr := compExpr ( ( '&&' | 'and' ) compExpr )*
        private BoolExpr parseAnd() {
            BoolExpr left = parseComp();
            while (tokenizer.peek(TokenType.AND)) {
                tokenizer.next();
                BoolExpr right = parseComp();
                left = ctx.mkAnd(left, right);
            }
            return left;
        }

        // compExpr := '(' orExpr ')' | arith (compOp arith)?
        private BoolExpr parseComp() {
                // Ambiguous case: '(' ... ')'
                // - it might be boolean grouping: (a > 1 and b < 2)
                // - or arithmetic grouping: ((a + b) / c) >= 0.1 and ...
                //
                // We first try boolean grouping, and if it fails, we backtrack and
                // parse it as an arithmetic expression (handled by parseArith()).
                if (tokenizer.peek(TokenType.LPAREN)) {
                    TokenizerState state = tokenizer.mark();
                    tokenizer.next(); // consume '('
                    try {
                        BoolExpr inner = parseOr();
                        tokenizer.expect(TokenType.RPAREN);
                        return inner;
                    } catch (RuntimeException ignored) {
                        tokenizer.restore(state);
                    }
                }

            if (tokenizer.peek(TokenType.TRUE)) {
                tokenizer.next();
                return ctx.mkTrue();
            }
            if (tokenizer.peek(TokenType.FALSE)) {
                tokenizer.next();
                return ctx.mkFalse();
            }

            ArithExpr left = parseArith();
            if (!tokenizer.peekAny(TokenType.GT, TokenType.GE, TokenType.LT, TokenType.LE, TokenType.EQ)) {
                throw new RuntimeException("Comparison operator expected after arithmetic expression.");
            }

            TokenType op = tokenizer.next().type;
            ArithExpr right = parseArith();

            return switch (op) {
                case GT -> ctx.mkGt(left, right);
                case GE -> ctx.mkGe(left, right);
                case LT -> ctx.mkLt(left, right);
                case LE -> ctx.mkLe(left, right);
                case EQ -> ctx.mkEq(left, right);
                default -> throw new RuntimeException("Unsupported comparison operator: " + op);
            };
        }

        // arithExpr := term ( ('+' | '-') term )*
        private ArithExpr parseArith() {
            ArithExpr left = parseTerm();
            while (tokenizer.peek(TokenType.PLUS) || tokenizer.peek(TokenType.MINUS)) {
                TokenType op = tokenizer.next().type;
                ArithExpr right = parseTerm();
                left = (op == TokenType.PLUS) ? ctx.mkAdd(left, right) : ctx.mkSub(left, right);
            }
            return left;
        }

        // term := factor ( ('*' | '/') factor )*
        private ArithExpr parseTerm() {
            ArithExpr left = parseFactor();
            while (tokenizer.peek(TokenType.MUL) || tokenizer.peek(TokenType.DIV)) {
                TokenType op = tokenizer.next().type;
                ArithExpr right = parseFactor();
                left = (op == TokenType.MUL) ? ctx.mkMul(left, right) : ctx.mkDiv(left, right);
            }
            return left;
        }

        // factor := number | ident | '-' factor | '(' arithExpr ')'
        private ArithExpr parseFactor() {
            if (tokenizer.peek(TokenType.TRUE)) {
                tokenizer.next();
                return ctx.mkReal("1");
            }
            if (tokenizer.peek(TokenType.FALSE)) {
                tokenizer.next();
                return ctx.mkReal("0");
            }

            if (tokenizer.peek(TokenType.MINUS)) {
                tokenizer.next();
                ArithExpr inner = parseFactor();
                return ctx.mkUnaryMinus(inner);
            }

            if (tokenizer.peek(TokenType.LPAREN)) {
                tokenizer.next();
                ArithExpr inner = parseArith();
                tokenizer.expect(TokenType.RPAREN);
                return inner;
            }

            if (tokenizer.peek(TokenType.NUMBER)) {
                String raw = tokenizer.next().text;
                // Some Z3 Java bindings only provide mkReal(String),
                // so we keep the token's original numeric text.
                return ctx.mkReal(raw);
            }

            if (tokenizer.peek(TokenType.IDENT)) {
                String name = tokenizer.next().text;
                RealExpr var = vars.get(name);
                if (var == null) {
                    throw new RuntimeException("Unknown identifier in condition: " + name);
                }
                return var;
            }

            throw new RuntimeException("Unexpected token in arithmetic expression: " + tokenizer.peekType());
        }

        // Tokenizer + recursive descent parser
        private enum TokenType {
            IDENT, NUMBER, TRUE, FALSE,
            AND, OR,
            GT, GE, LT, LE, EQ,
            PLUS, MINUS, MUL, DIV,
            LPAREN, RPAREN,
            EOF
        }

        private static class Token {
            final TokenType type;
            final String text;

            Token(TokenType type, String text) {
                this.type = type;
                this.text = text;
            }
        }

        private static class Tokenizer {
            private String s = "";
            private int i = 0;
            private Token current;

            void reset(String input) {
                this.s = input;
                this.i = 0;
                this.current = nextToken();
            }

            boolean peek(TokenType t) {
                return current.type == t;
            }

            boolean peekAny(TokenType... ts) {
                for (TokenType t : ts) {
                    if (current.type == t) return true;
                }
                return false;
            }

            Token next() {
                Token t = current;
                current = nextToken();
                return t;
            }

            void expect(TokenType t) {
                if (current.type != t) {
                    throw new RuntimeException("Expected token " + t + " but got " + current.type);
                }
                next();
            }

            TokenType peekType() {
                return current.type;
            }

            TokenizerState mark() {
                return new TokenizerState(i, current);
            }

            void restore(TokenizerState state) {
                this.i = state.i;
                this.current = state.current;
            }

            private Token nextToken() {
                int n = s.length();
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                if (i >= n) return new Token(TokenType.EOF, "");

                char c = s.charAt(i);

                // identifiers / keywords
                if (Character.isLetter(c) || c == '_') {
                    int start = i;
                    i++;
                    while (i < n) {
                        char cc = s.charAt(i);
                        if (Character.isLetterOrDigit(cc) || cc == '_') i++;
                        else break;
                    }
                    String text = s.substring(start, i);
                    String lower = text.toLowerCase();
                    return switch (lower) {
                        case "true" -> new Token(TokenType.TRUE, text);
                        case "false" -> new Token(TokenType.FALSE, text);
                        case "and" -> new Token(TokenType.AND, text);
                        case "or" -> new Token(TokenType.OR, text);
                        default -> new Token(TokenType.IDENT, text);
                    };
                }

                // numbers (int/decimal)
                if (Character.isDigit(c) || c == '.') {
                    int start = i;
                    i++;
                    while (i < n) {
                        char cc = s.charAt(i);
                        if (Character.isDigit(cc) || cc == '.') i++;
                        else break;
                    }
                    String text = s.substring(start, i);
                    return new Token(TokenType.NUMBER, text);
                }

                // two-char ops
                if (i + 1 < n) {
                    char c2 = s.charAt(i + 1);
                    if (c == '&' && c2 == '&') {
                        i += 2;
                        return new Token(TokenType.AND, "&&");
                    }
                    if (c == '|' && c2 == '|') {
                        i += 2;
                        return new Token(TokenType.OR, "||");
                    }
                    if (c == '>' && c2 == '=') {
                        i += 2;
                        return new Token(TokenType.GE, ">=");
                    }
                    if (c == '<' && c2 == '=') {
                        i += 2;
                        return new Token(TokenType.LE, "<=");
                    }
                    if (c == '=' && c2 == '=') {
                        i += 2;
                        return new Token(TokenType.EQ, "==");
                    }
                }

                // single-char ops
                i++;
                return switch (c) {
                    case '>' -> new Token(TokenType.GT, ">");
                    case '<' -> new Token(TokenType.LT, "<");
                    case '+' -> new Token(TokenType.PLUS, "+");
                    case '-' -> new Token(TokenType.MINUS, "-");
                    case '*' -> new Token(TokenType.MUL, "*");
                    case '/' -> new Token(TokenType.DIV, "/");
                    case '(' -> new Token(TokenType.LPAREN, "(");
                    case ')' -> new Token(TokenType.RPAREN, ")");
                    case '=' -> new Token(TokenType.EQ, "="); // support single '=' as equality
                    default -> throw new RuntimeException("Unexpected character in condition: '" + c + "'");
                };
            }
        }

        private static class TokenizerState {
            final int i;
            final Token current;

            TokenizerState(int i, Token current) {
                this.i = i;
                this.current = current;
            }
        }
    }

    private BoolExpr buildRuleFiringConstraint(Context ctx, Map<String, RealExpr> vars, Rule rule, Z3CheckResult errorCarrier) {
        if (rule == null) return null;
        List<BoolExpr> parts = new ArrayList<>();

        if (rule.getConditions() != null) {
            for (Condition condition : rule.getConditions()) {
                if (condition == null || condition.getExpression() == null || condition.getExpression().isBlank()) continue;
                try {
                    parts.add(new SimpleConditionZ3Parser(ctx, vars).parse(condition.getExpression()));
                } catch (RuntimeException e) {
                    errorCarrier.getErrors().add("Failed to parse condition expression: " + condition.getExpression() + " ; " + e.getMessage());
                    return null;
                }
            }
        }

        if (rule.getExceptions() != null) {
            for (Condition exception : rule.getExceptions()) {
                if (exception == null || exception.getExpression() == null || exception.getExpression().isBlank()) continue;
                try {
                    BoolExpr exExpr = new SimpleConditionZ3Parser(ctx, vars).parse(exception.getExpression());
                    parts.add(ctx.mkNot(exExpr));
                } catch (RuntimeException e) {
                    errorCarrier.getErrors().add("Failed to parse exception expression: " + exception.getExpression() + " ; " + e.getMessage());
                    return null;
                }
            }
        }

        if (parts.isEmpty()) return ctx.mkTrue();

        BoolExpr combined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            combined = ctx.mkAnd(combined, parts.get(i));
        }
        return combined;
    }

    private BoolExpr buildRuleFiringConstraint(Context ctx, Map<String, RealExpr> vars, Rule rule, Z3SolveResult errorCarrier) {
        // helper overload to avoid changing existing parser code
        if (rule == null) return null;
        List<BoolExpr> parts = new ArrayList<>();

        if (rule.getConditions() != null) {
            for (Condition condition : rule.getConditions()) {
                if (condition == null || condition.getExpression() == null || condition.getExpression().isBlank()) continue;
                try {
                    parts.add(new SimpleConditionZ3Parser(ctx, vars).parse(condition.getExpression()));
                } catch (RuntimeException e) {
                    errorCarrier.getErrors().add("Failed to parse condition expression: " + condition.getExpression() + " ; " + e.getMessage());
                    return null;
                }
            }
        }

        if (rule.getExceptions() != null) {
            for (Condition exception : rule.getExceptions()) {
                if (exception == null || exception.getExpression() == null || exception.getExpression().isBlank()) continue;
                try {
                    BoolExpr exExpr = new SimpleConditionZ3Parser(ctx, vars).parse(exception.getExpression());
                    parts.add(ctx.mkNot(exExpr));
                } catch (RuntimeException e) {
                    errorCarrier.getErrors().add("Failed to parse exception expression: " + exception.getExpression() + " ; " + e.getMessage());
                    return null;
                }
            }
        }

        if (parts.isEmpty()) return ctx.mkTrue();

        BoolExpr combined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            combined = ctx.mkAnd(combined, parts.get(i));
        }
        return combined;
    }

    private Map<String, String> buildInputTypes(List<Rule> rules) {
        Map<String, String> types = new HashMap<>();
        if (rules == null) return types;
        for (Rule rule : rules) {
            if (rule == null || rule.getInputs() == null) continue;
            for (RuleInput in : rule.getInputs()) {
                if (in == null || in.getName() == null) continue;
                if (in.getType() == null) continue;
                types.putIfAbsent(in.getName(), in.getType());
            }
        }
        return types;
    }

    private List<String> computeUnknownInputNames(Iterable<String> allInputNames, Map<String, Object> partialInputs) {
        List<String> unknowns = new ArrayList<>();
        for (String name : allInputNames) {
            if (name == null || name.isBlank()) continue;
            Object v = partialInputs == null ? null : partialInputs.get(name);
            if (v == null) unknowns.add(name);
        }
        return unknowns;
    }

    private Object coerceZ3RealToJavaValue(String z3RealText, String inputType) {
        // z3RealText example: "3/2", "12", "0.8"
        double d = parseRealToDouble(z3RealText);
        if ("INTEGER".equalsIgnoreCase(inputType)) {
            long rounded = Math.round(d);
            // Return Integer to match Drools compiler's intValue usage (it accepts any Number).
            return (int) rounded;
        }
        // DECIMAL/DATE/ENUM are not fully modeled yet; default to Double.
        return d;
    }

    private double parseRealToDouble(String z3RealText) {
        String s = z3RealText == null ? "0" : z3RealText.trim();
        if (s.isEmpty()) return 0.0;
        // Handle rational format like "3/2"
        if (s.contains("/")) {
            String[] parts = s.split("/");
            if (parts.length == 2) {
                double a = Double.parseDouble(parts[0]);
                double b = Double.parseDouble(parts[1]);
                return b == 0.0 ? 0.0 : (a / b);
            }
        }
        return Double.parseDouble(s);
    }
}

