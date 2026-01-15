package dev.thecampground.cloak.example

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.InternalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.thecampground.cloak.app.CloakAppOptions
import dev.thecampground.cloak.app.cloakApp
import dev.thecampground.cloak.engine.CloakScope
import dev.thecampground.cloak.engine.LocalCloakScope
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun main() = cloakApp(
    options = CloakAppOptions(
        showFrameStats = true,
    )
) {
    val scope = LocalCloakScope.current

    Column {
        Button(onClick = {
            testPinkClipboard(scope)
        }) {
            Text(text = "Copy Image PNG")
        }
        SimpleTextFieldSample()
        Button(onClick = {
            scope.quit()
        }) {
            Text(text = "Quit")
        }
    }
}


fun createImageGradient(width: Int = 512, height: Int = 512, format: String = "png"): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    for (y in 0 until height) {
        for (x in 0 until width) {
            val red = (255 * x / width)
            val green = (255 * y / height)
            val blue = 128
            val rgb = (red shl 16) or (green shl 8) or blue
            image.setRGB(x, y, rgb)
        }
    }

    // Two memory copies, lol.
    val outputStream = ByteArrayOutputStream()
    ImageIO.write(image, format, outputStream)
    return outputStream.toByteArray()
}

// Test it:
fun testPinkClipboard(scope: CloakScope) {
    scope.clipboard.setClipboard(createImageGradient(512, 512, "png"), arrayOf("image/png"))
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
