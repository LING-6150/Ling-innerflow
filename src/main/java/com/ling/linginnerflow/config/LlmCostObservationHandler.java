package com.ling.linginnerflow.config;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LlmCostObservationHandler implements ObservationHandler<Observation.Context> {

    private static final String INPUT_TOKENS = "gen_ai.usage.input_tokens";
    private static final String OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    private static final String REQUEST_MODEL = "gen_ai.request.model";
    private static final String RESPONSE_MODEL = "gen_ai.response.model";

    private final MeterRegistry meterRegistry;

    public LlmCostObservationHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onStop(Observation.Context context) {
        long inputTokens = readLong(context, INPUT_TOKENS);
        long outputTokens = readLong(context, OUTPUT_TOKENS);
        if (inputTokens == 0 && outputTokens == 0) {
            return;
        }

        String model = readString(context, RESPONSE_MODEL,
                readString(context, REQUEST_MODEL, "unknown"));

        meterRegistry.counter("llm.tokens", "model", model, "type", "input")
                .increment(inputTokens);
        meterRegistry.counter("llm.tokens", "model", model, "type", "output")
                .increment(outputTokens);

        Double costUsd = estimateCostUsd(model, inputTokens, outputTokens);
        if (costUsd != null) {
            Counter.builder("llm.cost.usd")
                    .description("Estimated LLM spend derived from gen_ai token usage")
                    .tag("model", model)
                    .register(meterRegistry)
                    .increment(costUsd);
        }

        log.debug("[Observability] LLM usage model={} inputTokens={} outputTokens={} costUsd={}",
                model, inputTokens, outputTokens, costUsd);
    }

    private Double estimateCostUsd(String model, long inputTokens, long outputTokens) {
        if (!"gpt-4o-mini".equals(model)) {
            return null;
        }
        double inputCost = inputTokens * (0.15 / 1_000_000.0);
        double outputCost = outputTokens * (0.60 / 1_000_000.0);
        return inputCost + outputCost;
    }

    private long readLong(Observation.Context context, String key) {
        KeyValue keyValue = find(context, key);
        if (keyValue == null) {
            return 0L;
        }
        try {
            return Long.parseLong(keyValue.getValue());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String readString(Observation.Context context, String key, String fallback) {
        KeyValue keyValue = find(context, key);
        return keyValue == null || keyValue.getValue().isBlank()
                ? fallback
                : keyValue.getValue();
    }

    private KeyValue find(Observation.Context context, String key) {
        for (KeyValue keyValue : context.getAllKeyValues()) {
            if (key.equals(keyValue.getKey())) {
                return keyValue;
            }
        }
        return null;
    }
}
