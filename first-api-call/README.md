# edu-ai — DeepSeek "First API Call" service

A small Kotlin service built with **Ktor** that wraps the DeepSeek
[First API Call](https://api-docs.deepseek.com/) behind a local HTTP API.
You send a request to the local API; the service calls DeepSeek's
`chat/completions` endpoint and returns the response.

## Stack

| Component   | Version |
|-------------|---------|
| Kotlin      | 2.4.20  |
| Ktor        | 3.5.2   |
| kotlinx.serialization | 1.11.0 |
| Gradle      | 9.7.0 (wrapper) |
| Tests       | JUnit 5 (kotlin-test) + Ktor MockEngine |

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

## Tests

```bash
./gradlew test
```

All tests run **without an API key** — the DeepSeek backend is mocked with
Ktor's `MockEngine`. They cover:

- `DeepSeekClientTest` — request shape (URL, `Authorization` header, body
  matching the docs' example), response parsing, error mapping, missing key
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
  Application.kt          — entry point: Netty server on PORT
  config/AppConfig.kt     — env-var configuration
  model/ChatModels.kt     — DeepSeek + local API DTOs
  service/DeepSeekClient.kt — chat/completions client
  routes/ChatRoutes.kt    — GET /health, POST /api/chat, error mapping
src/test/kotlin/com/eduai/
  service/DeepSeekClientTest.kt
  routes/ChatRoutesTest.kt
  LiveDeepSeekTest.kt     — real call, opt-in via DEEPSEEK_API_KEY
```
