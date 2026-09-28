[P2-05] (Stretch) External LangGraph (Python) reference target

**Phase:** 2 · **Est:** ~2 days · **Risk:** Med · **Labels:** eval, benchmark, stretch

## Why
LangGraph4j is JVM-side. An industry-standard Python LangGraph equivalent gives an external reference point and a stronger résumé claim ("benchmarked against reference LangGraph").

## Scope
- A minimal Python LangGraph reimplementation of the analyzer→planner→Lx graph behind an HTTP shim.
- Drive it through the same golden scenarios (P2-01) via the harness (P2-02), OTLP-exported to the same backend.

## Acceptance
- Python LangGraph runs the golden set and produces traces comparable to Path A/B.

## Depends on
P2-02. **Optional / nice-to-have.**
