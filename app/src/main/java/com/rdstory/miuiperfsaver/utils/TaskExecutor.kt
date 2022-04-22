package com.rdstory.miuiperfsaver.utils

import java.util.concurrent.Executor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object TaskExecutor {
    private val threadPool: Executor = ThreadPoolExecutor(
        2, Int.MAX_VALUE,
        60L, TimeUnit.SECONDS,
        SynchronousQueue()
    )

    fun execute(action: () -> Unit) {
        threadPool.execute(action)
    }
}