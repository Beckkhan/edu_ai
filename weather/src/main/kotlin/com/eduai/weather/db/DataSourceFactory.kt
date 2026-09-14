// src/main/kotlin/com/eduai/weather/db/DataSourceFactory.kt
package com.eduai.weather.db

import com.eduai.weather.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

class DataSourceFactory(private val config: AppConfig) {

    fun create(): HikariDataSource =
        HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
        })
}
