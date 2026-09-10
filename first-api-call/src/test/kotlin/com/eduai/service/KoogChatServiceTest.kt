package com.eduai.service

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekParams
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import com.eduai.config.AppConfig
import com.eduai.config.MissingApiKeyException
import com.eduai.model.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val okAssistant = Message.Assistant(
    parts = listOf(
        MessagePart.Text("Hi there! I am DeepSeek."),
        MessagePart.Reasoning("A friendly reply."),
    ),
    metaInfo = ResponseMetaInfo.create(
        KoogClock.System,
        totalTokensCount = 15,
        inputTokensCount = 9,
        outputTokensCount = 6,
    ),
    finishReason = "stop",
)

private fun testConfig(apiKey: String? = "test-key") = AppConfig(
    apiKey = apiKey,
    baseUrl = "https://api.deepseek.com",
    model = "deepseek-v4-pro",
)

/** A [PromptExecutor] fake that captures the executed prompt/model and returns a canned reply. */
private class CapturingPromptExecutor(
    private val reply: Message.Assistant = okAssistant,
) : PromptExecutor() {
    val calls = mutableListOf<Pair<Prompt, LLModel>>()

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Message.Assistant {
        calls += prompt to model
        return reply
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): Flow<StreamFrame> =
        throw UnsupportedOperationException("not used in tests")

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("not used in tests")

    override fun close() = Unit
}

class KoogChatServiceTest {

    @Test
    fun `sends a well-formed prompt and parses the response`() = runBlocking {
        val executor = CapturingPromptExecutor()
        val service = KoogChatService(executor, testConfig(), resolveModel("deepseek-v4-pro"))

        val response = service.chat("Hello!")

        // The prompt executed by Koog: system + user messages and DeepSeek thinking params
        val (prompt, model) = executor.calls.single()
        assertEquals(2, prompt.messages.size)
        assertIs<Message.System>(prompt.messages[0])
        assertEquals("You are a helpful assistant.", prompt.messages[0].textContent())
        assertIs<Message.User>(prompt.messages[1])
        assertEquals("Hello!", prompt.messages[1].textContent())
        assertEquals("deepseek-v4-pro", model.id)
        val params = assertIs<DeepSeekParams>(prompt.params)
        assertEquals(
            "enabled",
            params.additionalProperties!!["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
        assertEquals("high", params.additionalProperties!!["reasoning_effort"]!!.jsonPrimitive.content)
        // The parsed response mapped onto the local API shape
        assertEquals("Hi there! I am DeepSeek.", response.response)
        assertEquals("A friendly reply.", response.reasoning)
        assertEquals("deepseek-v4-pro", response.model)
        assertEquals("stop", response.finishReason)
        assertEquals(15, response.usage?.totalTokens)
        assertEquals(9, response.usage?.promptTokens)
        assertEquals(6, response.usage?.completionTokens)
    }

    @Test
    fun `maps the assistant message to the local response`() {
        val local = okAssistant.toLocalChatResponse("deepseek-v4-pro")

        assertEquals("Hi there! I am DeepSeek.", local.response)
        assertEquals("A friendly reply.", local.reasoning)
        assertEquals("deepseek-v4-pro", local.model)
        assertEquals("stop", local.finishReason)
        assertEquals(Usage(9, 6, 15), local.usage)
    }

    @Test
    fun `joins multiple reasoning parts with newlines`() {
        val assistant = Message.Assistant(
            parts = listOf(
                MessagePart.Text("answer"),
                MessagePart.Reasoning("first thought"),
                MessagePart.Reasoning("second thought"),
            ),
            metaInfo = ResponseMetaInfo.create(KoogClock.System),
        )

        assertEquals("first thought\nsecond thought", assistant.toLocalChatResponse("m").reasoning)
    }

    @Test
    fun `omits usage when token counts are missing`() {
        val assistant = Message.Assistant(
            content = "answer",
            metaInfo = ResponseMetaInfo.create(KoogClock.System),
        )

        val local = assistant.toLocalChatResponse("m")
        assertEquals("answer", local.response)
        assertNull(local.usage)
    }

    @Test
    fun `blank assistant content maps to a 502`() {
        val assistant = Message.Assistant(
            content = "   ",
            metaInfo = ResponseMetaInfo.create(KoogClock.System),
        )

        val exception = assertFailsWith<DeepSeekApiException> { assistant.toLocalChatResponse("m") }
        assertEquals(502, exception.statusCode)
    }

    @Test
    fun `translates Koog HTTP errors onto DeepSeekApiException`() {
        val unauthorized = translateError(
            KoogHttpClientException(statusCode = 401, errorBody = """{"error": "invalid api key"}"""),
        )
        assertEquals(401, unauthorized.statusCode)
        assertTrue(unauthorized.message!!.contains("401"))
        assertTrue(unauthorized.message!!.contains("invalid api key"))

        val transport = translateError(KoogHttpClientException())
        assertEquals(502, transport.statusCode)

        val client = translateError(LLMClientException("DeepSeekLLMClient", "boom"))
        assertEquals(502, client.statusCode)

        val invalid = translateError(IllegalArgumentException("Empty choices in response"))
        assertEquals(502, invalid.statusCode)
    }

    @Test
    fun `unknown exceptions are not translated`() {
        assertFailsWith<RuntimeException> { translateError(RuntimeException("unrelated")) }
    }

    @Test
    fun `throws MissingApiKeyException before calling the backend when no key is configured`() = runBlocking {
        val executor = CapturingPromptExecutor()
        val service = KoogChatService(executor, testConfig(apiKey = null), resolveModel("deepseek-v4-pro"))

        assertFailsWith<MissingApiKeyException> { service.chat("Hello!") }
        assertTrue(executor.calls.isEmpty(), "backend must not be called without a key")
    }

    @Test
    fun `resolves built-in and custom model ids`() {
        assertEquals(DeepSeekModels.DeepSeekV4Pro, resolveModel("deepseek-v4-pro"))
        assertEquals(DeepSeekModels.DeepSeekV4Flash, resolveModel("deepseek-v4-flash"))

        val custom = resolveModel("deepseek-custom-model")
        assertEquals("deepseek-custom-model", custom.id)
        assertEquals(LLMProvider.DeepSeek, custom.provider)
    }
}
