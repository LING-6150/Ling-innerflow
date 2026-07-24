# M0 B-summary fidelity contract

Reference implementation: Java memory code at `8a8aa693` (the protocol’s
pre-registered reference), including the later atomic prefix-splice correction
that is already part of `main`.

| Production behavior | M0 replica | Deterministic evidence |
|---|---|---|
| Add every role/content/timestamp to short memory | `FaithfulSummaryPolicy.add_message` | 19/20-message boundary test |
| Trigger at 10 rounds (20 messages) | threshold fixed to `10 * 2` | 19 does not call the model; 20 does |
| Keep 4 rounds (8 raw messages) | `keep_recent_rounds=4` | exact retained-tail assertion |
| Summarize the older prefix with the production prompt | `summary_prompt` | prompt source frozen in run manifest |
| Write `[Conversation summary]` as a `system` message | compression splice | summary shape and `AI:` rendering assertions |
| Preserve a compatible concurrent suffix; skip an incompatible prefix | `atomic_summary_splice` | compatible-append and rewritten-prefix tests |
| Permit recursive sliding-window compression | settled synchronous oracle of async production behavior | 31-message recursive test |
| Persist latest summary and increment compression count only after an applied write | `WikiState` side effects | applied compression assertions |
| First Wiki extraction iff emotion pattern, core struggles, and triggers are empty | `_is_first_session` | first/merge call sequence test |
| Merge prompt sees only four text fields, triggers, progress notes, and new conversation | `_wiki_prompt_text` | allowlist assertion |
| `new` trigger semantic dedup at cosine `> 0.88`; on embedding failure append | `_find_similar_trigger` | embedding backend is frozen in run manifest |
| `increment` / `remove` use exact case-insensitive matching | `_merge_triggers` | paraphrase no-op regression assertion |
| Trigger score is `min(1,count/5) * exp(-days/90)`, two-decimal Java rounding; confirmed is 1 | `compute_score` | context score assertion |
| Reflection sees only emotion pattern, core struggles, effective coping, and latest compression summary | `generate_reflection` | reflection input allowlist assertion |
| Probe context uses the production field/order/filter/window contract | `build_context` | allowlist, trigger threshold, and system-role assertions |
| Session end compiles Wiki, generates reflection, then clears short memory | `end_session` | two-session merge test |

## Deliberate execution equivalences

- Production compression is asynchronous. M0 waits for quiescence after every
  write so the probe observes a settled state; it does not change the sliding
  window or prefix-splice result.
- Redis/MySQL are represented as in-process state. M0 evaluates semantic memory
  behavior, while the production Lua itself remains covered by the real-Redis
  integration test from PR #84.
- Generated-text provenance is conservative container-level attribution: a
  summary or Wiki field receives the source IDs of the input container. It is
  not presented as claim-level causal provenance.
- B-summary intentionally retains the known exact-match `increment`/`remove`
  defect. Reports must not generalize failures exposed by that defect into a
  claim that summary compression is intrinsically incapable of lifecycle
  handling.

## Controls

- `B-full` renders every frozen source event in timestamp order and makes no
  summary/Wiki formation calls. It is a diagnostic ceiling, not a
  budget-matched competitor.
- `B-none` passes no persistent-memory context. It isolates probes answerable
  from the current turn or forced-choice priors alone.
- All three policies use the same response prompt, model, temperature, and
  token limit. Only their memory context differs.
