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

/**
 * P1-02 — central observability wiring.
 *
 * Three concerns the system lacked entirely:
 *  1. {@code @Observed} support, so graph nodes / services (P1-03..05) can be
 *     instrumented with a single annotation.
 *  2. Reactive context propagation, so a span opened on the request thread stays
 *     "current" across ReActAgent's {@code Flux} + {@code boundedElastic} hops
 *     (the P1-06 spike). NOTE: plain {@code CompletableFuture.supplyAsync} is NOT
 *     covered by this hook — the tool dispatch in ReActAgent needs an explicit
 *     {@code ContextSnapshot} capture/restore, handled in P1-06.
 *  3. Cost: turn Spring AI's gen_ai token usage into a $-denominated meter
 *     ({@link LlmCostObservationHandler}).
 *
 * Prompt-version tagging lives in {@link Observations}.
 */
@Slf4j
@Configuration
public class ObservabilityConfig {

    /** Enables the {@code @Observed} annotation on Spring-managed beans. */
    @Bean
    ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }

    /**
     * Make Reactor restore ThreadLocal context (incl. the current Observation /
     * trace context) automatically when work hops threads in a reactive chain.
     * The {@code ObservationThreadLocalAccessor} is auto-registered via
     * context-propagation's ServiceLoader, so enabling the hook is sufficient
     * for Flux/Mono boundaries.
     */
    @PostConstruct
    void enableReactorContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
        log.info("[Observability] Reactor automatic context propagation enabled");
    }

    /**
     * Derives an LLM spend meter from the token-usage key values Spring AI 1.0
     * attaches to its gen_ai observations. Decoupled from Spring AI internal
     * types — reads key values by OTel gen_ai semconv name, so it degrades to a
     * no-op (rather than a crash) if the names differ; exact names are confirmed
     * at verify time / in the P1-06 spike.
     */
    @Bean
    ObservationHandler<Observation.Context> llmCostObservationHandler(MeterRegistry meters) {
        return new LlmCostObservationHandler(meters);
    }
}
