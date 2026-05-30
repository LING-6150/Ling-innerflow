# Postmortem 001 — The Epic Compile Break

> Date: 2026-05-30
> Severity: P1 (blocked Epic→main merge, blocked V1 eval run)
> Time-to-detect: ~2 hours after first symptom (false healthy signal)
> Time-to-fix: ~45 minutes once root cause was isolated
> Recovery: PR #22

---

## 1. What happened

We were running a **multi-agent parallel implementation** of Pattern Engine V1.2
across 10 GitHub issues. Each sub-issue had its own git worktree, its own
feature branch off `epic/pattern-engine-v1.2`, and was assigned to either
Codex or Claude (via separate terminals).

The wave of PRs went like this:

```
PR #12  PE-1  eval harness            merged into epic
PR #13  PE-6  loader tests            merged into epic
PR #14  PE-7  confidence tests        merged into epic
PR #15  PE-9  dedup tests             merged into epic
PR #16  PE-2  baseline B0             merged into epic   ← contains Baseline.java
PR #18  PE-5  baseline B3             merged into epic   ← contains Baseline.java + stray duplicates
PR #20  PE-3  baseline B1             merged into epic   ← contains Baseline.java
PR #21  PE-10 Tier A-H personas       merged into epic
PR #17  PE-4  baseline B2             FAILED to merge — conflicts on Baseline.java
```

When PR #17 (B2) tried to rebase, GitHub reported conflicts. Initial inspection
said "just `Baseline.java` differs across branches; rebase and pick the right
one." That was **correct on the surface and incomplete underneath.**

After resolving the surface conflict and running `./mvnw test-compile` on the
rebased branch, we got **17 unrelated-looking errors**:

```
[ERROR] Baseline.java:[8,8] 类重复: com.ling.linginnerflow.pattern.eval.baseline.Baseline
[ERROR] RedisDefenseServiceTest.java:[125,26] 找不到符号: 方法 getName()
[ERROR] GroundTruthLoader.java:[62,17] 找不到符号: 变量 log
[ERROR] B0_PriorBaseline.java:[18,8] 未覆盖 Baseline 中的 predict()
[ERROR] B1_LexicalBaseline.java:[24,8] 未覆盖 Baseline 中的 predict()
[ERROR] B2_SinglePromptBaseline.java:[22,8] 未覆盖 Baseline 中的 predict()
... etc
```

These looked like a Lombok / annotation-processor problem. They weren't.

---

## 2. The actual root cause

After bisecting the baseline files (removing them one by one to find which
file broke the build), we found:

> **`B3_RetrievalNoVerifyBaseline.java` contained four stray top-level
> declarations appended at the end of the file:**
>
> ```java
> // ... B3 class proper ...
> }
>
> interface Baseline {              // ← duplicates pattern.eval.baseline.Baseline
>     Set<PredictedPattern> predict(GTPersona persona);
>     String name();
> }
>
> record GTPersona(String id, List<CorpusRecord> corpus) { }   // ← shadows real GTPersona (6 fields)
> record CorpusRecord(LocalDate date, String type, String text) { }
> record PredictedPattern(String patternKey, String domain) { }   // ← shadows real PredictedPattern (uses Domain enum)
> ```

These were **placeholder type definitions Codex wrote so `B3` would compile
standalone in its own worktree** (before PE-1's real types existed on the
shared branch). Codex never removed them after PE-1 landed.

That one file, sitting harmlessly through PR #18's merge, then caused:

| Visible symptom | Real cause |
|---|---|
| `class duplicate Baseline` | Two top-level `Baseline` declarations in same package |
| `GroundTruthLoader: 找不到 log` | Lombok `@Slf4j` not expanding — but **only** because compilation failed elsewhere first, so Lombok's annotation-processor pass never ran on later files |
| `RedisDefenseServiceTest: 找不到 getName()` | Same root: `@Data` never expanded after the early failure |
| `B0/B1/B2 implements Baseline but didn't override predict()` | They were resolving against the wrong (shadow) `Baseline` declaration |

All 17 errors collapsed into **one** real bug.

---

## 3. Why we didn't catch it earlier

This is the part worth remembering.

### 3.1 Every individual PR's tests passed — in isolation

Before each PR was opened, we ran:

```bash
./mvnw -q -Dtest='com.ling.linginnerflow.pattern.eval.baseline.B3_*' test
# → 4 tests, 0 failures   ← GREEN
```

That command ran inside the **PE-5 worktree**, where:
- `B0` didn't exist (PE-2 hadn't merged yet)
- `B1` didn't exist (PE-3 hadn't merged yet)
- `B2` didn't exist (PE-4 was still being written)

So B3's stray `Baseline` interface had nothing to clash with. **The bug was
invisible until two baseline PRs merged into the same branch.**

This is the textbook **"each PR green, integration red"** failure mode. It's
exactly the failure mode CI exists to catch — and our repo didn't have CI
on PR merges.

### 3.2 GitHub auto-merge bypassed the only safety net

GitHub's branch protection / required-check settings can force CI to run
before a PR can merge. Our repo had **no required checks on the epic
branch**, so each PR squash-merged the moment we clicked the button. The
post-merge epic was never compiled by anything until we tried PR #17.

### 3.3 The first error message lied (technically true, semantically misleading)

`类重复: Baseline` is a real, accurate javac message. But the human
debugger reads it as "two `Baseline.java` files somewhere" — and looks for
duplicate files on disk. There weren't any. The duplicate was an
*embedded type* inside an unrelated file. javac never tells you which
*other* declaration site shadowed yours.

