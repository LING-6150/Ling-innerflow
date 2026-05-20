package com.ling.linginnerflow.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker.CircuitBreakerStrategy;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.List ;

@Configuration
public class SentinelFlowRuleConfig {

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
    }

    private void initFlowRules() {
        FlowRule loginRule = new FlowRule("POST:/api/auth/login")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(20);

        FlowRule registerRule = new FlowRule("POST:/api/auth/register")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(5);

        FlowRule chatRule = new FlowRule("POST:/api/chat")
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(10);

        FlowRuleManager.loadRules(List.of(loginRule, registerRule, chatRule));
    }

    private void initDegradeRules() {
        DegradeRule chatDegrade = new DegradeRule("POST:/api/chat")
                .setGrade(CircuitBreakerStrategy.ERROR_RATIO.getType())
                .setCount(0.5)
                .setStatIntervalMs(10_000)
                .setMinRequestAmount(5)
                .setTimeWindow(10);

        DegradeRuleManager.loadRules(List.of(chatDegrade));
    }
}
