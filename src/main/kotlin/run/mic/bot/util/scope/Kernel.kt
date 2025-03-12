/*
 * Copyright (c) 2023-2025 CuteMic 保留所有权利。 All Right Reserved.
 */

package run.mic.bot.util.scope

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.Executors
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

internal object KernelScope : CoroutineScope {
    override val coroutineContext: CoroutineContext = KernelScheduler + CoroutineName("JuJiuBot-KernelScope")
}

internal object KernelScheduler : CoroutineDispatcher() {
    private val singlePoolExecutor = Executors.newSingleThreadExecutor { Thread(it, "JuJiuBot-Kernel") }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        singlePoolExecutor.submit(block)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T : Any, R> T.runSynchronized(block: T.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return synchronized(this@runSynchronized) {
        block.invoke(this@runSynchronized)
    }
}