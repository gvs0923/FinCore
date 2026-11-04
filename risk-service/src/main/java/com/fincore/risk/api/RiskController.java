package com.fincore.risk.api;

import com.fincore.risk.engine.RiskEngine;
import com.fincore.risk.engine.RiskRequest;
import com.fincore.risk.engine.RiskResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/risk")
public class RiskController {

    private final RiskEngine engine;

    public RiskController(RiskEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/assess")
    public RiskResult assess(@RequestBody RiskRequest request) {
        return engine.assess(request);
    }
}