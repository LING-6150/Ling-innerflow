"""The MemorySystem protocol and the systems compared in the Stage 2 eval.

Each system ingests an observation stream and exposes a consolidated profile,
its conflict resolutions, and retrieval. They differ ONLY in consolidation /
conflict handling / what they retain — which is what the eval measures.

Honest discriminator matrix (why the kernel must win for principled reasons):

  system            current-facts            keep_both        historical recall
  ----------------  -----------------------  ---------------  -----------------
  B-full            wrong (keeps stale)      right (keeps all) right (keeps all)
  B-rag             no profile -> worst      n/a              right
  B-latest-by-key   right (latest wins)      WRONG (drops it)  WRONG (drops hist)
  B-extract-only    = B-full here (PR-1)     right             right
  Kernel            right                    right            right
"""
from __future__ import annotations

import re
from typing import Protocol

from innerflow_v2.memory.models import MemoryConflict
from innerflow_v2.memory.types import ObservationInput, ProfileClaim

_TOKEN = re.compile(r"[a-z0-9]+|[一-鿿]")

# Accumulative kinds reinforce across observations (a recurring trigger is not a
# contradiction); replacive kinds (fact/preference/correction) supersede.
_ACCUMULATIVE: frozenset[str] = frozenset({"trigger", "progress"})


def _tokens(text: str) -> set[str]:
    return set(_TOKEN.findall((text or "").lower()))


def _retrieve_from(obs: list[ObservationInput], query: str, k: int) -> list[str]:
    """Lexical overlap retrieval over a retained observation set; recency
    tie-break. Returns observation ids (only those with any overlap)."""
    q = _tokens(query)
    scored = []
    for o in obs:
        score = len(q & _tokens(o.content + " " + (o.context or "")))
        if score > 0:
            scored.append((score, o.order, o.id))
    scored.sort(key=lambda t: (t[0], t[1]), reverse=True)
    return [oid for _, _, oid in scored[: max(0, k)]]


class MemorySystem(Protocol):
    def ingest(self, observations: list[ObservationInput]) -> None: ...
    def profile(self) -> list[ProfileClaim]: ...
    def retrieve(self, query: str, k: int = 5) -> list[str]: ...
    def resolved_conflicts(self) -> list[MemoryConflict]: ...


def _claim(o: ObservationInput) -> ProfileClaim:
    return ProfileClaim(
        semantic_key=o.semantic_key,
        content=o.content,
        source_observation_ids=[o.id],
        kind=o.kind,
    )


class BFull:
    """Full history, no consolidation: every observation is asserted as current."""
    name = "B-full"

    def __init__(self) -> None:
        self._obs: list[ObservationInput] = []

    def ingest(self, observations: list[ObservationInput]) -> None:
        self._obs = list(observations)

    def profile(self) -> list[ProfileClaim]:
        return [_claim(o) for o in self._obs]

    def retrieve(self, query: str, k: int = 5) -> list[str]:
        return _retrieve_from(self._obs, query, k)

    def resolved_conflicts(self) -> list[MemoryConflict]:
        return []


class BRag:
    """Naive RAG-over-chat: retrieval only, builds no consolidated profile."""
    name = "B-rag"

    def __init__(self) -> None:
        self._obs: list[ObservationInput] = []

    def ingest(self, observations: list[ObservationInput]) -> None:
        self._obs = list(observations)

    def profile(self) -> list[ProfileClaim]:
        return []

    def retrieve(self, query: str, k: int = 5) -> list[str]:
        return _retrieve_from(self._obs, query, k)

    def resolved_conflicts(self) -> list[MemoryConflict]:
        return []


