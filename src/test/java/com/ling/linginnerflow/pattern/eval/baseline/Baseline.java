package com.ling.linginnerflow.pattern.eval.baseline;

import com.ling.linginnerflow.pattern.eval.GTPersona;
import com.ling.linginnerflow.pattern.eval.PredictedPattern;

import java.util.Set;

public interface Baseline {
    Set<PredictedPattern> predict(GTPersona persona);
}
