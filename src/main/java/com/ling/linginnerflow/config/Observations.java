package com.ling.linginnerflow.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Observations {

    private final ObservationRegistry observationRegistry;

    public void tagPrompt(String promptId, String promptVersion) {
        Observation current = observationRegistry.getCurrentObservation();
        if (current == null) {
            return;
        }
        current.lowCardinalityKeyValue("prompt.id", promptId);
        current.lowCardinalityKeyValue("prompt.version", promptVersion);
    }
}
