package dev.thecampground.cloak.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.thecampground.cloak.engine.CloakEngine
import dev.thecampground.cloak.engine.CloakScope
import dev.thecampground.cloak.external.NativeExtractor
import dev.thecampground.cloak.external.glfw.GLFW
import dev.thecampground.cloak.external.glfw.GLFWPlatform
import jdk.internal.vm.vector.VectorSupport.store
import java.lang.foreign.Arena
import kotlin.io.path.pathString


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
class CloakViewModelStoreOwner() : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun clear() {
        viewModelStore.clear()
    }
}
@OptIn(InternalComposeUiApi::class)
@Suppress("unused")
fun cloakApp(options: CloakAppOptions = CloakAppOptions(), content: @Composable (CloakScope) -> Unit) {
    Arena.ofConfined().use { arena ->
        val glfw = GLFW.load(NativeExtractor.extract(NativeExtractor.GLFW3).pathString, platform = GLFWPlatform.Wayland(), arena)
        val window = glfw.createWindow(
            title = "Cloak App",
            width = options.window.width,
            height = options.window.height,
            arena = arena,
        )

        window.makeContextCurrent()
        val engine = CloakEngine(glfw, window) {
            val viewModelStoreOwner = remember { CloakViewModelStoreOwner() }

            CompositionLocalProvider(
                LocalViewModelStoreOwner provides viewModelStoreOwner
            ) {
                content(it)
            }
        }

        if (options.showFrameStats) {
            println(engine.stats)
        }
    }
}