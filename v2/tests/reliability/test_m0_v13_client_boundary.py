from types import SimpleNamespace

import pytest

from innerflow_v2.reliability.client import (
    OpenAICompatibleBackend,
    ProviderCallError,
)


def _backend(response):
    backend = OpenAICompatibleBackend.__new__(OpenAICompatibleBackend)
    backend.model = "frozen-model"
    backend.embedding_model = None
    backend._client = SimpleNamespace(
        chat=SimpleNamespace(
            completions=SimpleNamespace(create=lambda **kwargs: response)
        )
    )
    return backend


def _response(content, *, finish_reason="stop", request_id="provider-123"):
    return SimpleNamespace(
        id=request_id,
        choices=[
            SimpleNamespace(
                message=SimpleNamespace(content=content),
                finish_reason=finish_reason,
            )
        ],
    )


@pytest.mark.parametrize(
    "response",
    [
        _response(None),
        _response(""),
        _response("   "),
        _response('{"response_action":', finish_reason="length"),
        SimpleNamespace(id="provider-123", choices=[]),
    ],
)
def test_missing_empty_or_truncated_completion_is_provider_failure(response):
    with pytest.raises(ProviderCallError) as captured:
        _backend(response).complete(
            operation="memory.probe.response_action",
            prompt="frozen prompt",
            temperature=0.4,
            max_tokens=80,
        )

    assert captured.value.provider_request_id == "provider-123"
    assert captured.value.cause_type == "IncompleteProviderResponse"


def test_complete_but_schema_invalid_completion_reaches_frozen_grader():
    completion = _backend(_response('{"unexpected":"value"}')).complete(
        operation="memory.probe.response_action",
        prompt="frozen prompt",
        temperature=0.4,
        max_tokens=80,
    )

    assert completion.content == '{"unexpected":"value"}'
    assert completion.request_id == "provider-123"
