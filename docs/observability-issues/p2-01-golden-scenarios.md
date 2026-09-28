[P2-01] Golden conversation eval set (L1–L5 + tool-triggering)

**Phase:** 2 · **Est:** ~1 day · **Risk:** Low · **Labels:** eval, benchmark

## Why
The working tree has no golden dataset or A/B harness (only mocked unit tests; eval lives on unmerged branches). The benchmark needs a fixed, versioned input set both runtimes can be driven through.

## Scope
- A versioned dataset (JSON/YAML under `src/test/resources` or `docs/`) of conversations covering emotion L1–L5, multi-turn escalating/de-escalating trajectories, and inputs that trigger each tool (EmotionTrendAnalyzer, HistoryContextRetriever, CBTSkillLibrary, WellnessResourceSearch).
- Each scenario tagged with `scenario.id` and expected route/level for sanity checks.

## Acceptance
- Dataset loads in a test and enumerates N scenarios with stable IDs.
