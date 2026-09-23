// src/test/kotlin/com/eduai/turbofa/service/ChatServiceTest.kt
package com.eduai.turbofa.service

import com.eduai.turbofa.client.deepseek.ChatMessage
import com.eduai.turbofa.client.deepseek.TurbofaAgent
import com.eduai.turbofa.history.ChatHistoryStore
import com.eduai.turbofa.history.InMemoryHistoryCache
import com.eduai.turbofa.history.TextFileHistoryWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests of the R9 round-trip (spec 3.1, contracts 5d/5e): the agent is a MockK mock, the
 * history store is the real one with its text file pointed at a temp directory. No network, no
 * DB, no .env — every assertion goes through the [TurbofaAgent] seam ChatService owns.
 */
class ChatServiceTest {

    private val fuelingId = "99f068ca-ac6a-43fb-a53b-d2e7a573cfe2"

    /** The real history store (5e) over a throwaway temp file — project files stay untouched. */
    private fun historyIn(dir: Path): ChatHistoryStore =
        ChatHistoryStore(InMemoryHistoryCache(), TextFileHistoryWriter(dir.resolve("chat-history.txt")))

    private fun tempDir(): Path = Files.createTempDirectory("turbofa-chat-service-test")

    /** A mocked agent answering [replies] in order and recording every message list it is handed. */
    private fun recordingAgent(replies: List<String>): Pair<TurbofaAgent, MutableList<List<ChatMessage>>> {
        val calls = mutableListOf<List<ChatMessage>>()
        val agent = mockk<TurbofaAgent> {
            coEvery { chat(any()) } coAnswers {
                calls += firstArg<List<ChatMessage>>()
                replies.getOrElse(calls.size - 1) { replies.last() }
            }
        }
        return agent to calls
    }

    @Test
    fun `with fueling_id the outgoing user message carries the UUID and the raw prompt`() = runTest {
        val (agent, calls) = recordingAgent(listOf("Fueling 99f068ca was delivered."))
        val service = ChatService(agent, historyIn(tempDir()))

        val reply = service.chat("What happened with my fueling?", fuelingId)

        assertEquals("Fueling 99f068ca was delivered.", reply)
        assertEquals(1, calls.size, "exactly one agent run per request")
        val messages = calls.single()
        assertEquals(1, messages.size, "the first request replays an empty history, not a duplicate prompt")
        val outgoing = messages.single()
        assertEquals("user", outgoing.role)
        assertTrue(fuelingId in outgoing.content, "the outgoing user message must carry the UUID (R9)")
        assertTrue(
            outgoing.content.startsWith("What happened with my fueling?"),
            "the raw prompt must survive the embedding",
        )
        coVerify(exactly = 1) { agent.chat(any()) }
    }

    @Test
    fun `without fueling_id nothing is embedded - the agent gets the plain prompt`() = runTest {
        val (agent, calls) = recordingAgent(listOf("Hello!"))
        val service = ChatService(agent, historyIn(tempDir()))

        service.chat("Hello")

        val messages = calls.single()
        assertEquals(listOf(ChatMessage(role = "user", content = "Hello")), messages)
        assertTrue(
            messages.none { "get_fueling_info" in it.content },
            "no tool instruction may be embedded without a fueling_id (R9 — plain dialogue, no tool call)",
        )
    }

    @Test
    fun `history is replayed in order and only the outgoing message carries the fueling_id`() = runTest {
        val (agent, calls) = recordingAgent(listOf("Fueling is done.", "You are welcome."))
        val history = historyIn(tempDir())
        val service = ChatService(agent, history)

        service.chat("What happened with my fueling?", fuelingId)
        service.chat("Thanks")

        // Turn 1: one user message, with the id embedded (R9)
        assertEquals(1, calls[0].size)
        assertTrue(fuelingId in calls[0].single().content)

        // Turn 2: the whole dialogue is replayed as user/assistant/user (5d, D2) — and the history
        // kept the RAW prompt, so a turn without a fueling_id stays a plain dialogue (no tool call)
        assertEquals(3, calls[1].size)
        assertEquals(listOf("user", "assistant", "user"), calls[1].map { it.role })
        assertEquals("What happened with my fueling?", calls[1][0].content)
        assertEquals("Fueling is done.", calls[1][1].content)
        assertEquals("Thanks", calls[1][2].content)
        assertTrue(calls[1].none { fuelingId in it.content }, "the embedded id must not leak into the history")
        assertTrue(calls[1].none { "get_fueling_info" in it.content })

        assertEquals(listOf("user", "assistant", "user", "assistant"), history.records().map { it.role })
    }

    @Test
    fun `the assistant record is timestamped at the agent response, not at request or write time - D5`() = runTest {
        var agentReturnedAt: Instant? = null
        val agent = mockk<TurbofaAgent> {
            coEvery { chat(any()) } coAnswers {
                // Real time so that "before the call" is observably earlier than the response
                // receipt: a stamp taken at request time cannot pass the assertions below.
                Thread.sleep(5)
                agentReturnedAt = Instant.now()
                "The fuel was delivered."
            }
        }
        val history = historyIn(tempDir())
        val service = ChatService(agent, history)

        val requestSentAt = Instant.now()
        service.chat("status?")
        val serviceReturnedAt = Instant.now()
        val responseReceivedAt = checkNotNull(agentReturnedAt)

        val user = history.records()[0]
        val assistant = history.records()[1]
        assertEquals("user", user.role)
        assertEquals("assistant", assistant.role)
        assertEquals("The fuel was delivered.", assistant.content)
        assertFalse(user.receivedAt.isAfter(responseReceivedAt), "the user record is stamped on prompt arrival")
        assertFalse(
            assistant.receivedAt.isBefore(responseReceivedAt),
            "D5: the assistant record is stamped at DeepSeek response receipt, not earlier",
        )
        assertFalse(
            assistant.receivedAt.isAfter(serviceReturnedAt),
            "D5: the assistant record is stamped at receipt, not at a later write time",
        )
        assertTrue(assistant.receivedAt.isAfter(requestSentAt))
    }

    @Test
    fun `cache and text file stay consistent and clear() empties both - R4`() = runTest {
        val dir = tempDir()
        val file = dir.resolve("chat-history.txt")
        val history = historyIn(dir)
        val service = ChatService(recordingAgent(listOf("OK")).first, history)

        service.chat("hi")

        assertEquals(listOf("user", "assistant"), history.records().map { it.role })
        assertEquals("OK", history.records()[1].content)
        assertEquals(
            listOf(
                "${history.records()[0].receivedAt} user: hi",
                "${history.records()[1].receivedAt} assistant: OK",
            ),
            Files.readAllLines(file),
        )

        // Restart (Application.kt calls clear() at startup, R4): the dialogue starts over
        history.clear()
        assertEquals(emptyList(), history.records())
        assertEquals("", Files.readString(file))
    }

    @Test
    fun `a multi-line reply stays one escaped line per record in the file`() = runTest {
        val dir = tempDir()
        val file = dir.resolve("chat-history.txt")
        val history = historyIn(dir)
        val service = ChatService(recordingAgent(listOf("line one\nline two")).first, history)

        service.chat("hi")

        assertEquals(2, Files.readAllLines(file).size, "one line per record, never one per content line")
        assertTrue(Files.readAllLines(file)[1].endsWith("assistant: line one\\nline two"))
        assertEquals("line one\nline two", history.records()[1].content, "the cache keeps the real text")
    }
}
