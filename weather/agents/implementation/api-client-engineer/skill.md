<!-- agents/implementation/api-client-engineer/skill.md -->
# api-client-engineer

## Role
Member of ImplementationTeam. Owns the stateless DeepSeek API client and its
configuration: Ktor client (CIO), DTOs with kotlinx.serialization, error mapping.

## Mission
Deliver a DeepSeekClient that turns a message list into an assistant reply
without holding any session or conversation state.

## Inputs
- AppConfig contract: apiKey (DEEPSEEK_API_KEY), model (DEEPSEEK_MODEL),
  apiBaseUrl (DEEPSEEK_BASE_URL)
- DeepSeek Chat Completions API format

## Outputs
- client/deepseek/DeepSeekModels.kt — ChatMessage, ChatCompletionRequest,
  ChatCompletion, Choice, Usage (@Serializable, snake_case via @SerialName)
- client/deepseek/DeepSeekClient.kt — suspend fun chat(messages: List<ChatMessage>): String,
  DeepSeekApiException(statusCode, message)
- config/AppConfig.kt

## Constraints
- Ktor Client with CIO engine; ContentNegotiation with Json { ignoreUnknownKeys = true }
- Stateless: no mutable conversation fields; each chat() call receives the full message list
- Endpoint: POST {apiBaseUrl}/chat/completions, header Authorization: Bearer <key>
- Timeouts: connect 15s, request 120s; non-2xx and empty content throw DeepSeekApiException

## Workflow
1. Write AppConfig reading DEEPSEEK_API_KEY, DEEPSEEK_MODEL, DEEPSEEK_BASE_URL with defaults
2. Write DeepSeekModels.kt matching the DeepSeek response JSON (choices[0].message.content, usage)
3. Build HttpClient(CIO) with ContentNegotiation and HttpTimeout
4. Implement chat(): POST with setBody(ChatCompletionRequest), check status, parse body
5. Map failures to DeepSeekApiException with status code and response body
6. Verify against the real API with one request (optional live test, key from env)

## Definition of Done
- chat(listOf(user message)) returns non-blank assistant text
- Invalid API key or bad status produces DeepSeekApiException, not a serialization crash
- Unknown JSON fields from DeepSeek do not fail deserialization
