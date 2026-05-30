package com.ling.linginnerflow.pattern.eval;

import com.ling.linginnerflow.pattern.definition.PatternDefinitionLoader;
import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.eval.baseline.B0_PriorBaseline;
import com.ling.linginnerflow.pattern.eval.baseline.B1_LexicalBaseline;
import com.ling.linginnerflow.pattern.eval.baseline.Baseline;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * End-to-end V1 eval. Produces eval/RESULTS.md with B0/B1 against Tier A + Tier A-H.
 *
 * Runs offline (no network) — B2/B3 require live API and are excluded from
 * the headline report. B1 is the V1.2 R30 "headline floor" claim.
 *
 * Invoke with: -Dpattern.eval.run-v1=true
 */
@Tag("eval")
class V1EvalRunner {

    @Test
    void runV1Eval() throws Exception {
        if (!Boolean.getBoolean("pattern.eval.run-v1")) {
            return; // gated: only runs when explicitly requested
        }

        PatternDefinitionLoader loader = new PatternDefinitionLoader();
        loader.load();

        GroundTruthLoader gt = new GroundTruthLoader();
        List<GTPersona> tierA = gt.loadTierA();
        List<GTPersona> tierAH = gt.loadTierAH();

        Map<String, Double> baseRates = computeBaseRates(tierA);
        List<Baseline> baselines = List.of(
            new B0_PriorBaseline(42L, baseRates),
            new B1_LexicalBaseline(loader, 3)
        );

        MetricsCalculator calc = new MetricsCalculator();
        StringBuilder md = new StringBuilder();
        md.append("# Pattern Engine V1.2 — End-to-End Eval Results\n\n");
        md.append("Generated: ").append(java.time.LocalDate.now()).append("\n\n");
        md.append("Scope: B0 (chance) and B1 (lexical floor) only. B2/B3 require live API.\n");
        md.append("Engine pipeline (full S0-S9) requires DB + LLM; reported separately when run.\n\n");

        // Tier A results
        md.append("## Tier A (synthetic, Claude-authored — V1.2 R1 cross-model)\n\n");
        md.append("Personas: ").append(tierA.size()).append(" (").append(joinIds(tierA)).append(")\n\n");
        md.append(renderTable(baselines, tierA, calc));

        // Tier A-H results
        md.append("## Tier A-H (human-authored, sealed — V1.2 R5/R30)\n\n");
        md.append("Personas: ").append(tierAH.size()).append(" (").append(joinIds(tierAH)).append(")\n\n");
        md.append("Note: ah-05 and ah-06 are FULL-DECOY personas (no true_patterns by design — anti-pattern test).\n\n");
        md.append(renderTable(baselines, tierAH, calc));

        md.append("## Synthetic ↔ Human gap (V1.2 R8 honesty metric)\n\n");
        md.append(renderGap(baselines, tierA, tierAH, calc));

        Path out = Path.of("eval/RESULTS.md");
        Files.createDirectories(out.getParent());
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.print(md);
        }
        System.out.println("RESULTS written to " + out.toAbsolutePath());
        System.out.println(md);
    }

    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Double> computeBaseRates(List<GTPersona> personas) {
        Map<String, Long> counts = new HashMap<>();
        long total = personas.size();
        for (GTPersona p : personas) {
            Set<String> keys = new HashSet<>();
            for (GTLabel l : p.truePatterns()) keys.add(l.patternKey());
            for (String k : keys) counts.merge(k, 1L, Long::sum);
        }
        Map<String, Double> rates = new HashMap<>();
        counts.forEach((k, c) -> rates.put(k, c / (double) total));
        return rates;
    }

    private String joinIds(List<GTPersona> personas) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < personas.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(personas.get(i).id());
        }
        return sb.toString();
    }

    private String renderTable(List<Baseline> baselines, List<GTPersona> personas, MetricsCalculator calc) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Baseline | Precision | Recall | F1 | Hard-Neg FPR |\n");
        sb.append("|---|---:|---:|---:|---:|\n");
        for (Baseline b : baselines) {
            double sumP = 0, sumR = 0, sumF1 = 0, sumFPR = 0;
            int n = 0;
            for (GTPersona p : personas) {
                Set<PredictedPattern> pred;
                try {
                    pred = b.predict(p);
                } catch (Exception e) {
                    pred = Set.of();
                }
                MetricReport r = calc.score(pred, p);
                sumP += r.precision();
                sumR += r.recall();
                sumF1 += r.f1();
                sumFPR += r.hardNegativeFPR();
                n++;
            }
            sb.append("| ").append(b.name())
              .append(" | ").append(fmt(sumP / n))
              .append(" | ").append(fmt(sumR / n))
              .append(" | ").append(fmt(sumF1 / n))
              .append(" | ").append(fmt(sumFPR / n))
              .append(" |\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String renderGap(List<Baseline> baselines, List<GTPersona> tierA, List<GTPersona> tierAH, MetricsCalculator calc) {
        StringBuilder sb = new StringBuilder();
        sb.append("| Baseline | F1 (Tier A) | F1 (Tier A-H) | Δ |\n");
        sb.append("|---|---:|---:|---:|\n");
        for (Baseline b : baselines) {
            double aF1 = avgF1(b, tierA, calc);
            double ahF1 = avgF1(b, tierAH, calc);
            String delta = String.format("%+.3f", ahF1 - aF1);
            sb.append("| ").append(b.name())
              .append(" | ").append(fmt(aF1))
              .append(" | ").append(fmt(ahF1))
              .append(" | ").append(delta).append(" |\n");
        }
        sb.append("\nA strong negative Δ on B1 means the human-text floor is harder than synthetic — expected.\n");
        sb.append("This delta is the honesty metric: it must not be hidden behind aggregate numbers (V1.2 R8).\n\n");
        return sb.toString();
    }

    private double avgF1(Baseline b, List<GTPersona> personas, MetricsCalculator calc) {
        double sum = 0;
        for (GTPersona p : personas) {
            Set<PredictedPattern> pred;
            try { pred = b.predict(p); } catch (Exception e) { pred = Set.of(); }
            sum += calc.score(pred, p).f1();
        }
        return sum / personas.size();
    }

    private String fmt(double v) {
        return String.format("%.3f", v);
    }
}
