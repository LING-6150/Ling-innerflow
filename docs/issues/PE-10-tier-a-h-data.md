# PE-10 — Tier A-H human held-out personas (≥ 5)

> Lane D. Independent. **HUMAN ONLY — no AI may touch this.**
> Tracked by **#PE-0**.

## Why

Tier A-H is the only style-agnostic ground truth in the entire eval. Its whole purpose is
to falsify confounds that LLM-generated Tier A cannot detect (V1.2 R4, R5, R36). The
moment an LLM writes a sentence in this set, the set's value goes to zero — it stops being
a confound-detector and becomes another LLM-style sample.

This is also the **only** issue that cannot be parallelized with an AI. It is yours alone.

## Scope

- `eval/groundtruth/sealed/` — fill in human personas:
  - You will write **at least 5** personas (target 8–10).
  - File naming: `ah-01.answerkey.yaml` + `ah-01.corpus.md`, `ah-02.*`, …

You may use the existing `ah-01.*` skeleton as a starting structural example, but you must
replace every `<...>` placeholder with your own human-authored content.

## How to do it

The full instructions are already in `eval/groundtruth/sealed/README.md`. The 5 authoring
rules summarized:

1. **Symptoms, not labels.** Write what the person actually said or felt in a real-feeling
   scene — never "I show people-pleasing tendencies."
2. **At least one strongly quotable line per true pattern.** This tests the verbatim
   invariant (the engine must be able to quote it back exactly).
3. **Mandatory hard-negative decoy.** Include content that *looks* like a pattern but is
   a one-off, not recurrence. This is the most valuable part — without decoys, a "yes to
   everything" detector scores well.
4. **Spread evidence across time (cross-month).** Not clustered in one week. Real patterns
   are months-long.
5. **(Optional but recommended) 1–2 crisis-language lines.** Topically pattern-relevant.
   Tests that the engine NEVER cites them as evidence (product §17 hard fail).

## Verification (self-check before closing the issue)

- [ ] At least 5 personas with both `<id>.answerkey.yaml` and `<id>.corpus.md`.
- [ ] Each persona's corpus has ≥ 15 records (chat/journal/checkin lines).
- [ ] Each persona's corpus spans **at least 2 calendar months**.
- [ ] Each `true_pattern` has at least one identifiable "quotable line" in the corpus.
- [ ] Each persona has at least one `decoy_patterns` entry with a `why_not` explanation.
- [ ] Every `pattern_key` and `domain` referenced is in the closed set (see README).
- [ ] **You authored every line of corpus text yourself.** No AI involvement.
- [ ] `eval/groundtruth/sealed/MANIFEST.md` lists each persona and its `true_patterns`,
      `decoys`, `has_crisis_seed`, and a short `notes` column.

## Out of scope

- Implementing any harness or loader changes — PE-1 covers that.
- Generating personas with a prompt — explicitly forbidden by R4/R5/R36.

---

## "Drop-in prompt"

There is no AI prompt for this issue. It is human work. If you find yourself wanting to
ask an LLM "write me a Tier A-H persona," stop — that defeats the entire point of the
held-out set and silently breaks the eval methodology you committed to.

If you want help **organizing** what to write (e.g. "I have 30 minutes; which patterns
should I prioritize?"), that is fine and not LLM-touch — ping the operator out-of-band.

---

## Tip: a realistic working pace

- 1 persona ≈ 20–40 minutes if you sketch first (true patterns, decoy, 3 scenes), then
  write.
- 5 personas in one sitting is ambitious; 1–2 per day across a week is easier and gives
  better variety.
- Re-read after a day's pause — that's how you catch lines that secretly sound like a
  label rather than a symptom.
