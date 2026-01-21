package dev.thecampground.cloak.example.intent

import androidx.compose.ui.unit.IntSize

sealed interface PlayerIntent {

    object Play : PlayerIntent
    object Pause : PlayerIntent
    object OnRedraw : PlayerIntent
    object SetPositionFrozen : PlayerIntent

    data class SetPath(val path: String) : PlayerIntent
    object Start : PlayerIntent
    data class SetPosition(val position: Float) : PlayerIntent
    data class SetVideoSize(val size: IntSize) : PlayerIntent
}