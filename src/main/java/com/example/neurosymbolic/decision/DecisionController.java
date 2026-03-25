package com.example.neurosymbolic.decision;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/decision")
@Validated
public class DecisionController {

    private final DecisionExecutionService executionService;
    private final DecisionZ3SolveService z3SolveService;

    public DecisionController(DecisionExecutionService executionService,
                                DecisionZ3SolveService z3SolveService) {
        this.executionService = executionService;
        this.z3SolveService = z3SolveService;
    }

    /**
     * Execute decision for a given rule set and inputs.
     *
     * 请求体示例：
     * {
     *   "ruleSetId": "Basel III Market Risk",
     *   "inputs": {
     *     "lgd": 0.45,
     *     "notional": 1000000,
     *     "pnl": 0,
     *     "position_direction": "LONG"
     *   }
     * }
     */
    @PostMapping("/execute")
    public ResponseEntity<DecisionResponse> execute(@RequestBody DecisionRequest request) {
        DecisionResponse response = executionService.execute(request.getRuleSetId(), request.getInputs());
        return ResponseEntity.ok(response);
    }

    /**
     * Reverse constraint solving:
     * solve missing input values so that one/more rules can fire.
     */
    @PostMapping("/solve")
    public ResponseEntity<DecisionSolveResponse> solve(@RequestBody DecisionSolveRequest request) {
        DecisionSolveResponse response = z3SolveService.solve(request);
        return ResponseEntity.ok(response);
    }
}

