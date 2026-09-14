// src/main/kotlin/com/eduai/weather/tool/ToolRegistry.kt
package com.eduai.weather.tool

class ToolRegistry(private val tools: List<Tool>) {
    fun get(name: String): Tool? = tools.firstOrNull { it.name == name }
}
