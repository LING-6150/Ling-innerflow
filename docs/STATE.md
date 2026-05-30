# InnerFlow — Current State

> Last updated: 2026-05-30
> Read this BEFORE asking the user "where are we?"
>
> If you are an AI assistant (Codex / Claude / etc.) being asked to help on
> this project, READ THIS FILE FIRST. Everything you need to know to continue
> is here.

---

## What this project is

**InnerFlow** is an AI Engineering portfolio project. It's a Spring Boot +
Vue 3 system that tries to identify recurring psychological patterns in a
user's accumulated text (chat / journal / check-ins) and present them with
evidence the user can confirm or reject.

The system is positioned as a **mirror, not a therapist** — it surfaces
patterns from the user's own data; it does not interpret causes.

---

## What's done

### Pattern Engine V1.2 (merged to main on 2026-05-30)

A complete, compiling, unit-tested implementation:

- **Backend** (`src/main/java/com/ling/linginnerflow/pattern/`):
  domain enums, JPA entities (`PatternInstance`, `EvidenceChain`,
  `EvidenceItem`), 12 `PatternDefinition` YAMLs + loader, S0 corpus
  assembly, S2/S3 HyDE-exemplar retrieval, S4 verification (BATCH /
  SINGLE_ITEM whole-system switch — V1.2 R16′), confidence scoring
  (no LLM `strength` term — V1.2 R13), `0.88` cosine dedup, language
  firewall, S0–S9 orchestrator, REST controller, nightly scheduler.

- **Eval ground truth**:
  - `eval/groundtruth/tierA/` — 6 Claude-authored synthetic personas
    (`a-01`..`a-06`)
  - `eval/groundtruth/sealed/` — 5 human-authored personas
    (`ah-02`..`ah-06`); `ah-05` and `ah-06` are **FULL-DECOY** personas
    (no `true_patterns` by design — anti-pattern test)

- **Eval harness**: `GroundTruthLoader`, `MetricsCalculator` (R40
  recall-retention), 4 baselines (B0/B1/B2/B3), 60 unit tests.

- **Documents** (`docs/`):
  - `superpowers/specs/2026-05-29-pattern-engine-v1.2.md` — FINAL engine spec
  - `superpowers/specs/2026-05-29-pattern-discovery-v1-design.md` — product spec
  - `postmortems/001-epic-compile-break.md` — postmortem of a real bug
  - `issues/` — Epic + 10 sub-issue specs + operations manual

### V1 Validation (LIVE) — completed 2026-05-30

Branch: `feature/v1-validate`. Two reports:
`eval/RESULTS_LIVE.md` and `eval/RESULTS_DECOY.md`.

This is **the most important artifact in the project right now**. Read these
two files before doing anything else.

---

## The hard truth from V1 validation (READ THIS)

The V1 pipeline was tested against all 11 personas using live OpenAI calls
(`gpt-4o-mini` chat + `text-embedding-3-small`). Total cost: **\$0.025**,
total runtime ~11 minutes.

Three things were measured. All three results were unfavorable:

### RQ1 — Does Full beat baselines?

NO. Full pipeline underperforms B2 (a dumb single-prompt LLM baseline)
on BOTH tiers, while being 10x slower and 4x more expensive.

| | Tier A F1 | Tier A-H F1 |
|---|---:|---:|
| B0 (chance) | 0.111 | 0.050 |
| B1 (lexical) | 0.000 | 0.000 |
| **B2 (single prompt)** | **0.386** | **0.359** |
| **Full pipeline** | **0.111** | **0.000** |

### RQ2 — Does verification add value?

NO. Removing the §4 verification stage **improves** F1 on Tier A from
0.111 to 0.333 (Δ +0.222) and uses **half** the tokens.

The V1.2 R3 design hypothesis ("verifier raises precision enough to be
worth its cost") is rejected by this data.

### RQ3 — Does the system abstain on full-decoy personas?

PARTIAL. `ah-05` correctly surfaced 0 patterns. `ah-06` (NPD persona)
surfaced **11 patterns**, **8 of them with confidence=1.000**.

The LLM-generated interpretations look reasonable in isolation
("This reflects a tendency to avoid confrontation through silence")
but every single one is mislabeling someone whose persona key explicitly
says "system should not label this."

This is the **most dangerous failure mode** for a mirror product.

---

## What this means for V2

V2 direction is now **constrained by data**, not speculation.

**OFF the table:**
- Pattern Structure (the V2 brainstorm direction from earlier in this
  project): cannot be built on top of an evidence chain whose underlying
  pipeline has F1=0 on human prose. Pattern Structure would be a noise
  amplifier.
- Joint calibration: single-pattern confidences are already wrong
  (0.900-1.000 on false positives); joint structure cannot rescue them.

**ON the table (single direction):**
- **OOD / abstain capability**. The ah-06 failure mode IS the V2 problem.
  The system needs to learn to refuse to label rather than confidently
  mislabel. This is also the same fix that would address the verifier-hurts
  finding — the precision gain from "say nothing when uncertain" addresses
  RQ2 by a different route than "verify before saying."

V2 is therefore reframed as:

> "Turn V1 from a system that confidently confabulates into a system that
> reliably abstains."

---

## What you should do RIGHT NOW (depending on who you are)

### If you are **the user (apple / project owner)** returning to the project:

1. Read `eval/RESULTS_LIVE.md` and `eval/RESULTS_DECOY.md` (~25 min).
2. Sleep on it. Do not make V2 spec decisions while the validation is
   fresh — it's emotionally loaded data.
3. When you come back: write `docs/superpowers/specs/2026-05-30-pattern-engine-v2-direction.md`
   capturing the OOD/abstain pivot.

### If you are **an AI assistant** (Codex / Claude / etc.) the user just opened:

1. Read this file completely (you're doing it).
2. Read `eval/RESULTS_LIVE.md` and `eval/RESULTS_DECOY.md`.
3. **Do NOT propose Pattern Structure / Joint Calibration / new
   architecture rewrites** — that V2 brainstorm is invalidated by the data.
4. **Do NOT propose more eval set expansion as the primary V2 task** —
   the eval already exposed the failure mode; more personas won't change the
   diagnosis.
5. **Do** help with: OOD detection design, abstain mechanism design,
   re-running validation after changes, V2 spec writing.

---

## Where to read more

| What | Where |
|---|---|
| V1 final engine spec | `docs/superpowers/specs/2026-05-29-pattern-engine-v1.2.md` |
| V1 product spec | `docs/superpowers/specs/2026-05-29-pattern-discovery-v1-design.md` |
| LIVE validation results | `eval/RESULTS_LIVE.md` + `eval/RESULTS_DECOY.md` |
| First postmortem | `docs/postmortems/001-epic-compile-break.md` |
| Operations manual | `docs/issues/README.md` |
| GitHub repo | https://github.com/LING-6150/Ling-innerflow |
| Branch this state was written on | `feature/v1-validate` |

---

## Key numbers (so you don't have to recompute)

- 12 pattern_keys (closed set, see `src/main/resources/patterns/*.yaml`)
- 6 domains: self, family, intimate, work, social, body
- 11 personas total (6 synthetic + 5 human; 2 of the human ones are full-decoy)
- 60 passing unit tests
- F1 of full pipeline on Tier A-H: **0.000**
- Cost of one LIVE eval pass over all 11 personas: **\$0.025**
- Runtime: ~11 minutes
- ah-06 false positives: **11**, of which **8 have confidence=1.000**

---

*If something here is contradicted by code, the code wins and this file is
stale — please update it.*