Lesson: when a "duplicate class" error fires and `find -name '*.java'`
shows only one file, the duplicate is almost certainly an inner/nested/
trailing declaration in another source file. Grep for the type name
across the codebase, not the filename.

### 3.4 The downstream errors masked the upstream one

Once `Baseline` was poisoned, three more error families fired:
- `predict()` doesn't override → because the bad `Baseline` was being picked
- `@Slf4j` doesn't expand → because Lombok bailed after the first compile failure
- `@Data` getters missing → same Lombok bailout

Each of those, in isolation, would have sent us looking at Lombok config,
build tooling, or the test framework. We almost did. **The correct move
was to fix the first error and let the rest fall out — but in a 17-error
wall of red, "fix the first error" requires discipline, not instinct.**

---

## 4. What we should do differently

### 4.1 Required CI on the epic branch (the single most valuable change)

Add a minimum GitHub Actions workflow that runs `./mvnw test-compile` on
every PR targeting `epic/*` AND on every push to `epic/*`. Make it a
required check before merge.

```yaml
# .github/workflows/epic-ci.yml
on:
  pull_request:
    branches: [ 'epic/**' ]
  push:
    branches: [ 'epic/**' ]

jobs:
  compile-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - run: ./mvnw -B -DskipTests test-compile
      - run: ./mvnw -B -Dtest='com.ling.linginnerflow.pattern.**' test
```

This would have caught the B3 stray-declaration bug **the moment PR #18
merged**, not 4 PRs later.

### 4.2 Forbid placeholder type declarations in scope discipline

The `Drop-in prompt` block in every sub-issue already says
"Touch ONLY files declared in `Scope`." It needs one more clause:

> **Never declare top-level types whose name matches a type defined elsewhere
> in the repo, even temporarily. If a class you depend on doesn't exist yet,
> stop and ask — don't shim it.**

This would have prevented Codex from appending the placeholder `Baseline`
to its B3 file in the first place.

### 4.3 Always test against the merged base, not the worktree base

Before opening any PR, run the test command from inside an **ephemeral
worktree checked out on the latest `origin/epic`** with your changes
cherry-picked on top. Not the per-issue worktree. This catches integration
breaks one PR at a time, not 4 PRs in.

Cheap shell trick (could go into `spawn-worktree.sh` as a `verify` subcommand):

```bash
git worktree add /tmp/verify origin/epic/pattern-engine-v1.2
cd /tmp/verify
git cherry-pick <current-feature-branch>
./mvnw -DskipTests test-compile && ./mvnw -Dtest='...**' test
cd - && git worktree remove --force /tmp/verify
```

### 4.4 Read the FIRST javac error, ignore the rest, then re-run

This is a process rule, not a code rule. When javac dumps 17 errors:
1. Read the first ERROR line ONLY.
2. Form a hypothesis about that specific error.
3. Fix the hypothesis, recompile.
4. Repeat with the new first error.

Do **not** scan the wall of errors looking for patterns — you'll see
patterns that don't exist (we saw "Lombok problem" when there was no
Lombok problem).

### 4.5 If you have multi-agent parallel work, the contracts must be defined ONCE in the foundation phase

This is the meta-lesson of the whole V1 build:

> **In parallel multi-agent development, anything multiple agents
> independently define will diverge. The only types that survive merge are
> the ones defined by ONE agent in a foundation phase that everyone else
> branches off.**

The Pattern Engine `Baseline` interface should have been part of PE-1's
eval-harness foundation (PR #12), not invented separately by each
baseline PR. Three baselines wrote three slightly-different `Baseline`
interfaces. The semantic-merge problem was inevitable.

This is the same lesson as **"shared types belong in a foundation
package"** in any monorepo, just amplified by parallel AI agents who
can't coordinate verbally with each other.

---

## 5. What this actually demonstrated

The bug felt like a setback. It is in fact one of the most valuable artifacts
in this project, because it shows three things:

1. **You can run a real multi-agent parallel workflow** — 10 issues, multiple
   model families, separate worktrees, structured PR flow.
2. **Integration always finds the joints, no matter how clean each piece
   looks.** This is true of human teams too; it's not an AI-specific
   problem. The fix is engineering process (CI, contracts), not
   "better prompts."
3. **A working postmortem is more credible than an unbroken commit
   history.** If the GitHub history were perfectly green from PR #12
   to #21, a senior reviewer would assume either (a) the work was
   trivial, or (b) someone hid the breaks. The B3 bug + this writeup
   shows the project is real.

---

## 6. Concrete follow-ups (low-cost, high-leverage)

- [ ] Add `.github/workflows/epic-ci.yml` (§4.1) before opening any V2 Epic.
- [ ] Update `docs/issues/README.md` rule #1 with the "no placeholder
      type declarations" clause from §4.2.
- [ ] Add a `verify-against-epic` subcommand to `spawn-worktree.sh`
      (§4.3) so every sub-issue can self-check before pushing.
- [ ] In the next Epic, foundation PR explicitly defines all shared
      interfaces (the "contracts phase"), and downstream agents are
      forbidden from re-declaring them.

---

*This document covers a real bug in `worktree-pattern-engine-v1`, fixed by
PR #22 on 2026-05-30. The root-cause file was* `src/test/java/com/ling/linginnerflow/pattern/eval/baseline/B3_RetrievalNoVerifyBaseline.java` *and the four stray top-level declarations at its tail.*
