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
        response = self._client.chat.completions.create(
            model=self.model,
            messages=[{"role": "user", "content": prompt}],
            temperature=temperature,
            max_tokens=max_tokens,
        )
        content = response.choices[0].message.content or ""
        return Completion(content=content, request_id=response.id)

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not self.embedding_model:
            raise RuntimeError("embedding model is not configured")
        response = self._client.embeddings.create(
            model=self.embedding_model,
            input=texts,
        )
        return [row.embedding for row in response.data]


def parse_json_object(raw: str) -> dict[str, Any]:
    cleaned = raw.replace("```json", "").replace("```", "").strip()
    value = json.loads(cleaned)
    if not isinstance(value, dict):
        raise ValueError("model response must be a JSON object")
    return value
