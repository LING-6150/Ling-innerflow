"""KernelLLM — the PR-2b memory kernel.

Same MemorySystem interface as the PR-1 systems, but consolidation / conflict
resolution / context inference are delegated to a `MemoryReasoner` (LLM-backed
in the real run, deterministic Fake in tests). It is scored against the SAME
locked gold + precision guards + baselines, so the (harder) inference difficulty
shows up honestly in the numbers.
"""
from __future__ import annotations

from innerflow_v2.memory.llm import MemoryReasoner
from innerflow_v2.memory.models import MemoryConflict
from innerflow_v2.memory.types import ObservationInput, ProfileClaim


class KernelLLM:
    name = "Kernel-llm"

    def __init__(self, reasoner: MemoryReasoner) -> None:
        self._reasoner = reasoner
        self._obs: list[ObservationInput] = []
        self._cache: tuple[list[ProfileClaim], list[MemoryConflict]] | None = None

    def ingest(self, observations: list[ObservationInput]) -> None:
        self._obs = list(observations)
        self._cache = None

    def _consolidated(self) -> tuple[list[ProfileClaim], list[MemoryConflict]]:
        if self._cache is None:
            self._cache = self._reasoner.consolidate(self._obs)
        return self._cache

    def profile(self) -> list[ProfileClaim]:
        return self._consolidated()[0]

    def resolved_conflicts(self) -> list[MemoryConflict]:
        return self._consolidated()[1]

    def retrieve(self, query: str, k: int = 5) -> list[str]:
        return self._reasoner.rank(query, self._obs, k)
