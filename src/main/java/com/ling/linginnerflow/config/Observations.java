package com.ling.linginnerflow.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Small helper for stamping a {@code prompt.id} / {@code prompt.version} onto the
 * currently-active observation (→ span attributes).
 *
 * There is no prompt versioning in the codebase today; this introduces the
 * convention. Prompt builders (P1-03..05) call {@link #tagPrompt} from inside an
 * {@code @Observed} method so the tags land on that node/service span, making
 * prompt revisions comparable in the Phase-2 benchmark.
 *
 * No-op when called outside any observation scope.
 */
@Component
@RequiredArgsConstructor
public class Observations {

    private final ObservationRegistry registry;

    public void tagPrompt(String promptId, String version) {
        Observation current = registry.getCurrentObservation();
        if (current != null) {
            current.lowCardinalityKeyValue("prompt.id", promptId);
            current.lowCardinalityKeyValue("prompt.version", version);
        }
    }
}
