package com.eduai.turbofa.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Provider {
    @SerialName("deepseek")
    DEEPSEEK,

    @SerialName("ollama")
    OLLAMA,
}
