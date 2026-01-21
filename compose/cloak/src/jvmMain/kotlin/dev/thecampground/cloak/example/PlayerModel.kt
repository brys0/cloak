package dev.thecampground.cloak.example

import androidx.lifecycle.ViewModel

data class PlayerState(
    val path: String,
    val progress: Float,
    val duration: Int,
)

class PlayerModel: ViewModel() {
}