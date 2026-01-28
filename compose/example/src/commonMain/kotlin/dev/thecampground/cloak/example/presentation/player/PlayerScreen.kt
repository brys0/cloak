package dev.thecampground.cloak.example.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cloak_project.example.generated.resources.Res
import cloak_project.example.generated.resources.pause_fill
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.thecampground.cloak.example.intent.PlayerIntent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.skia.Rect
import kotlin.math.floor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(playerViewModel: PlayerViewModel = viewModel()) {
    val state by playerViewModel.state.collectAsState()
    // This state is only to trigger recomposition
    val frameTicker = remember { mutableStateOf(0L) }
    var sliderValue by remember { mutableStateOf(state.progress) }
    val hazeState = rememberHazeState()
    val alpha by animateFloatAsState(if (state.playing) 0f else 0.8f)
    val interactionSource = remember { MutableInteractionSource() }

    // This launches a coroutine that updates every frame
    LaunchedEffect(Unit) {

        while (true) {
            withFrameNanos { frameTime ->
                frameTicker.value = frameTime // forces Compose to recompose
            }
        }
    }

    LaunchedEffect(state.progress) {
        // Only update the slider if the user is not actively dragging
        if (sliderValue != state.progress) {
            sliderValue = state.progress
        }
    }

    if (!state.started) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(state.path, onValueChange = { playerViewModel.onIntent(PlayerIntent.SetPath(it)) })
                Button(onClick = { playerViewModel.onIntent(PlayerIntent.Start) }) {
                    Text("Start Video")
                }
            }

        }
    } else {
        Box(modifier = Modifier.fillMaxSize().onSizeChanged {
            playerViewModel.onIntent(
                PlayerIntent.SetVideoSize(it)
            )
        }.clickable(interactionSource = interactionSource, indication = null) {
                when (state.playing) {
                    true -> playerViewModel.onIntent(PlayerIntent.Pause)
                    false -> playerViewModel.onIntent(PlayerIntent.Play)
                }
            }, contentAlignment = Alignment.BottomStart) {
            Canvas(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
                val x = frameTicker.value // read the state to invalidate draw
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    playerViewModel.onIntent(PlayerIntent.OnRedraw)
                    playerViewModel.image?.let { image ->
                        nativeCanvas.drawImageRect(
                            image,
                            Rect(0f, 0f, size.width, size.height),
                        )
                    }
                }


            }
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)), contentAlignment = Alignment.Center) {
                AnimatedVisibility(!state.playing, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
                    Icon(
                        painterResource(Res.drawable.pause_fill),
                        contentDescription = "Paused",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                Column(modifier = Modifier.hazeEffect(hazeState) {
                    progressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 8f)
                    noiseFactor = 0f
                }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTimestamp(state.position), color = Color.White)
                            Text(formatTimestamp(state.duration), color = Color.White)
                        }
                        Slider(
                            value = sliderValue,
                            onValueChange = { value ->
                                sliderValue = value
                                playerViewModel.onIntent(PlayerIntent.SetPositionFrozen)
                            },
                            onValueChangeFinished = {
                                playerViewModel.onIntent(PlayerIntent.SetPosition(sliderValue))
                            }
                        )
                    }

                }



            }
//            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
//                FpsCounter( textStyle = TextStyle(color = Color.White))
////
//
//            }
        }
    }
}

fun Int.padStart(pad: Int): String {
    return this.toString().padStart(pad, padChar = '0')
}

fun formatTimestamp(milliseconds: Long): String {
    var seconds = floor((milliseconds / 1000).toDouble())
    var minutes = floor((seconds / 60))
    var hours = floor((minutes / 60))

    seconds %= 60
    minutes %= 60
    hours %= 24

    return "${hours.toInt().padStart(2)}:${minutes.toInt().padStart(2)}:${seconds.toInt().padStart(2)}"

}
