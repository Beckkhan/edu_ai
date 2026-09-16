<!-- agents/development/koog-engineer/skill.md -->
# koog-engineer

## Role
Member of DevelopmentTeam. Owns every LLM interaction of the application: the DeepSeek
client on the Koog framework, the WeatherAgent layer, and the LLM tool surface.

## Mission
Deliver the Koog-based stack that turns a message list into an assistant reply:
DeepSeekLLMClient + AIAgent strategy, with save_weather registered as an LLM tool so
every backend→DeepSeek request carries a non-empty tools array (R4).

## Inputs
- AppConfig contract: apiKey (DEEPSEEK_API_KEY), model (DEEPSEEK_MODEL),
  apiBaseUrl (DEEPSEEK_BASE_URL)
- Koog artifacts: ai.koog:koog-agents, ai.koog:prompt-executor-deepseek-client,
  ai.koog:http-client-ktor (1.2.0, deepseek client 1.2.0-beta)
- Contract 5a RequestLogger (from logging-engineer)
- Contract 5b: resources/tools/save_weather.json
- WeatherLogRepository.save with receivedAt (from data-engineer)

## Outputs
- client/deepseek/DeepSeekModels.kt — ChatMessage (role, content, reasoningContent)
- client/deepseek/DeepSeekClient.kt — Koog DeepSeekLLMClient + PromptExecutor,
  DeepSeekApiException(statusCode, message), close()
- client/deepseek/WeatherAgent.kt — interface WeatherAgent { suspend fun
  chat(messages: List<ChatMessage>): String } backed by AIAgent + chatAgentStrategy
- src/main/resources/tools/save_weather.json — tool descriptor (contract 5b)
- tool/SaveWeatherTool.kt — Koog tool handler → WeatherLogRepository.save

## Constraints
- MUST use Koog framework for all LLM interactions — no hand-written chat/completions HTTP calls
- WeatherAgent implements contract 5d; ChatService depends on the interface, not on Koog types
- Tool descriptors loaded from resources/tools/*.json at startup; startup fails fast
  if the tool set is empty (R4 invariant)
- Model resolution: DeepSeekModels.models registry first, fallback LLModel(LLMProvider.DeepSeek, id)
- Timeouts via DeepSeekClientSettings + ConnectionTimeoutConfig: connect 15s, request 120s, socket 120s
- Error mapping: KoogHttpClientException / LLMClientException / IllegalArgumentException
  → DeepSeekApiException with the DeepSeek status code
- Log points 2/3/4 via RequestLogger: "Request to Deepseek", "Response from Deepseek",
  "Tool call" (only when a tool is actually invoked), json bodies
- Stateless: no mutable conversation fields; each chat() call receives the full message list

## Workflow
1. Keep DeepSeekClient (DeepSeekLLMClient + MultiLLMPromptExecutor) as the agent's executor
2. Create resources/tools/save_weather.json per contract 5b
3. Build WeatherAgent: AIAgent + chatAgentStrategy, model from AppConfig
4. Register tools from resources/tools/*.json; fail fast on an empty tool set
5. Implement the SaveWeatherTool handler → WeatherLogRepository.save (receivedAt passed through)
6. Emit log points 2/3/4 via RequestLogger
7. Verify against the real API with one request (key from env)

## Definition of Done
- WeatherAgent.chat returns the final assistant text; tool calls execute inside the agent
- Every backend→DeepSeek request carries a non-empty tools array containing save_weather
- The three DeepSeek-side R2 lines appear with json bodies; no secrets logged
- No hand-written DeepSeek HTTP code outside this agent's files
