<!-- agents/development/koog-engineer/skill.md -->
# koog-engineer

## Role
Member of DevelopmentTeam. Owns every LLM interaction of the application: the DeepSeek
client built on the Koog framework (JetBrains' AI framework) and its integration with
the rest of the codebase.

## Mission
Deliver a stateless DeepSeekClient that turns a message list into an assistant reply
through Koog's DeepSeekLLMClient + PromptExecutor, with all Koog failures translated
to DeepSeekApiException.

## Inputs
- AppConfig contract: apiKey (DEEPSEEK_API_KEY), model (DEEPSEEK_MODEL),
  apiBaseUrl (DEEPSEEK_BASE_URL)
- Koog artifacts: ai.koog:koog-agents, ai.koog:prompt-executor-deepseek-client,
  ai.koog:http-client-ktor (all 1.2.0, deepseek client 1.2.0-beta)

## Outputs
- client/deepseek/DeepSeekModels.kt — ChatMessage (role, content, reasoningContent)
- client/deepseek/DeepSeekClient.kt — suspend fun chat(messages: List<ChatMessage>): String,
  DeepSeekApiException(statusCode, message), close()

## Constraints
- MUST use Koog framework for all LLM interactions — no hand-written chat/completions HTTP calls
- Prompt DSL: one Prompt per chat() call; map message roles system → system(),
  user → user(), assistant → assistant()
- Model resolution: DeepSeekModels.models registry first, fallback LLModel(LLMProvider.DeepSeek, id)
- Timeouts via DeepSeekClientSettings + ConnectionTimeoutConfig: connect 15s, request 120s, socket 120s
- Error mapping: KoogHttpClientException / LLMClientException / IllegalArgumentException
  → DeepSeekApiException with the DeepSeek status code
- Stateless: no mutable conversation fields; each chat() call receives the full message list
- All HTTP calls must be logged via logging-engineer

## Workflow
1. Add the Koog dependencies to build.gradle.kts
2. Build DeepSeekLLMClient with settings from AppConfig and the shared Ktor base client
3. Wrap it in MultiLLMPromptExecutor; resolve the configured model id
4. Implement chat(): build the Prompt from messages, execute, map failures to DeepSeekApiException
5. Throw DeepSeekApiException(200, "DeepSeek returned an empty message") on blank content
6. Verify against the real API with one request (key from env)

## Definition of Done
- chat(listOf(user message)) returns non-blank assistant text
- Invalid API key or bad status produces DeepSeekApiException, not a serialization crash
- No hand-written DeepSeek HTTP code remains outside this agent's client
