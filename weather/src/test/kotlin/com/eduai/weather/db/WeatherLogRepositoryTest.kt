// src/test/kotlin/com/eduai/weather/db/WeatherLogRepositoryTest.kt
//
// !!! REQUIRED BEFORE RUNNING THIS TEST !!!
// The DB volume still holds the old schema (no country column). Reset it so the
// new schema (city, country, temperature, description) is applied:
//     docker compose down -v && docker compose up -d
package com.eduai.weather.db

import org.postgresql.ds.PGSimpleDataSource
import java.sql.Timestamp
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Integration test: requires the Docker Postgres from docker-compose.yml to be running. */
class WeatherLogRepositoryTest {

    private lateinit var ds: PGSimpleDataSource
    private lateinit var repo: WeatherLogRepository
    private var savedId: Long = -1

    @BeforeTest
    fun setUp() {
        ds = PGSimpleDataSource().apply {
            setURL(System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/weather")
            user = System.getenv("DB_USER") ?: "weather"
            password = System.getenv("DB_PASSWORD") ?: "weather"
        }
        repo = WeatherLogRepository(ds)
    }

    @Test
    fun `saves and retrieves weather with country temperature description and receivedAt`() {
        val receivedAt = Timestamp(System.currentTimeMillis())
        savedId = repo.save("Moscow", "Russia", -2.5, "Clear", receivedAt)

        val saved = repo.listRecent(50).firstOrNull { it.id == savedId }
        assertNotNull(saved, "saved row must be retrievable")
        assertEquals("Moscow", saved.city)
        assertEquals("Russia", saved.country)
        assertEquals(-2.5, saved.temperature)
        assertEquals("Clear", saved.description)
        // received_at must round-trip exactly (D5/T8), not be replaced by the DB default
        assertEquals(receivedAt.time, saved.receivedAt.time)
    }

    @AfterTest
    fun cleanUp() {
        ds.connection.use { conn ->
            conn.prepareStatement("DELETE FROM weather_log WHERE id = ?").use { st ->
                st.setLong(1, savedId)
                st.executeUpdate()
            }
        }
    }
}
