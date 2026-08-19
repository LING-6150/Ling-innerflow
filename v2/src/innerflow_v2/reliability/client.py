from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any, Protocol


@dataclass(frozen=True)
class Completion:
    content: str
    request_id: str | None = None


class ChatBackend(Protocol):
    def complete(
        self,
        *,
        operation: str,
        prompt: str,
        temperature: float,
        max_tokens: int,
    ) -> Completion: ...


class EmbeddingBackend(Protocol):
    def embed(self, texts: list[str]) -> list[list[float]]: ...


class ProviderCallError(RuntimeError):
    def __init__(self, operation: str, cause: Exception) -> None:
        super().__init__(f"{operation} provider call failed: {type(cause).__name__}")
        self.operation = operation
        self.provider_request_id = getattr(cause, "request_id", None)
        self.cause_type = type(cause).__name__
        self.__cause__ = cause


class IncompleteProviderResponse(RuntimeError):
    def __init__(
        self,
        *,
        request_id: str | None,
        finish_reason: str | None,
    ) -> None:
        super().__init__("provider returned no complete response content")
        self.request_id = request_id
        self.finish_reason = finish_reason


def require_complete_content(response: Any, *, operation: str) -> str:
    request_id = getattr(response, "id", None)
    try:
        choice = response.choices[0]
        content = choice.message.content
        finish_reason = getattr(choice, "finish_reason", None)
    except (AttributeError, IndexError, TypeError) as error:
        incomplete = IncompleteProviderResponse(
            request_id=request_id,
            finish_reason=None,
        )
        raise ProviderCallError(operation, incomplete) from error
    if (
        finish_reason != "stop"
        or not isinstance(content, str)
        or not content.strip()
    ):
        incomplete = IncompleteProviderResponse(
            request_id=request_id,
            finish_reason=finish_reason,
        )
        raise ProviderCallError(operation, incomplete)
    return content


class OpenAICompatibleBackend:
    """Small adapter; importing OpenAI is deferred so deterministic tests stay offline."""

    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        base_url: str | None = None,
        embedding_model: str | None = None,
        timeout: float = 120.0,
    ) -> None:
        from openai import OpenAI

        self._client = OpenAI(api_key=api_key, base_url=base_url, timeout=timeout)
        self.model = model
        self.embedding_model = embedding_model

    def complete(
        self,
        *,
        operation: str,
        prompt: str,
        temperature: float,
        max_tokens: int,
    ) -> Completion:
        try:
            response = self._client.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": prompt}],
                temperature=temperature,
                max_tokens=max_tokens,
            )
        except Exception as error:
            raise ProviderCallError(operation, error) from error
        content = require_complete_content(response, operation=operation)
        return Completion(content=content, request_id=response.id)

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not self.embedding_model:
            raise RuntimeError("embedding model is not configured")
        try:
            response = self._client.embeddings.create(
                model=self.embedding_model,
                input=texts,
            )
        except Exception as error:
            raise ProviderCallError("memory.trigger.embedding", error) from error
        return [row.embedding for row in response.data]


def parse_json_object(raw: str) -> dict[str, Any]:
    cleaned = raw.replace("```json", "").replace("```", "").strip()
    value = json.loads(cleaned)
    if not isinstance(value, dict):
        raise ValueError("model response must be a JSON object")
    return value
