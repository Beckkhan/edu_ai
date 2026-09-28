package com.eduai.turbofa.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

/**
 * One read-only HikariCP pool per domain database (D7) with an Exposed Database on each.
 * Uses readOnlyMode=always for server-side read-only guarantee.
 */
class DataSourceFactory(
    fuelingJdbcUrl: String,
    paymentJdbcUrl: String,
    vendorsJdbcUrl: String,
    private val user: String,
    private val password: String,
    private val maxPoolSize: Int = MAX_POOL_SIZE,
) : AutoCloseable {
    val fuelingPool: HikariDataSource = pool(fuelingJdbcUrl)

    val paymentPool: HikariDataSource = pool(paymentJdbcUrl)

    val vendorsPool: HikariDataSource = pool(vendorsJdbcUrl)

    val fuelingDatabase: Database = Database.connect(fuelingPool)

    val paymentDatabase: Database = Database.connect(paymentPool)

    val vendorsDatabase: Database = Database.connect(vendorsPool)

    override fun close() {
        fuelingPool.close()
        paymentPool.close()
        vendorsPool.close()
    }

    private fun pool(jdbcUrl: String): HikariDataSource {
        val config = HikariConfig()
        config.jdbcUrl = jdbcUrl
        config.username = user
        config.password = password
        config.driverClassName = POSTGRES_DRIVER
        config.maximumPoolSize = maxPoolSize
        config.isReadOnly = true
        // readOnlyMode=always is server-side (R5); Hikari's isReadOnly flag alone stays client-side.
        config.addDataSourceProperty("readOnlyMode", "always")
        return HikariDataSource(config)
    }

    companion object {
        const val MAX_POOL_SIZE = 5

        const val POSTGRES_DRIVER = "org.postgresql.Driver"
    }
}
