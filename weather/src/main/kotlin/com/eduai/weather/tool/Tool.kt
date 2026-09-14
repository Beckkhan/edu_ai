// src/main/kotlin/com/eduai/weather/tool/Tool.kt
package com.eduai.weather.tool

interface Tool {
    val name: String
    val description: String
    suspend fun execute(input: String): String
}
