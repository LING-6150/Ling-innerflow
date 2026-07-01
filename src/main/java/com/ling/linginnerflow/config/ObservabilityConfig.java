package com.ling.linginnerflow.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

@Slf4j
@Configuration
public class ObservabilityConfig {

    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }

    @PostConstruct
    void enableReactorContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
        log.info("[Observability] Reactor automatic context propagation enabled");
    }

    @Bean
    ObservationHandler<Observation.Context> llmCostObservationHandler(MeterRegistry meterRegistry) {
        return new LlmCostObservationHandler(meterRegistry);
    }
}
