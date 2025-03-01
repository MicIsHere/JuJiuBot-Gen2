package cn.cutemic.bot.util.scope

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

internal object TaskScope : CoroutineScope {
    override val coroutineContext: CoroutineContext = TaskScheduler + CoroutineName("JuJiuBot-TaskScope")
}

internal object TaskScheduler : CoroutineDispatcher() {
    private val singlePoolExecutor = Executors.newSingleThreadExecutor { Thread(it, "JuJiuBot-Task") }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        singlePoolExecutor.submit(block)
    }
}
