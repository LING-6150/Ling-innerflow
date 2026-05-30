package com.ling.linginnerflow.pattern.eval;

import java.time.LocalDate;

public record CorpusRecord(
        LocalDate date,
        String type,
        String text
) {
}
