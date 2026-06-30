package com.ling.linginnerflow.config;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts the token-usage key values that Spring AI 1.0 records on its gen_ai
 * observations into:
 *   - {@code llm.tokens}  (counter, tags: model, type=input|output)
 *   - {@code llm.cost.usd} (counter, tag: model) — derived via a static price map
 *
 * Reads key values by OTel gen_ai semantic-convention name across all cardinality
 * buckets, so it does not depend on Spring AI internal classes and never throws
 * if a name is missing — it simply records nothing for that call. Exact key names
 * (especially for the streaming path, where usage may only arrive on the final
 * chunk) are confirmed during the P1-06 spike.
 */
@Slf4j
public class LlmCostObservationHandler implements ObservationHandler<Observation.Context> {

    // gpt-4o-mini list price (USD per token). Update when the model changes.
    private static final double GPT_4O_MINI_INPUT_PER_TOKEN  = 0.15 / 1_000_000.0;
    private static final double GPT_4O_MINI_OUTPUT_PER_TOKEN = 0.60 / 1_000_000.0;

    private static final String K_INPUT_TOKENS  = "gen_ai.usage.input_tokens";
    private static final String K_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    private static final String K_MODEL         = "gen_ai.request.model";

    private final MeterRegistry meters;

    public LlmCostObservationHandler(MeterRegistry meters) {
        this.meters = meters;
    }

    /** Inspect every observation; cheap early-return below if it carries no token usage. */
    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onStop(Observation.Context context) {
        long inputTokens  = readLong(context, K_INPUT_TOKENS);
        long outputTokens = readLong(context, K_OUTPUT_TOKENS);
        if (inputTokens == 0 && outputTokens == 0) {
            return; // not an LLM observation, or usage not reported
        }

        String model = readString(context, K_MODEL, "unknown");
        double costUsd = inputTokens  * GPT_4O_MINI_INPUT_PER_TOKEN
                       + outputTokens * GPT_4O_MINI_OUTPUT_PER_TOKEN;

        meters.counter("llm.tokens", "model", model, "type", "input").increment(inputTokens);
        meters.counter("llm.tokens", "model", model, "type", "output").increment(outputTokens);
        Counter.builder("llm.cost.usd")
                .description("Estimated LLM spend derived from gen_ai token usage")
                .tag("model", model)
                .register(meters)
                .increment(costUsd);

        if (log.isDebugEnabled()) {
            log.debug("[LlmCost] model={} in={} out={} costUsd={}",
                    model, inputTokens, outputTokens, costUsd);
        }
    }

    private long readLong(Observation.Context context, String key) {
        KeyValue kv = find(context, key);
        if (kv == null) return 0L;
        try {
            return Long.parseLong(kv.getValue().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String readString(Observation.Context context, String key, String fallback) {
        KeyValue kv = find(context, key);
        return kv == null ? fallback : kv.getValue();
    }

    private KeyValue find(Observation.Context context, String key) {
        for (KeyValue kv : context.getAllKeyValues()) {
            if (kv.getKey().equals(key)) return kv;
        }
        return null;
    }
}