class BLatestByKey:
    """Last-write-wins per semantic_key. Cheap and strong on supersession, but
    DROPS superseded observations — so it loses context-specific exceptions
    (keep_both) and cannot retrieve historical/exception facts."""
    name = "B-latest-by-key"

    def __init__(self) -> None:
        self._latest: dict[str, ObservationInput] = {}

    def ingest(self, observations: list[ObservationInput]) -> None:
        self._latest = {}
        for o in sorted(observations, key=lambda x: x.order):
            self._latest[o.semantic_key] = o  # later order overwrites

    def profile(self) -> list[ProfileClaim]:
        return [_claim(o) for o in self._latest.values()]

    def retrieve(self, query: str, k: int = 5) -> list[str]:
        return _retrieve_from(list(self._latest.values()), query, k)

    def resolved_conflicts(self) -> list[MemoryConflict]:
        return []


class BExtractOnly:
    """Extraction + retrieval, no conflict resolution. Under PR-1's
    observations-as-input setting this coincides with B-full; it diverges in
    PR-2 when extraction quality is actually in play."""
    name = "B-extract-only"

    def __init__(self) -> None:
        self._obs: list[ObservationInput] = []

    def ingest(self, observations: list[ObservationInput]) -> None:
        self._obs = list(observations)

    def profile(self) -> list[ProfileClaim]:
        return [_claim(o) for o in self._obs]

    def retrieve(self, query: str, k: int = 5) -> list[str]:
        return _retrieve_from(self._obs, query, k)

    def resolved_conflicts(self) -> list[MemoryConflict]:
        return []


class KernelDeterministic:
    """Context-aware consolidation: within a (semantic_key, context) keep the
    latest; ACROSS distinct contexts keep both. Retains full history for
    retrieval. Emits its conflict resolutions.

    The `context` field stands in for what PR-2's LLM judge infers from content;
    here it is an explicit feature so PR-1 stays deterministic and offline."""
    name = "Kernel-deterministic"

    def __init__(self) -> None:
        self._obs: list[ObservationInput] = []

    def ingest(self, observations: list[ObservationInput]) -> None:
        self._obs = list(observations)

    def _by_key(self) -> dict[str, list[ObservationInput]]:
        out: dict[str, list[ObservationInput]] = {}
        for o in sorted(self._obs, key=lambda x: x.order):
            out.setdefault(o.semantic_key, []).append(o)
        return out

    def profile(self) -> list[ProfileClaim]:
        claims: list[ProfileClaim] = []
        for _key, obs in self._by_key().items():
            if obs and all(o.kind in _ACCUMULATIVE for o in obs):
                # reinforce: a single consolidated claim citing every source
                last = obs[-1]
                claims.append(
                    ProfileClaim(
                        semantic_key=last.semantic_key,
                        content=last.content,
                        source_observation_ids=[o.id for o in obs],
                        kind=last.kind,
                    )
                )
                continue
            # replacive: keep the latest within each context bucket
            latest_by_ctx: dict[str, ObservationInput] = {}
            for o in obs:  # already order-ascending
                latest_by_ctx[o.context or "general"] = o
            for o in latest_by_ctx.values():
                claims.append(_claim(o))
        return claims

    def retrieve(self, query: str, k: int = 5) -> list[str]:
        # history preserved -> can answer current AND historical queries
        return _retrieve_from(self._obs, query, k)

    def resolved_conflicts(self) -> list[MemoryConflict]:
        conflicts: list[MemoryConflict] = []
        for _key, obs in self._by_key().items():
            if len(obs) < 2:
                continue
            if all(o.kind in _ACCUMULATIVE for o in obs):
                continue  # reinforcement, not a conflict
            contexts = {o.context or "general" for o in obs}
            old, new = obs[0], obs[-1]
            if len(contexts) >= 2:
                conflicts.append(
                    MemoryConflict(
                        old_observation_id=old.id,
                        new_observation_id=new.id,
                        conflict_type="contradiction",
                        resolution="keep_both",
                        rationale="distinct contexts -> both apply",
                    )
                )
            else:
                conflicts.append(
                    MemoryConflict(
                        old_observation_id=old.id,
                        new_observation_id=new.id,
                        conflict_type="supersession",
                        resolution="supersede",
                        rationale="same context -> latest supersedes",
                    )
                )
        return conflicts


ALL_SYSTEMS: list[type] = [BFull, BRag, BLatestByKey, BExtractOnly, KernelDeterministic]
