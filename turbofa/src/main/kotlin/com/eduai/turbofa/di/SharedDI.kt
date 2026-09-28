package com.eduai.turbofa.di

import org.kodein.di.DI

object SharedDI {

    lateinit var context: DI
        private set

    fun init(context: DI) {
        this.context = context
    }
}
