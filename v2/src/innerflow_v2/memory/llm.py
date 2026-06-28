"""LLM/embedding plumbing for the PR-2b memory kernel.

A `MemoryReasoner` turns an observation stream into (claims, conflicts) and ranks
observations for a query. Two impls:

* `LLMReasoner` — uses an OpenAI chat model to INFER semantic_key + context from
  content (constrained to the closed vocabulary) and judge conflicts, and uses
  embeddings for retrieval. This is where extraction/inference difficulty becomes
  real (PR-1 was handed those fields).
* `FakeReasoner` — deterministic, offline; used by the test-suite to verify
  KernelLLM wiring without spending tokens.
"""
from __future__ import annotations

import json
import math
import os
from typing import Protocol

from innerflow_v2.memory.models import MemoryConflict
from innerflow_v2.memory.semantic_keys import SEMANTIC_KEYS, is_valid_semantic_key
from innerflow_v2.memory.types import ObservationInput, ProfileClaim

_VALID_RESOLUTIONS = {"supersede", "keep_both", "mark_uncertain", "reject_new"}


def _safe_json(text: str | None) -> dict:
    """Parse model output to a dict, never raising. A non-JSON or non-object
    response degrades to {} (scored as empty/failed output, not a crash)."""
    try:
        obj = json.loads(text or "{}")
    except (ValueError, TypeError):
        return {}
    return obj if isinstance(obj, dict) else {}


def _rows(value: object) -> list[dict]:
    """Coerce a JSON field to a list of dict rows; tolerate junk (a string, a
    dict, non-dict rows) by skipping it."""
    if not isinstance(value, list):
        return []
    return [r for r in value if isinstance(r, dict)]


class MemoryReasoner(Protocol):
    def consolidate(
        self, observations: list[ObservationInput]
    ) -> tuple[list[ProfileClaim], list[MemoryConflict]]: ...

    def rank(self, query: str, observations: list[ObservationInput], k: int) -> list[str]: ...


def _read_api_key() -> str:
    for name in ("MY_OPENAI_KEY", "PERSONAL_OPENAI_KEY", "OPENAI_API_KEY"):
        v = os.environ.get(name)
        if v and v.strip():
            return v.strip()
    raise RuntimeError("No API key in MY_OPENAI_KEY / PERSONAL_OPENAI_KEY / OPENAI_API_KEY")


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


class OpenAIClient:
    def __init__(self, chat_model: str = "gpt-4o-mini", embed_model: str = "text-embedding-3-small") -> None:
        from openai import OpenAI

        self._client = OpenAI(api_key=_read_api_key())
        self._chat_model = chat_model
        self._embed_model = embed_model

    def complete_json(self, system: str, user: str) -> dict:
        resp = self._client.chat.completions.create(
            model=self._chat_model,
            messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
            response_format={"type": "json_object"},
            temperature=0,
        )
        return _safe_json(resp.choices[0].message.content)

    def embed(self, texts: list[str]) -> list[list[float]]:
        resp = self._client.embeddings.create(model=self._embed_model, input=texts)
        return [d.embedding for d in resp.data]


_SYSTEM = (
    "You consolidate a user's observations (made across sessions) into a CURRENT, "
    "self-consistent profile. You must INFER each observation's semantic_key (from "
    "the provided closed list ONLY) and its situational context from the text. Rules:\n"
    "- Same semantic_key + same situation: the later observation SUPERSEDES the earlier.\n"
    "- Same semantic_key + DIFFERENT situations/contexts: KEEP_BOTH (both are current).\n"
    "- Recurring observations of the same recurring signal (e.g. the same kind of "
    "trigger): CONSOLIDATE into one claim citing all their ids; that is not a conflict.\n"
    "- Only cite observation ids that were given. Only use semantic_keys from the list.\n"
    'Return JSON: {"claims":[{"semantic_key","content","source_observation_ids":[...]}],'
    '"conflicts":[{"old_observation_id","new_observation_id","resolution"}]} where '
    "resolution is one of supersede|keep_both|mark_uncertain|reject_new."
)


