package dev.thecampground.cloak.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import dev.thecampground.cloak.engine.CloakEngine
import dev.thecampground.cloak.engine.CloakScope
import dev.thecampground.cloak.external.CloakLibrary


/**
 * @property showFrameStats Whether or not to display the final frame stats, such as the longest frame, average frame time, and the breakdown of each frame
 */
class CloakAppOptions(
    val window: CloakWindowOptions = CloakWindowOptions(),
    val showFrameStats: Boolean = false,
)

class CloakWindowOptions(
    val width: Int = 800,
    val height: Int = 600,
    val title: String = "Cloak App",
    val className: String = "cloak_app",
)

@OptIn(InternalComposeUiApi::class)
@Suppress("unused")
fun cloakApp(options: CloakAppOptions = CloakAppOptions(), content: @Composable (CloakScope) -> Unit) {
    val cloakLib = CloakLibrary

    cloakLib.init(
        title = options.window.title,
        width = options.window.width,
        height = options.window.height,
        className = options.window.className
    )

    val engine = CloakEngine(cloakLib) {
        content(it)
    }

    if (options.showFrameStats) {
        println(engine.stats)
    }
}