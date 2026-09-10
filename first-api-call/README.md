# edu-ai — DeepSeek "First API Call" service

A small Kotlin service built with **Ktor** and **Koog** that wraps the DeepSeek
[First API Call](https://api-docs.deepseek.com/) behind a local HTTP API.
You send a request to the local API; the service runs a Koog prompt against
DeepSeek's `chat/completions` endpoint and returns the response.

## Stack

| Component   | Version |
|-------------|---------|
| Kotlin      | 2.4.20  |
| Ktor        | 3.5.2   |
| [Koog](https://docs.koog.ai/) (JetBrains' AI framework) | 1.2.0 (`prompt-executor-deepseek-client` is `1.2.0-beta`) |
| kotlinx.serialization | 1.11.0 |
| Gradle      | 9.7.0 (wrapper) |
| Tests       | JUnit 5 (kotlin-test) + Ktor test host |

## Prerequisites

- JDK 23+
- A DeepSeek API key from https://platform.deepseek.com

## Run locally

```bash
cp .env.example .env          # then put your key in .env — the app reads it automatically
./gradlew run
```

(Environment variables take precedence over `.env` values, so
`export DEEPSEEK_API_KEY=...` also works.)

The server starts on `http://localhost:8080`.

## API

### `GET /health`

```bash
curl http://localhost:8080/health
# {"status": "ok"}
```

### `POST /api/chat` — triggers the DeepSeek call

```bash
curl -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"prompt": "Hello!"}'
```

```json
{
  "response": "Hello! How can I help you today?",
  "reasoning": "The user greeted me...",
  "model": "deepseek-v4-pro",
  "usage": { "prompt_tokens": 9, "completion_tokens": 12, "total_tokens": 21 },
  "finish_reason": "stop"
}
```

Errors from DeepSeek (401, 429, 5xx…) are mapped onto the same HTTP statuses
with an `{"error": "..."}` body. A missing `DEEPSEEK_API_KEY` returns 500 with
a clear message.

## Configuration (environment variables)

| Variable           | Default                    | Description                              |
|--------------------|----------------------------|------------------------------------------|
| `DEEPSEEK_API_KEY` | *(required)*               | Your DeepSeek API key                    |
| `DEEPSEEK_BASE_URL`| `https://api.deepseek.com` | DeepSeek API base URL                    |
| `DEEPSEEK_MODEL`   | `deepseek-v4-pro`          | Model name (see [DeepSeek docs](https://api-docs.deepseek.com/)) |
| `PORT`             | `8080`                     | Local server port                        |

The app reads a `.env` file from the working directory automatically
(see `.env.example`); environment variables take precedence.

## How the Koog integration works

- `Application.kt` builds a Koog `DeepSeekLLMClient` (API key, base URL,
  timeouts) and wraps it in a `MultiLLMPromptExecutor`.
- Per request, `KoogChatService` builds a Koog `Prompt` — a system message
  plus the user prompt, with DeepSeek thinking enabled
  (`thinking: {"type": "enabled"}`, `reasoning_effort: "high"` — the same
  parameters the old raw HTTP client sent) — and calls `executor.execute()`.
- The resulting `Message.Assistant` is mapped onto the local API response:
  `textContent()` → `response`, `MessagePart.Reasoning` → `reasoning`,
  `metaInfo` token counts → `usage`, `finishReason` → `finish_reason`.
- Koog failures (`KoogHttpClientException`, `LLMClientException`) are
  translated to `DeepSeekApiException` so the Ktor `StatusPages` mapping
  stays unchanged.

## Tests

```bash
./gradlew test
```

All tests run **without an API key** — the chat backend is faked with a
`ChatService`/`PromptExecutor` stub, no network. They cover:

- `KoogChatServiceTest` — prompt shape (system + user messages, thinking and
  `reasoning_effort` params, model), response mapping (text, reasoning,
  usage, finish reason), error translation, model resolution, missing key
- `ChatRoutesTest` — the local API: happy path, DeepSeek 401 mapping,
  missing key, blank prompt, malformed JSON

### Optional live test (real API call)

```bash
DEEPSEEK_API_KEY=sk-your-key-here ./gradlew test --tests "com.eduai.LiveDeepSeekTest"
```

Skipped automatically when the key is not set.

## Project layout

```
src/main/kotlin/com/eduai/
  Application.kt          — entry point: Koog client + Netty server on PORT
  config/AppConfig.kt     — env-var configuration
  model/ChatModels.kt     — local API DTOs
  service/KoogChatService.kt — Koog prompt executor wrapper (ChatService)
  routes/ChatRoutes.kt    — GET /health, POST /api/chat, error mapping
src/test/kotlin/com/eduai/
  service/KoogChatServiceTest.kt
  routes/ChatRoutesTest.kt
  LiveDeepSeekTest.kt     — real call, opt-in via DEEPSEEK_API_KEY
```
