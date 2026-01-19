package dev.thecampground.cloak.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import dev.thecampground.cloak.external.CloakLibrary
import dev.thecampground.cloak.mpv.MPVCompat
import org.jetbrains.skia.DirectContext

typealias RenderSideEffect = (engine: CloakEngine, context: DirectContext) -> Boolean
typealias RenderSideEffectOnce = (engine: CloakEngine, context: DirectContext) -> Unit

val LocalCloakScope = staticCompositionLocalOf<CloakScope> { error("Local cloak scope not initialized, make sure you are starting CloakEngine, or using cloakApp composable.") }

interface IClipboard {
    fun setClipboard(bytes: ByteArray, mime: Array<String>)
    fun setClipboardText(text: String)
}

class CloakScope internal constructor(
    internal val engine: CloakEngine,
    internal val renderQueue: CloakEngine.RenderQueue,
) {
    val library: CloakLibrary.Companion = engine.cloak
    val clipboard: IClipboard = Clipboard()
    val mpvCompat = MPVCompat()

    fun quit() = engine.close()

    inner class Clipboard: IClipboard {
        private val lib = engine.cloak

        override fun setClipboard(bytes: ByteArray, mime: Array<String>) {
            lib.setClipboard(bytes, mime)
        }

        override fun setClipboardText(text: String) {
            lib.setClipboardText(text)
        }
    }
}

/**
 * Run a side effect on the MAIN thread, this is a blocking call.
 * Useful for side effects that need main thread context (Such as GL calls)
 */
@Composable
fun CloakScope.runOnceOnRenderThread(action: RenderSideEffectOnce) {
    DisposableEffect(action) {
        val dequeue = this@runOnceOnRenderThread.renderQueue.post { engine, context ->
            action(engine, context)
            false
        }

        onDispose {
            this@runOnceOnRenderThread.renderQueue.remove(dequeue)
        }
    }
}

/**
 * Run a side effect on the MAIN thread, this is a blocking call.
 * Useful for side effects that need main thread context (Such as GL calls)
 */
@Composable
fun CloakScope.runOnRenderThread(action: RenderSideEffect) {
    DisposableEffect(action) {
        this@runOnRenderThread.renderQueue.post(action)

        onDispose {
            println("Disposed")
            this@runOnRenderThread.renderQueue.remove(action)
        }
    }
}