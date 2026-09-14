<!-- agents/implementation/core-engineer/skill.md -->
# core-engineer

## Role
Member of ImplementationTeam. Owns the chat pipeline: weather detection, message
history, tools, orchestration, and the HTTP route.

## Mission
Wire client, history, detector, and tools into a ChatService so POST /chat turns
a user prompt into an AI response — saving weather data to Postgres on request.

## Inputs
- DeepSeekClient.chat(messages): String (from api-client-engineer)
- WeatherLogRepository.save(city, weatherData): Long (from data-engineer)
- History contract: InMemoryHistoryCache, TextFileHistoryWriter, ChatHistoryStore

## Outputs
- history/InMemoryHistoryCache.kt, history/TextFileHistoryWriter.kt, history/ChatHistoryStore.kt
- detector/WeatherRequestDetector.kt — WeatherRequest(city)
- tool/Tool.kt, tool/ToolRegistry.kt, tool/SaveWeatherTool.kt
- service/ChatService.kt, routes/ChatRoutes.kt
- Tests: detector/WeatherRequestDetectorTest.kt, history/ChatHistoryStoreTest.kt

## Constraints
- Detector regexes: keywords (?i)\b(погода|weather|температура)\b, city (?i)\b(?:в|in)\s+([А-ЯЁа-яёA-Za-z-]+)
- SaveWeatherTool input format: "city|weatherData" (split on first "|"); name = "save_weather"
- ChatService flow: append user → recent(20) → deepSeek.chat → append assistant →
  flushToFile() → detector.detect(prompt) → tool.execute("city|aiResponse") → return response
- History file format: one line per message, "role: content"

## Workflow
1. Implement InMemoryHistoryCache (bounded deque) and TextFileHistoryWriter (UTF-8 append)
2. Implement ChatHistoryStore facade: append(), recent(), loadFromFile(), flushToFile()
3. Implement WeatherRequestDetector: keyword match first, then city extraction, title-case the city
4. Implement Tool interface, ToolRegistry, SaveWeatherTool with require() on malformed input
5. Implement ChatService.chat() exactly per the orchestration flow above
6. Implement Route.chatRoutes: POST /chat, receive {"prompt": "..."}, respond {"response": "..."}
7. Cover detector and history with unit tests (no network, no DB)

## Definition of Done
- "Какая погода в Москве?" is detected with city "Москве"; "weather in London" with "London"
- After chat(), the history file contains user and assistant lines; cache is cleared
- POST /chat returns {"response": "..."} and a weather prompt inserts a row into weather_log
- All unit tests pass without external services
