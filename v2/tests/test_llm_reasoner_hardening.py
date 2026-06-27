"""LLMReasoner must fail-safe on bad model output: malformed JSON, wrong shapes,
and hallucinated observation ids degrade to empty/dropped — never crash, never
silently launder a hallucinated reference into a partial claim."""
from __future__ import annotations

from innerflow_v2.memory.llm import LLMReasoner, _safe_json
from innerflow_v2.memory.types import ObservationInput

OBS = [
    ObservationInput(id="o1", session_id="s1", order=1, semantic_key="name.preferred", kind="preference", content="Call me Alex."),
    ObservationInput(id="o2", session_id="s2", order=2, semantic_key="name.preferred", kind="correction", content="Call me Alexander."),
]


class _FakeClient:
    def __init__(self, data: dict) -> None:
        self._data = data

    def complete_json(self, system: str, user: str) -> dict:
        return self._data

    def embed(self, texts):
        return [[1.0, 0.0] for _ in texts]


def test_safe_json_never_raises():
    assert _safe_json("not json") == {}
    assert _safe_json("[1, 2, 3]") == {}  # valid json but not an object
    assert _safe_json(None) == {}
    assert _safe_json('{"a": 1}') == {"a": 1}


def test_consolidate_tolerates_bad_shapes():
    claims, conflicts = LLMReasoner(_FakeClient({"claims": "oops", "conflicts": 123})).consolidate(OBS)
    assert claims == [] and conflicts == []


def test_consolidate_skips_non_dict_rows():
    data = {"claims": [123, "x", {"semantic_key": "name.preferred", "content": "Alexander", "source_observation_ids": ["o2"]}]}
    claims, _ = LLMReasoner(_FakeClient(data)).consolidate(OBS)
    assert [c.semantic_key for c in claims] == ["name.preferred"]


def test_consolidate_drops_claim_with_any_hallucinated_id():
    data = {"claims": [{"semantic_key": "name.preferred", "content": "x", "source_observation_ids": ["o1", "ghost"]}]}
    claims, _ = LLMReasoner(_FakeClient(data)).consolidate(OBS)
    assert claims == []  # whole claim dropped, not laundered to ["o1"]


def test_consolidate_drops_bad_conflicts():
    data = {"conflicts": [
        {"old_observation_id": "o1", "new_observation_id": "ghost", "resolution": "supersede"},
        {"old_observation_id": "o1", "new_observation_id": "o2", "resolution": "not_a_resolution"},
    ]}
    _, conflicts = LLMReasoner(_FakeClient(data)).consolidate(OBS)
    assert conflicts == []
