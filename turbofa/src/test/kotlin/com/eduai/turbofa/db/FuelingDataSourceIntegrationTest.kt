package com.eduai.turbofa.db

import com.eduai.turbofa.config.AppConfig
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

/**
 * T11: integration test of the five SELECT queries of spec 5f (R5, R7) against the external stage
 * DB, using the real wiring of T9 — [AppConfig] (credentials from `.env`/environment, fail-fast)
 * feeds [DataSourceFactory], whose three pools feed [FuelingDataSource]. Nothing is mocked: the
 * test proves the actual JDBC path, live PostgreSQL error behavior and the TEXT-UUID binding of D6.
 *
 * Read-only (R5): every call is one of the five SELECT statements; the test issues no DDL, no DML
 * and no `INSERT`/`UPDATE`/`DELETE` — not even a rejected one. The only assertion about writes is
 * the client-side `isReadOnly` flag of the pools, which executes no SQL.
 *
 * Requires network access to the stage DB plus the `DB_*` values in `.env` (or the environment);
 * like the application, it fails fast when a credential is missing. The pools are closed in
 * [tearDown] so the Gradle test JVM exits without Hikari housekeeping threads.
 *
 * Sample ids come from the T3 verification of the 2026-09-23 schema discovery (§6):
 * - [FULL_CHAIN_ID] appears in all three databases (payments via its `user_id` 2433);
 * - [EMPTY_PAYMENTS_ID] is a fueling whose user has zero payments — the empty-list case of D8;
 * - [VENDOR_ONLY_ID] exists only in `vendor_fueling_orders`, the best-effort subset of D9;
 * - a fresh random UUID covers the "unknown id" path of all five queries.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FuelingDataSourceIntegrationTest {

    private lateinit var dataSources: DataSourceFactory
    private lateinit var fuelingDataSource: FuelingDataSource

    @BeforeAll
    fun setUp() {
        val config = AppConfig()
        dataSources = DataSourceFactory(
            fuelingJdbcUrl = config.fuelingJdbcUrl,
            paymentJdbcUrl = config.paymentJdbcUrl,
            vendorsJdbcUrl = config.vendorsJdbcUrl,
            user = config.dbUser,
            password = config.dbPassword,
        )
        fuelingDataSource = FuelingDataSource(
            fuelingDatabase = dataSources.fuelingDatabase,
            paymentDatabase = dataSources.paymentDatabase,
            vendorsDatabase = dataSources.vendorsDatabase,
        )
    }

    @AfterAll
    fun tearDown() {
        dataSources.close()
    }

    @Test
    fun `full chain - all five queries return data for a sample fueling_id`() {
        // Query 1, fueling DB: the row exists and the String UUID binds to the TEXT column (D6).
        val fueling = fuelingDataSource.fuelingById(FULL_CHAIN_ID)
        assertNotNull(fueling, "fuelings row of $FULL_CHAIN_ID (query 1)")
        assertEquals(FULL_CHAIN_ID, fueling["fueling_id"], "query 1 round-trips the fueling_id")

        val userId = fueling["user_id"]
        assertTrue(userId is String && userId.isNotBlank(), "user_id is a non-blank String, got $userId")

        // Query 2, payment DB: user-scoped, newest first, capped by the D8 LIMIT.
        val payments = fuelingDataSource.paymentsByUserId(userId)
        assertTrue(payments.isNotEmpty(), "user $userId has payments (query 2)")
        assertTrue(
            payments.size <= FuelingDataSource.PAYMENT_LIMIT,
            "query 2 respects LIMIT ${FuelingDataSource.PAYMENT_LIMIT}, got ${payments.size}",
        )
        payments.forEach { payment ->
            assertEquals(userId, payment["user_id"], "query 2 returns only the user's payments (D8)")
        }
        assertNewestFirst(payments, "query 2 (ORDER BY created_at DESC)")

        // Query 3, vendors DB: the 1:1 vendor order; its jsonb column arrives as text (§5f).
        val order = fuelingDataSource.fuelingOrdersById(FULL_CHAIN_ID)
        assertNotNull(order, "fueling_orders row of $FULL_CHAIN_ID (query 3)")
        assertEquals(FULL_CHAIN_ID, order["fueling_id"], "query 3 round-trips the fueling_id")
        assertTrue(order["data"] is String, "fueling_orders.data jsonb is unwrapped to text, got ${order["data"]?.javaClass}")

        // Query 4, vendors DB: the events of the fueling, newest first.
        val events = fuelingDataSource.fuelingEventsById(FULL_CHAIN_ID)
        assertTrue(events.isNotEmpty(), "fueling_events rows of $FULL_CHAIN_ID (query 4)")
        assertTrue(
            events.size <= FuelingDataSource.EVENT_LIMIT,
            "query 4 respects LIMIT ${FuelingDataSource.EVENT_LIMIT}, got ${events.size}",
        )
        events.forEach { event ->
            assertEquals(FULL_CHAIN_ID, event["fueling_id"], "query 4 returns only this fueling's events")
        }
        assertNewestFirst(events, "query 4 (ORDER BY created_at DESC)")

        // Query 5, vendors DB: best-effort (D9) — present or not, a hit must belong to the id.
        val vendorOrder = fuelingDataSource.vendorFuelingOrdersById(FULL_CHAIN_ID)
        if (vendorOrder != null) {
            assertEquals(FULL_CHAIN_ID, vendorOrder["fueling_id"], "query 5 returns only this fueling's vendor order")
        }
        assertTrue(fueling["extra"] is String, "fuelings.extra jsonb is unwrapped to text, got ${fueling["extra"]?.javaClass}")
    }

    @Test
    fun `empty payments list - a fueling whose user has no payments returns an empty list`() {
        // D8's negative case: the fueling and the vendor side exist, the payment side is empty —
        // an empty list, not null and not an exception.
        val fueling = fuelingDataSource.fuelingById(EMPTY_PAYMENTS_ID)
        assertNotNull(fueling, "fuelings row of $EMPTY_PAYMENTS_ID (query 1)")

        val userId = fueling["user_id"]
        assertTrue(userId is String && userId.isNotBlank(), "user_id is a non-blank String, got $userId")
        assertEquals(
            emptyList(),
            fuelingDataSource.paymentsByUserId(userId),
            "user $userId has no payments (query 2 empty-list case)",
        )

        // The other queries are independent of the empty payment list and still return data.
        assertNotNull(fuelingDataSource.fuelingOrdersById(EMPTY_PAYMENTS_ID), "query 3 still returns the order")
        assertTrue(fuelingDataSource.fuelingEventsById(EMPTY_PAYMENTS_ID).isNotEmpty(), "query 4 still returns events")
    }

    @Test
    fun `vendor-only key - vendor_fueling_orders covers ids absent from the fueling DB (D9)`() {
        // Query 5 is best-effort exactly because this key space is wider: the row exists here while
        // queries 1, 3 and 4 find nothing.
        val vendorOrder = fuelingDataSource.vendorFuelingOrdersById(VENDOR_ONLY_ID)
        assertNotNull(vendorOrder, "vendor_fueling_orders row of $VENDOR_ONLY_ID (query 5, D9)")
        assertEquals(VENDOR_ONLY_ID, vendorOrder["fueling_id"], "query 5 round-trips the fueling_id")

        assertNull(fuelingDataSource.fuelingById(VENDOR_ONLY_ID), "no fuelings row for the vendor-only key")
        assertNull(fuelingDataSource.fuelingOrdersById(VENDOR_ONLY_ID), "no fueling_orders row for the vendor-only key")
        assertTrue(fuelingDataSource.fuelingEventsById(VENDOR_ONLY_ID).isEmpty(), "no fueling_events for the vendor-only key")
    }

    @Test
    fun `unknown id - all five queries answer null or empty instead of throwing`() {
        val unknown = UUID.randomUUID().toString()

        assertNull(fuelingDataSource.fuelingById(unknown), "query 1")
        assertNull(fuelingDataSource.fuelingOrdersById(unknown), "query 3")
        assertTrue(fuelingDataSource.fuelingEventsById(unknown).isEmpty(), "query 4")
        assertNull(fuelingDataSource.vendorFuelingOrdersById(unknown), "query 5")
        // Query 2 is scoped by the user_id of query 1, so there is nothing to ask for: no call.
    }

    @Test
    fun `fueling_id is a String UUID - the TEXT column round-trips it verbatim (D6)`() {
        // D6 pins the parameter type at compile time (String, not Int); at runtime the test proves
        // the value is a TEXT UUID on the wire: the same 36-character string comes back unchanged.
        val fueling = fuelingDataSource.fuelingById(FULL_CHAIN_ID)
        val id = fueling?.get("fueling_id")
        assertTrue(id is String, "fueling_id column is TEXT, got ${id?.javaClass}")
        assertEquals(FULL_CHAIN_ID, id)
        assertEquals(36, id.length, "UUID textual form is 36 characters")
    }

    @Test
    fun `pools are read-only - every connection refuses writes (R5)`() {
        // The read-only guarantee of spec 5f (readOnlyMode=always) surfaces on the connection; this
        // check executes no SQL and the test itself never writes.
        listOf(
            "fueling" to dataSources.fuelingDatabase,
            "payment" to dataSources.paymentDatabase,
            "vendors" to dataSources.vendorsDatabase,
        ).forEach { (database, pool) ->
            pool.connection.use { connection ->
                assertTrue(connection.isReadOnly, "$database pool hands out read-only connections (R5)")
            }
        }
        assertEquals(DataSourceFactory.MAX_POOL_SIZE, dataSources.fuelingDatabase.maximumPoolSize)
    }

    /** Asserts that the non-null `created_at` values of [rows] are in descending order. */
    private fun assertNewestFirst(rows: List<Map<String, Any?>>, label: String) {
        val timestamps = rows.mapNotNull { it["created_at"] as? Long }
        assertEquals(timestamps.sortedDescending(), timestamps, "$label keeps non-null created_at descending")
    }

    private companion object {
        /** Fuels the full chain: payments (via user 2433) + orders + events (T3 verification). */
        const val FULL_CHAIN_ID = "b1625805-326f-48d7-811c-f91ee409eb82"

        /** Fueling whose user has zero payments — the empty-list case of D8. */
        const val EMPTY_PAYMENTS_ID = "99f068ca-ac6a-43fb-a53b-d2e7a573cfe2"

        /** Key present only in `vendor_fueling_orders` — the best-effort positive case of D9. */
        const val VENDOR_ONLY_ID = "gpn7a264-61ab-4622-983b-1ed62961a679"
    }
}
