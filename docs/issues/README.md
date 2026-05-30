# Pattern Engine V1.2 — Issue Operations Manual

This is the **operator's manual** for the Pattern Engine V1.2 finish-line work,
split into 1 Epic + 10 sub-issues. Read this once before opening any terminal.

---

## 1. Big picture

The Pattern Engine V1.2 implementation has 5 phases:

| Phase | Status |
|---|---|
| Phase 1 — Foundation (entities, 12 YAMLs, templates) | ✅ committed `0f034c2` |
| Phase 2 — Engine stages (corpus, retrieval, verify, scoring, dedup, firewall) | ✅ committed `0f034c2` + `43d79a9` |
| Phase 3 — Orchestration + API (discovery service, review, controller, scheduler) | ✅ committed `43d79a9` |
| **Phase 4 — Eval harness + baselines + R40** | ⏳ → **PE-1, PE-2, PE-3, PE-4, PE-5** |
| **Phase 5 — Unit tests + integration** | ⏳ → **PE-6, PE-7, PE-8, PE-9** |
| **Tier A-H human held-out data** | ⏳ → **PE-10 (human only, no AI)** |

The Epic is `PE-0`. All 10 sub-issues hang off it.

---

## 2. Branch model

```
main
 └─ epic/pattern-engine-v1.2          ← Epic integration branch (squash target)
     ├─ feature/pe-1-eval-harness
     ├─ feature/pe-2-baseline-b0
     ├─ feature/pe-3-baseline-b1
     ├─ feature/pe-4-baseline-b2
     ├─ feature/pe-5-baseline-b3
     ├─ feature/pe-6-test-loader
     ├─ feature/pe-7-test-confidence
     ├─ feature/pe-8-test-chain-assembler
     ├─ feature/pe-9-test-dedup
     └─ feature/pe-10-tier-a-h    (data only, human-authored)
```

- **Every sub-PR targets `epic/pattern-engine-v1.2`, NOT main.**
- When the Epic is green (all sub-PRs merged + epic CI passing), one final PR
  merges `epic/pattern-engine-v1.2` → `main`. That keeps `main` linear.

---

## 3. Worktree per sub-issue (zero conflict across terminals)

For each sub-issue you want to work on, spin up a dedicated worktree:

```bash
# from the main repo root
./docs/issues/spawn-worktree.sh pe-1-eval-harness
# creates: .claude/worktrees/pe-1-eval-harness
# branches from: epic/pattern-engine-v1.2
# you immediately cd into it
```

That worktree is its own checkout. Two terminals on two worktrees never collide
— git tracks each independently.

---

## 4. Dependency lanes (what can run in parallel)

```
                          PE-1  Eval harness  (BLOCKS lane B)
                          /                \
       Lane B (baselines): all depend on PE-1
       ┌──────┬──────┬──────┬──────┐
      PE-2   PE-3   PE-4   PE-5      ← any 4 in parallel after PE-1 lands
       └──────┴──────┴──────┴──────┘

       Lane C (unit tests): independent, always parallelizable
       ┌──────┬──────┬──────┬──────┐
      PE-6   PE-7   PE-8   PE-9
       └──────┴──────┴──────┴──────┘

       Lane D (data): independent, human-only
       PE-10  ← do this yourself, by hand, no AI
```

**Parallelism ceiling**:
- Day 1 (before PE-1 lands): up to 5 in parallel — PE-1, PE-6, PE-7, PE-8, PE-9, PE-10.
- Day 2 (PE-1 merged): up to 9 in parallel — PE-2..PE-5, PE-6..PE-9, PE-10.

---

## 5. Multi-terminal startup recipe

In each new terminal you open:

```bash
# 1. spawn worktree for the issue
cd /Users/apple/Documents/Codex/2026-05-29/Ling-innerflow
./docs/issues/spawn-worktree.sh pe-X-<slug>

# 2. open the issue spec
cat docs/issues/PE-X-*.md

# 3. paste the "Drop-in prompt" section into Codex/Claude
#    (it's self-contained — has scope, contracts, verification, output disciplines)

# 4. when the AI finishes, verify:
./mvnw -q -DskipTests compile           # must succeed
./mvnw -q -Dtest='com.ling.linginnerflow.pattern.**' test   # must pass

# 5. commit + push + open PR targeting epic/pattern-engine-v1.2
git add -A
git commit -m "feat(pattern): PE-X <subject>" -m "Closes #<issue-number>"
git push -u origin feature/pe-X-<slug>
gh pr create --base epic/pattern-engine-v1.2 --title "PE-X: <subject>" --body "Closes #<issue-number>"
```

---

## 6. Rules every sub-issue obeys

1. **Touch only the files declared in `Scope`.** No drive-by refactors.
2. **Never weaken an invariant test** — if a test fails because the invariant
   it protects is violated, fix the implementation, not the test.
3. **Keep tests offline.** Mock `ChatClient` / `EmbeddingModel`. No live API.
   Tests that genuinely need network must be `@Disabled` with a clear reason.
4. **Match existing code style.** Lombok `@Data` / `@Service` / `@Slf4j` /
   `@RequiredArgsConstructor`, jakarta.persistence, Spring AI imports — see
   `memory/MemoryService.java` for the canonical idiom.
5. **No diagnostic / clinical language** in user-facing strings (product §13).
6. **Crisis-flagged source docs may never appear as evidence.**
7. **Confidence formula has NO LLM strength term** (V1.2 R13). Don't reintroduce it.
8. **Verifier is BATCH or SINGLE_ITEM whole-system** (V1.2 R16′). No per-item
   shuffle-and-drop.

---

## 7. PE-10 special: human-only, no AI

PE-10 is the **Tier A-H human held-out persona set**. Its entire value is that
it is written by a human and never touched by any LLM (V1.2 R4, R5, R36). If
you feed it to Claude or Codex, you destroy its purpose and the eval becomes
circular.

The template + scaffolding is already at `eval/groundtruth/sealed/`. Open
`README.md` there for the 5 authoring rules; the issue body lists them too.

---

## 8. Quick map: issue → spec

| Issue | File | Lane | Depends on |
|---|---|---|---|
| **PE-0** Epic | `PE-0-epic.md` | — | — |
| PE-1 Eval harness | `PE-1-eval-harness.md` | A | — |
| PE-2 Baseline B0 | `PE-2-baseline-b0.md` | B | PE-1 |
| PE-3 Baseline B1 | `PE-3-baseline-b1.md` | B | PE-1 |
| PE-4 Baseline B2 | `PE-4-baseline-b2.md` | B | PE-1 |
| PE-5 Baseline B3 | `PE-5-baseline-b3.md` | B | PE-1 |
| PE-6 Test: Loader | `PE-6-test-loader.md` | C | — |
| PE-7 Test: Confidence | `PE-7-test-confidence.md` | C | — |
| PE-8 Test: ChainAssembler | `PE-8-test-chain-assembler.md` | C | — |
| PE-9 Test: Dedup | `PE-9-test-dedup.md` | C | — |
| PE-10 Tier A-H data | `PE-10-tier-a-h-data.md` | D | — (human) |

---

*End of Operations Manual.*
