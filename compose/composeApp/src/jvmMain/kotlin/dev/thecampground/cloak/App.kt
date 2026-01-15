package dev.thecampground.cloak

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import cloak.composeapp.generated.resources.Res
import cloak.composeapp.generated.resources.compose_multiplatform

private const val UPDATE_FPS_EVERY_MS = 60

@Composable
fun FpsCounter(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    var fps by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        var frameCount = 0
        var lastTimeNs = 0L

        while (true) {
            withFrameNanos { timeNs ->
                if (lastTimeNs == 0L) {
                    lastTimeNs = timeNs
                }

                frameCount++

                val elapsedNs = timeNs - lastTimeNs
                if (elapsedNs >= 1_000_000_000L) {
                    fps = frameCount
                    frameCount = 0
                    lastTimeNs = timeNs
                }

            }
        }
    }

    Text(
        text = "$fps FPS",
        modifier = modifier,
        fontSize = 32.sp,
        style = textStyle
    )
}

@Composable
fun DebugOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(8.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        FpsCounter()
    }
}
@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        val exampleContentString = rememberTextFieldState("Example")
        val focusRequester = remember { FocusRequester() }

        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DebugOverlay()

            TextField(state = exampleContentString, modifier = Modifier
                .focusRequester(focusRequester)) // Attach the requester)
            Button(onClick = {
                showContent = !showContent
            }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { "Linux" }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}