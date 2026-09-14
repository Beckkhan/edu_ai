// src/main/kotlin/com/eduai/weather/db/WeatherLogRepository.kt
package com.eduai.weather.db

import java.sql.Timestamp
import javax.sql.DataSource

data class WeatherLogEntry(
    val id: Long,
    val city: String,
    val country: String,
    val temperature: Double,
    val description: String,
    val receivedAt: Timestamp,
)

/** Plain JDBC access to weather_log. No ORM. */
class WeatherLogRepository(private val ds: DataSource) {

    fun save(city: String, country: String, temperature: Double, description: String): Long =
        ds.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO weather_log (city, country, temperature, description, received_at) VALUES (?, ?, ?, ?, ?) RETURNING id"
            ).use { st ->
                st.setString(1, city)
                st.setString(2, country)
                st.setDouble(3, temperature)
                st.setString(4, description)
                st.setTimestamp(5, Timestamp(System.currentTimeMillis()))
                st.executeQuery().use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    fun listRecent(limit: Int = 20): List<WeatherLogEntry> =
        ds.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, city, country, temperature, description, received_at FROM weather_log ORDER BY received_at DESC LIMIT ?"
            ).use { st ->
                st.setInt(1, limit)
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                WeatherLogEntry(
                                    id = rs.getLong("id"),
                                    city = rs.getString("city"),
                                    country = rs.getString("country"),
                                    temperature = rs.getDouble("temperature"),
                                    description = rs.getString("description"),
                                    receivedAt = rs.getTimestamp("received_at"),
                                )
                            )
                        }
                    }
                }
            }
        }
}
