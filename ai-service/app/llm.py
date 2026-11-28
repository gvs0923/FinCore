import json
from collections.abc import AsyncIterator

import httpx

from app.config import Settings


class LlmUnavailable(Exception):
    pass


class LlmClient:
    """OpenAI-compatible chat completions (Ollama, vLLM, hosted). Prompts are never logged."""

    def __init__(self, s: Settings):
        self._url = s.llm_base_url.rstrip("/") + "/chat/completions"
        self._model = s.llm_model
        self._timeout = s.llm_timeout_s
        self._headers = {"Authorization": f"Bearer {s.llm_api_key}"} if s.llm_api_key else {}
        self.enabled = bool(s.llm_base_url)

    @property
    def name(self) -> str:
        return self._model if self.enabled else "template"

    def _body(self, messages: list[dict], max_tokens: int, stream: bool, json_mode: bool = False) -> dict:
        body = {"model": self._model, "messages": messages, "temperature": 0,
                "max_tokens": max_tokens, "stream": stream}
        if json_mode:
            body["response_format"] = {"type": "json_object"}
        return body

    async def complete(self, messages: list[dict], *, json_mode: bool = False, max_tokens: int = 400) -> str:
        if not self.enabled:
            raise LlmUnavailable("not_configured")
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(self._timeout, connect=2.0)) as client:
                res = await client.post(self._url, json=self._body(messages, max_tokens, False, json_mode),
                                        headers=self._headers)
                res.raise_for_status()
                return res.json()["choices"][0]["message"]["content"]
        except (httpx.HTTPError, KeyError, IndexError, ValueError) as e:
            raise LlmUnavailable(type(e).__name__) from e

    async def stream(self, messages: list[dict], *, max_tokens: int = 600) -> AsyncIterator[str]:
        if not self.enabled:
            raise LlmUnavailable("not_configured")
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(self._timeout, connect=2.0)) as client:
                async with client.stream("POST", self._url, json=self._body(messages, max_tokens, True),
                                         headers=self._headers) as res:
                    res.raise_for_status()
                    async for line in res.aiter_lines():
                        if not line.startswith("data:"):
                            continue
                        data = line[5:].strip()
                        if data == "[DONE]":
                            return
                        choices = json.loads(data).get("choices") or []
                        delta = choices[0].get("delta", {}).get("content") if choices else None
                        if delta:
                            yield delta
        except (httpx.HTTPError, KeyError, ValueError) as e:
            raise LlmUnavailable(type(e).__name__) from e