package dev.thecampground.cloak.example

import dev.thecampground.cloak.app.CloakAppOptions
import dev.thecampground.cloak.app.cloakApp
import dev.thecampground.cloak.example.presentation.player.PlayerScreen


fun main() = cloakApp(
    options = CloakAppOptions(
        showFrameStats = true,
    )
) {
    PlayerScreen()
}