_SYSTEM_V2 = (
    _SYSTEM
    + "\n\nFurther guidance (PR-2c):\n"
    "- Infer `context` from situational phrases ('generally'/'usually' = general; "
    "'before interviews', 'during incidents', 'on hard days', 'before a deadline' "
    "= that specific situation). Same key + DIFFERENT situation => keep_both, not "
    "supersede.\n"
    "- Emit a claim ONLY for keys an observation actually supports; do NOT invent "
    "claims for keys with no backing observation (avoid over-emitting).\n"
    "- Output EXACTLY ONE consolidated claim per (semantic_key, situation).\n"
    "Examples:\n"
    "  [{id:a,'I usually like blunt feedback'},{id:b,'but during reviews be gentle'}] "
    "-> two claims (general=a, reviews=b); conflict keep_both(a,b).\n"
    "  [{id:a,'I jog at dawn'},{id:b,'switched to evening jogs'}] -> one claim citing "
    "b; conflict supersede(a,b).\n"
    "  [{id:a,'crowds stress me'},{id:b,'busy stations make me tense'}] -> one "
    "consolidated claim citing a and b (recurring); no conflict."
)


class LLMReasoner:
    def __init__(self, client: OpenAIClient, system_prompt: str = _SYSTEM) -> None:
        self._client = client
        self._system = system_prompt

    def consolidate(
        self, observations: list[ObservationInput]
    ) -> tuple[list[ProfileClaim], list[MemoryConflict]]:
        obs_ids = {o.id for o in observations}
        payload = {
            "allowed_semantic_keys": sorted(SEMANTIC_KEYS),
            "observations": [{"id": o.id, "content": o.content} for o in observations],
        }
        data = self._client.complete_json(self._system, json.dumps(payload, ensure_ascii=False))

        claims: list[ProfileClaim] = []
        for c in _rows(data.get("claims")):
            key = c.get("semantic_key", "")
            srcs = c.get("source_observation_ids")
            if not key or not isinstance(srcs, list) or not srcs:
                continue
            # Drop the WHOLE claim if it cites any unknown id — a hallucinated ref
            # must not be silently cleaned into a partially-correct claim (it then
            # surfaces as a missing claim => contradiction, which is the point).
            if any(i not in obs_ids for i in srcs):
                continue
            # keep out-of-vocab keys too so the extra_claim guard can see them
            claims.append(
                ProfileClaim(
                    semantic_key=key if is_valid_semantic_key(key) else f"__invalid__:{key}",
                    content=str(c.get("content", "")),
                    source_observation_ids=list(srcs),
                    kind="fact",
                )
            )

        conflicts: list[MemoryConflict] = []
        for c in _rows(data.get("conflicts")):
            old, new = c.get("old_observation_id"), c.get("new_observation_id")
            res = c.get("resolution")
            if old in obs_ids and new in obs_ids and res in _VALID_RESOLUTIONS:
                conflicts.append(
                    MemoryConflict(
                        old_observation_id=old,
                        new_observation_id=new,
                        conflict_type="contradiction" if res == "keep_both" else "supersession",
                        resolution=res,
                        rationale="llm",
                    )
                )
        return claims, conflicts

    def rank(self, query: str, observations: list[ObservationInput], k: int) -> list[str]:
        if not observations:
            return []
        vecs = self._client.embed([query] + [o.content for o in observations])
        qv, ovs = vecs[0], vecs[1:]
        scored = sorted(
            ((_cosine(qv, ov), o.id) for o, ov in zip(observations, ovs)),
            key=lambda t: t[0],
            reverse=True,
        )
        return [oid for _, oid in scored[: max(0, k)]]


class FakeReasoner:
    """Deterministic, offline reasoner: reuses the deterministic kernel over the
    GIVEN semantic_key/context fields. Used by tests to verify KernelLLM wiring
    without tokens — it does NOT exercise inference quality (that needs the real
    LLMReasoner)."""

    def consolidate(
        self, observations: list[ObservationInput]
    ) -> tuple[list[ProfileClaim], list[MemoryConflict]]:
        from innerflow_v2.memory.systems import KernelDeterministic

        k = KernelDeterministic()
        k.ingest(observations)
        return k.profile(), k.resolved_conflicts()

    def rank(self, query: str, observations: list[ObservationInput], k: int) -> list[str]:
        from innerflow_v2.memory.systems import _retrieve_from

        return _retrieve_from(observations, query, k)
