package com.eduai.turbofa.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * One read-only HikariCP pool per domain database of spec 5f (D7): `fueling`, `payment` and
 * `vendors`.
 *
 * Pools are created eagerly, so a wrong URL or credential fails at startup instead of on the first
 * query. `readOnlyMode=always` makes every session read-only on the server side (R5), a guarantee
 * Hikari's own [HikariConfig.setReadOnly] flag cannot give: that flag stays client-side with the
 * PostgreSQL driver.
 */
class DataSourceFactory(
    fuelingJdbcUrl: String,
    paymentJdbcUrl: String,
    vendorsJdbcUrl: String,
    private val user: String,
    private val password: String,
    private val maxPoolSize: Int = MAX_POOL_SIZE,
) : AutoCloseable {

    val fuelingDatabase: HikariDataSource = pool(fuelingJdbcUrl)

    val paymentDatabase: HikariDataSource = pool(paymentJdbcUrl)

    val vendorsDatabase: HikariDataSource = pool(vendorsJdbcUrl)

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
        const val MAX_POOL_SIZE = 5

        const val POSTGRES_DRIVER = "org.postgresql.Driver"
    }
}
