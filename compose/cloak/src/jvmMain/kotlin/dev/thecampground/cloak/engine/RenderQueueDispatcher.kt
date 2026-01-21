package dev.thecampground.cloak.engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.skia.DirectContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext


class EngineElement(val engine: CloakEngine) :
    AbstractCoroutineContextElement(EngineElement) {
    companion object Key : CoroutineContext.Key<EngineElement>
}

class CloakScopeElement(val scope: CloakScope) :
    AbstractCoroutineContextElement(CloakScopeElement) {
    companion object Key : CoroutineContext.Key<CloakScopeElement>
}

val CoroutineScope.engine: CloakEngine
    get() = coroutineContext[EngineElement]?.engine
        ?: error("Engine not available in this coroutine")

val CoroutineScope.ctx: CloakScope
    get() = coroutineContext[CloakScopeElement]?.scope
        ?: error("DirectContext not available in this coroutine")

object RenderQueueDispatcher : CoroutineDispatcher() {
    // This will provide the current engine and context
    private var provider: (() -> Pair<CloakEngine, CloakScope>)? = null
    private var scopeProvider: (() -> CloakScope)? = null

    /** Call this once to set the active engine and context provider */
    fun init(provider: () -> Pair<CloakEngine, CloakScope>) {
        this.provider = provider
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val (engine, scope) = provider?.invoke()
            ?: throw IllegalStateException("RenderQueueDispatcher not initialized!")

        engine.renderQueue.post { _, _ ->
            // Wrap the block to automatically inject context elements
            val wrappedContext = context + EngineElement(engine) + CloakScopeElement(scope)
            block.run() // run the coroutine block
            true
        }
    }
}

