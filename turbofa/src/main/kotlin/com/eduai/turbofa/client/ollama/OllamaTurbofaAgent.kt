package com.eduai.turbofa.client.ollama

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import com.eduai.turbofa.client.deepseek.KoogTurbofaAgent
import com.eduai.turbofa.client.deepseek.TurbofaAgent
import com.eduai.turbofa.db.FuelingDataSource
import com.eduai.turbofa.logging.RequestLogger

class OllamaTurbofaAgent(
    executor: PromptExecutor,
    model: LLModel,
    dataSource: FuelingDataSource,
    requestLogger: RequestLogger,
) : TurbofaAgent by KoogTurbofaAgent(executor, model, dataSource, requestLogger)
