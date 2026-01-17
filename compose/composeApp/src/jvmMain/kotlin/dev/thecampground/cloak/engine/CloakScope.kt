package dev.thecampground.cloak.engine

import androidx.compose.runtime.staticCompositionLocalOf
import dev.thecampground.cloak.external.CloakLibrary
import org.jetbrains.skia.DirectContext


val LocalCloakScope = staticCompositionLocalOf<CloakScope> { error("Local cloak scope not initialized, make sure you are starting CloakEngine, or using cloakApp composable.") }

interface IClipboard {
    fun setClipboard(bytes: ByteArray, mime: Array<String>)
    fun setClipboardText(text: String)
}

class CloakScope internal constructor(
    internal val engine: CloakEngine,
) {
    var onEngineDraw: ((engine: CloakEngine, context: DirectContext) -> Unit)? = null
    val library: CloakLibrary.Companion = engine.cloak
    val clipboard: IClipboard = Clipboard()

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