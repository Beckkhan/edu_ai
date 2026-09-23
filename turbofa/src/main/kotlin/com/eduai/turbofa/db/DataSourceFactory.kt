// src/main/kotlin/com/eduai/turbofa/db/DataSourceFactory.kt
package com.eduai.turbofa.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * One read-only HikariCP pool per domain database of spec 5f (D7): `fueling`, `payment` and
 * `vendors`.
 *
 * The three JDBC URLs are input, not derived here: AppConfig builds them from DB_HOST / DB_PORT
 * and the fixed database names (E3, D7), so URL derivation lives with the rest of the environment
 * configuration. The .env `DB_URL` — the `postgres` admin database, which holds none of the domain
 * tables — never reaches this factory.
 *
 * Pools are created eagerly, so a wrong URL or credential fails at startup instead of on the first
 * query. `readOnlyMode=always` makes every session read-only on the server side, which turns the
 * R5 promise into a guarantee: even a stray write could not execute. Hikari's own
 * [HikariConfig.setReadOnly] flag alone stays client-side with the PostgreSQL driver.
 *
 * @param fuelingJdbcUrl JDBC URL of the `fueling` database (table `fuelings`)
 * @param paymentJdbcUrl JDBC URL of the `payment` database (table `payments`)
 * @param vendorsJdbcUrl JDBC URL of the `vendors` database (tables `fueling_orders`,
 *   `fueling_events`, `vendor_fueling_orders`)
 * @param user DB_USER
 * @param password DB_PASSWORD (never logged, never part of a URL)
 * @param maxPoolSize connections per database (spec 5f: 5)
 */
class DataSourceFactory(
    fuelingJdbcUrl: String,
    paymentJdbcUrl: String,
    vendorsJdbcUrl: String,
    private val user: String,
    private val password: String,
    private val maxPoolSize: Int = MAX_POOL_SIZE,
) : AutoCloseable {

    /** Pool for the `fueling` database (query 1 of 5f). */
    val fuelingDatabase: HikariDataSource = pool(fuelingJdbcUrl)

    /** Pool for the `payment` database (query 2 of 5f). */
    val paymentDatabase: HikariDataSource = pool(paymentJdbcUrl)

    /** Pool for the `vendors` database (queries 3-5 of 5f). */
    val vendorsDatabase: HikariDataSource = pool(vendorsJdbcUrl)

    /** Closes the three pools; call on application shutdown. */
    override fun close() {
        fuelingDatabase.close()
        paymentDatabase.close()
        vendorsDatabase.close()
    }

    private fun pool(jdbcUrl: String): HikariDataSource {
        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = user
        config.password = password
        config.driverClassName = POSTGRES_DRIVER
        config.maximumPoolSize = maxPoolSize
        config.isReadOnly = true
        config.addDataSourceProperty("readOnlyMode", "always")
        return HikariDataSource(config)
    }

    companion object {
        /** Pool size per database (spec 5f). */
        const val MAX_POOL_SIZE = 5

        /** JDBC driver of the PostgreSQL server. */
        const val POSTGRES_DRIVER = "org.postgresql.Driver"
    }
}
