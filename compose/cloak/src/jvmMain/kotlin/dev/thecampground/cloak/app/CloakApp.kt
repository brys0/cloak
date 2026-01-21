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
import dev.thecampground.cloak.external.CloakLibrary
import jdk.internal.vm.vector.VectorSupport.store


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
    val cloakLib = CloakLibrary

    cloakLib.init(
        title = options.window.title,
        width = options.window.width,
        height = options.window.height,
        className = options.window.className
    )

    val engine = CloakEngine(cloakLib) {
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