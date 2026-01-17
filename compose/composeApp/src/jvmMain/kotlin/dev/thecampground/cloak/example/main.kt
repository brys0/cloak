package dev.thecampground.cloak.example

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.InternalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import dev.thecampground.cloak.FpsCounter
import dev.thecampground.cloak.app.CloakAppOptions
import dev.thecampground.cloak.app.cloakApp
import dev.thecampground.cloak.engine.LocalCloakScope
import org.jetbrains.skia.BackendTexture
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.SurfaceOrigin

@OptIn(ExperimentalHazeMaterialsApi::class)
fun main() = cloakApp(
    options = CloakAppOptions(
        showFrameStats = true,
    )
) {
    var path by remember { mutableStateOf<String>("") }
    var playing by remember { mutableStateOf(false) }
    val hazeState = rememberHazeState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        if (playing) {
            VideoCanvas(path, Modifier.hazeSource(state = hazeState))
        }
        Column(modifier = Modifier.fillMaxWidth().hazeEffect(state = hazeState) {
            progressive = HazeProgressive.verticalGradient(startIntensity = 1.5f, endIntensity = 0f)
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FpsCounter( textStyle = TextStyle(color = Color.White))

                OutlinedTextField(onValueChange = { text -> path = text }, value = path, label = {
                    Text("Video Path or URL")
                })
            }

            Button(onClick = { playing = true }) {
                Text("Play Video")
            }
        }

    }
}


@Composable
fun VideoCanvas(path: String, modifier: Modifier = Modifier) {
    val scope = LocalCloakScope.current
    val mpv = remember { scope.library.mpvCreate() } // Create MPV context

    var currentSize = remember { IntSize.Zero }
    // Textures for MPV
    var backendTexture = remember<BackendTexture?> { null }
    var backendImage = remember<Image?> { null }

    var lastSize = remember { IntSize.Zero }

    LaunchedEffect(path) {
        scope.library.mpvLoad(mpv, path)
    }

    LaunchedEffect(mpv) {
        scope.onEngineDraw = { engine, context ->
            val flags = scope.library.mpvCheckUpdate(mpv)
            if (flags != 0L || lastSize != currentSize) {
                if (lastSize != currentSize) {
                    engine.cloak.mpvResizeTexture(mpv!!, currentSize.width, currentSize.height)
                    lastSize = currentSize
                }
                context.resetGLAll()

                if (backendTexture == null || lastSize != currentSize) {
                    backendTexture = BackendTexture.makeGL(
                        width = currentSize.width,
                        height = currentSize.height,
                        isMipmapped = false,
                        textureId = scope.library.mpvGetTexture(mpv),
                        textureTarget = 0x0DE1,
                        textureFormat = 0x881A
                    )

                    backendImage = Image.adoptTextureFrom(
                        context,
                        backendTexture!!,
                        SurfaceOrigin.BOTTOM_LEFT,
                        ColorType.RGBA_F16
                    )
                }

                scope.library.mpvRender(mpv, currentSize.width, currentSize.height)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            println("Disposed")
            scope.onEngineDraw = null

            backendTexture?.close()
            backendImage?.close()
        }
    }


    Canvas(modifier = Modifier.fillMaxSize().then(modifier)) {
        currentSize = drawContext.size.toIntSize()
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            backendImage?.let { nativeCanvas.drawImageRect(backendImage!!, org.jetbrains.skia.Rect(0f, 0f, drawContext.size.width, drawContext.size.height)) }
        }
    }
}


@OptIn(InternalFoundationApi::class)
@Preview
@Composable
fun SimpleTextFieldSample() {
    val content = remember { mutableStateOf("") }
    val cloak = LocalCloakScope.current

    TextField(
        value = content.value,
        onValueChange = { str: String ->
            println("onValueChange: $str")
            content.value = str
            cloak.clipboard.setClipboardText(str)
        },
        label = { Text("Label") },
    )
}
