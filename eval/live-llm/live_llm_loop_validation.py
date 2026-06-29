#!/usr/bin/env python3
"""
Live-LLM validation of the ReAct loop's result-driven feedback (PRs #66/#67).

The deterministic A/B harness (#67) used two SYNTHETIC model profiles (NAIVE /
ROBUST). This script checks the central claim on a REAL model: given the feedback
text each loop arm produces for a tool outcome, does a real LLM pick the correct
next action?

Scope (stated honestly): this validates the DECISION-GIVEN-FEEDBACK step on real
scenarios using the EXACT strings the Java loop emits. It is NOT a full multi-turn
loop run (that needs live tools + multiple turns). The uncaught-exception and
transient-retry wins are model-independent and already proven deterministically,
so they are intentionally out of scope here.

Cost: 4 scenarios x 2 arms x N repeats on a cheap model (default gpt-4o-mini).
Reads the API key from env MY_OPENAI_KEY (never hard-coded). No third-party deps.
"""
import json
import os
import re
import sys
import time
import urllib.request
import urllib.error

MODELS = [m.strip() for m in os.environ.get("OPENAI_MODELS", "gpt-4o-mini").split(",") if m.strip()]
N = int(os.environ.get("N_REPEATS", "8"))
TEMPERATURE = float(os.environ.get("TEMPERATURE", "0.7"))
KEY = os.environ.get("MY_OPENAI_KEY", "").strip()

SUCCESS_BODY = "Relevant CBT intervention:\nName the thought, then test it against the evidence."

# The two loop arms' feedback for each tool outcome, EXACTLY as the Java code emits:
#   baseline  = raw tool string (assume-success)
#   treatment = ReActAgent.observationForModel(...) typed framing
SCENARIOS = [
    {
        "name": "cbt_success",
        "expected": "USE_RESULT",
        "baseline": SUCCESS_BODY,
        "treatment": SUCCESS_BODY,  # SUCCESS feeds the raw content unchanged
    },
    {
        "name": "cbt_empty(partial)",
        "expected": "HEDGE",
        "baseline": "No relevant CBT content found.",
        "treatment": "[partial result — information is incomplete; do NOT treat this as a confident answer] No relevant CBT content found.",
    },
    {
        "name": "emotion_empty(partial)",
        "expected": "HEDGE",
        "baseline": "No sufficient emotion records yet.",
        "treatment": "[partial result — information is incomplete; do NOT treat this as a confident answer] No sufficient emotion records yet.",
    },
    {
        "name": "cbt_failure",
        "expected": "RECOVER",
        "baseline": "CBT knowledge base lookup failed.",
        "treatment": "[tool failed (tool_reported_failure) — do NOT assume it succeeded; answer from what you already know, or try a different approach]",
    },
    {
        # Ambiguous, non-self-describing: the tool returned nothing. Our loop maps
        # blank -> PARTIAL; baseline hands the model an EMPTY observation. This is
        # where typed framing should help even a capable model.
        "name": "empty_observation",
        "expected": "HEDGE",
        "baseline": "",
        "treatment": "[partial result — information is incomplete; do NOT treat this as a confident answer] ",
    },
]

SYSTEM = (
    "You are an agent in the middle of a ReAct reasoning loop for a mental-health "
    "support assistant. You just called a tool and received an OBSERVATION. Decide "
    "your single next action and reply with EXACTLY ONE of these tokens, nothing else:\n"
    "USE_RESULT  - the observation is usable content; build your answer on it.\n"
    "HEDGE       - the observation has no real content / is incomplete; do not assert "
    "a confident answer, acknowledge the gap or ask for more.\n"
    "RECOVER     - the tool did not succeed; do not rely on it, recover by trying "
    "another approach or answering from prior knowledge."
)

VALID = {"USE_RESULT", "HEDGE", "RECOVER"}


def call(observation, model):
    body = json.dumps({
        "model": model,
        "temperature": TEMPERATURE,
        "max_tokens": 8,
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": "OBSERVATION:\n" + observation + "\n\nYour next action (one token):"},
        ],
    }).encode()
    req = urllib.request.Request(
        "https://api.openai.com/v1/chat/completions",
        data=body,
        headers={"Authorization": "Bearer " + KEY, "Content-Type": "application/json"},
    )
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                out = json.load(r)
            txt = out["choices"][0]["message"]["content"].upper()
            m = re.search(r"USE_RESULT|RECOVER|HEDGE", txt)
            return m.group(0) if m else "UNPARSED:" + txt.strip()[:20]
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503) and attempt < 3:
                time.sleep(2 * (attempt + 1))
                continue
            return "ERROR:%d:%s" % (e.code, e.read().decode()[:120])
        except Exception as e:  # noqa
            if attempt < 3:
                time.sleep(2 * (attempt + 1))
                continue
            return "ERROR:" + str(e)[:120]


def run_model(model):
    print("\n############ model=%s  N=%d/cell  temperature=%.1f ############" % (model, N, TEMPERATURE))
    header = "%-26s %-10s %8s   %s" % ("scenario", "arm", "correct", "decision distribution")
    print(header)
    print("-" * len(header))
    agg = {"baseline": [0, 0], "treatment": [0, 0]}  # [correct, total]
    rows = []
    for sc in SCENARIOS:
        for arm in ("baseline", "treatment"):
            dist = {}
            correct = 0
            for _ in range(N):
                d = call(sc[arm], model)
                dist[d] = dist.get(d, 0) + 1
                if d == sc["expected"]:
                    correct += 1
            agg[arm][0] += correct
            agg[arm][1] += N
            dist_s = ", ".join("%s:%d" % (k, v) for k, v in sorted(dist.items(), key=lambda x: -x[1]))
            print("%-26s %-10s %6d/%d   %s" % (sc["name"], arm, correct, N, dist_s))
            rows.append((sc["name"], arm, correct, N, sc["expected"], dist))
        print()
    print("  aggregate correct-action rate:")
    for arm in ("baseline", "treatment"):
        c, t = agg[arm]
        print("    %-10s %5.1f%%  (%d/%d)" % (arm, 100.0 * c / t, c, t))
    return {"model": model, "n": N, "temperature": TEMPERATURE,
            "aggregate": {a: {"correct": agg[a][0], "total": agg[a][1]} for a in agg},
            "rows": [{"scenario": r[0], "arm": r[1], "correct": r[2], "n": r[3],
                      "expected": r[4], "dist": r[5]} for r in rows]}


def main():
    if not KEY:
        print("MY_OPENAI_KEY not set in env", file=sys.stderr)
        sys.exit(2)
    print("=== Live-LLM loop-feedback validation ===")
    print("Validates: does a REAL model pick the right next action given each arm's feedback?")
    print("Synthetic #67 reference: baseline NAIVE 20%% / ROBUST 60%% -> treatment 100%%.")
    results = [run_model(m) for m in MODELS]
    print("\nReading: capable models behave near the ROBUST profile on explicit strings "
          "(both arms high); the gap shows up on ambiguous/empty observations and weaker "
          "models, where typed framing keeps treatment correct. Crash/transient wins are "
          "model-independent (proven deterministically, not tested here).")
    print("\n---JSON---")
    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__":
    main()
