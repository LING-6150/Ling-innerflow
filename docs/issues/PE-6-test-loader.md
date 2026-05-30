# PE-6 — Test: PatternDefinitionLoader

> Lane C. Independent — runs any time.
> Tracked by **#PE-0**.

## Why

Lock the closed-set taxonomy invariant. If the loader silently accepts 11 or 13 keys, or
an unknown domain, the entire detection layer's assumptions break.

## Scope

- `src/test/java/com/ling/linginnerflow/pattern/definition/PatternDefinitionLoaderTest.java`
- Optionally a `src/test/resources/patterns-invalid/` for fixture YAMLs.

No main-source changes.

## Build

Tests to write (JUnit 5, Spring `@SpringBootTest` is overkill — use direct construction
or a minimal `@ContextConfiguration` if needed):

1. **`loads_all_twelve_keys`** — real loader against `src/main/resources/patterns/*.yaml`
   returns exactly 12, and the set of keys equals the V1 product spec §7 closed set
   (read it from a constant the test owns; cross-check with the engine's `Domain` enum
   for sanity).
2. **`all_primary_domains_are_valid`** — every loaded `primary_domain` is one of the 6
   `Domain` enum values.
3. **`all_also_in_domains_are_valid`** — every entry in `also_in` is a valid `Domain`.
4. **`hyde_exemplars_present_and_positive`** — every YAML declares `hyde_exemplars >= 1`.
5. **`lexical_cues_non_empty`** — every YAML has `>= 3` cues (else B1 baseline can't work).
6. **`fails_fast_on_unknown_domain`** — point loader at a fixture dir containing a single
   YAML with `primary_domain: chimera` → expect `IllegalStateException` at `@PostConstruct`.
7. **`fails_fast_on_wrong_count`** — fixture dir with 5 YAMLs → expect throw with a clear
   message mentioning "12".
8. **`fails_fast_on_duplicate_key`** — fixture dir with two files declaring the same
   `pattern_key` → expect throw.

## Verification

- `./mvnw -q -Dtest='PatternDefinitionLoaderTest' test` green.
- Test execution time < 2s (no Spring context, no DB).

## Out of scope

- Testing the YAML *content semantics* (e.g. "is `people_pleasing.neutral_description`
  good?"). Content review is a doc concern, not a unit test.

---

## Drop-in prompt

```
Write the PatternDefinitionLoader unit tests in worktree .claude/worktrees/pe-6-test-loader.
Branch: feature/pe-6-test-loader (from epic/pattern-engine-v1.2).

READ FIRST:
  - docs/issues/PE-6-test-loader.md
  - src/main/java/com/ling/linginnerflow/pattern/definition/PatternDefinitionLoader.java
  - One of src/main/resources/patterns/people_pleasing.yaml (for the schema)

CONSTRAINTS:
  - Touch ONLY src/test/java/com/ling/linginnerflow/pattern/definition/ and
    src/test/resources/patterns-invalid/.
  - JUnit 5 (org.junit.jupiter). No Spring context unless strictly required.
  - Offline. Fast (< 2s total).
  - Closed set of 12 pattern_keys — encode it as a test-owned constant; do not import from main.

DELIVERABLES:
  - PatternDefinitionLoaderTest.java with the 8 tests listed in the issue
  - 3 small fixture YAML files for fail-fast tests under src/test/resources/

VERIFY:
  ./mvnw -q -Dtest='PatternDefinitionLoaderTest' test

REPORT and DO NOT push.
```
