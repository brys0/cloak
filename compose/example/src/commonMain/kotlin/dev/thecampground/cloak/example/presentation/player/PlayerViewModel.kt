package dev.thecampground.cloak.example.presentation.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.thecampground.cloak.engine.GlobalCloakScope
import dev.thecampground.cloak.engine.RenderQueueDispatcher
import dev.thecampground.cloak.engine.RenderSideEffect
import dev.thecampground.cloak.engine.ctx
import dev.thecampground.cloak.engine.engine
import dev.thecampground.cloak.engine.runOnRenderThread
import dev.thecampground.cloak.example.intent.PlayerIntent
import dev.thecampground.cloak.mpv.MPVCompat
import dev.zt64.mpvkt.Mpv
import dev.zt64.mpvkt.MpvEvent
import dev.zt64.mpvkt.MpvLogLevel
import dev.zt64.mpvkt.command
import dev.zt64.mpvkt.getPropertyDouble
import dev.zt64.mpvkt.getPropertyLong
import dev.zt64.mpvkt.getPropertyString
import dev.zt64.mpvkt.observeProperty
import dev.zt64.mpvkt.render.MpvRenderApiType
import dev.zt64.mpvkt.render.MpvRenderContext
import dev.zt64.mpvkt.render.MpvRenderFrameInfoFlag
import dev.zt64.mpvkt.render.RenderParam
import dev.zt64.mpvkt.render.renderContextCreate
import dev.zt64.mpvkt.setOption
import dev.zt64.mpvkt.setProperty
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import org.jetbrains.skia.BackendTexture
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.SurfaceOrigin
import java.awt.image.renderable.RenderContext

@Immutable
data class PlayerState(
    val started: Boolean = false,
    val playing: Boolean = false,
    val positionFrozen: Boolean = false,
    val progress: Float = 0f,
    val position: Long = 0L,
    val duration: Long = 0L,
    val path: String = "",
    val needsRedraw: Boolean = false,
    val loading: Boolean = false,
    val currentSize: IntSize = IntSize.Zero,
)

class PlayerViewModel : ViewModel() {
    private val _state = MutableStateFlow(PlayerState())
    private lateinit var _mpv: Mpv
    private lateinit var _compat: MPVCompat
    private var _renderContext: MpvRenderContext? = null
    private var _renderFunc: RenderSideEffect? = null
    private var lastSize: IntSize = IntSize.Zero

    var texture: BackendTexture? = null
    var image: Image? = null

    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun onIntent(intent: PlayerIntent) {
        when(intent) {
            is PlayerIntent.Play -> play()

            PlayerIntent.Pause -> pause()
            is PlayerIntent.SetPosition -> {
                val newPosition = (_state.value.duration * intent.position)
                val newPositionSeconds = newPosition / 1000

                println("newPositionSeconds: $newPositionSeconds $newPosition")
                _state.value = _state.value.copy(progress = intent.position, position = newPosition.toLong())
                GlobalCloakScope.scope.runOnRenderThread { engine, context ->
                    _mpv.setProperty("time-pos", newPositionSeconds.toDouble())
                    _state.value = _state.value.copy(positionFrozen = false)
                    false
                }
            }
            is PlayerIntent.Start -> start()
            is PlayerIntent.SetPath -> setPath(intent.path)
            is PlayerIntent.SetVideoSize -> setVideoSize(intent.size)
            is PlayerIntent.OnRedraw -> {
                _state.value = _state.value.copy(needsRedraw = false)
            }

            PlayerIntent.SetPositionFrozen -> {
                _state.value = _state.value.copy(positionFrozen = true)
            }
        }
    }

    private fun setVideoSize(size: IntSize) {
        _state.value = _state.value.copy(currentSize = size)
    }
    private fun setPath(path: String) {
        _state.value = _state.value.copy(path = path)
    }
    private fun play() {
        GlobalCloakScope.scope.runOnRenderThread { engine, context ->
            _mpv.setProperty("pause", false)
            _state.value = _state.value.copy(playing = true)

            false
        }
    }

    private fun pause() {
        GlobalCloakScope.scope.runOnRenderThread { engine, context ->
            _mpv.setProperty("pause", true)
            _state.value = _state.value.copy(playing = false)

            false
        }
    }

    private fun start() {
        GlobalCloakScope.scope.runOnRenderThread { engine, context ->
            println("load file")
            _mpv.command("loadfile", _state.value.path)
            false
        }

        _renderFunc = GlobalCloakScope.scope.runOnRenderThread { engine, context ->
            val event = _mpv.waitEvent(0)

            if (event is MpvEvent.EndFile) {
                println("end file")

                _state.value = _state.value.copy(started = false)
                return@runOnRenderThread false
            }

            if (_state.value.started && !_state.value.positionFrozen) {
                try {
                    val positionMilli = ((_mpv.getPropertyDouble("time-pos/full") ?: 0.toDouble()) * 1000)
                    val progress: Double = positionMilli / _state.value.duration

                    // Get position
                    _state.value = _state.value.copy(position = positionMilli.toLong(), progress = progress.toFloat())
                } catch (e: Exception) {
                    println("error: ${e.message}")
                }

            }

            if (event == MpvEvent.FileLoaded) {
                _state.value = _state.value.copy(started = true, playing = true)
                println("file loaded and started playback")

                _mpv.getPropertyDouble("duration/full")?.let { duration ->
                    val durationMilli = duration * 1000

                    _state.value = _state.value.copy(duration = durationMilli.toLong())
                }

                _mpv.observeProperty<Double>("time-pos/full") { timePos ->
                    println("time pos: $timePos")
                }
            }
            _renderContext?.let { render ->
                val currentState = this._state.value
                val flags = render.update()

                if (flags != 0.toULong() || lastSize != currentState.currentSize) {
                    _state.value = _state.value.copy(needsRedraw = true)
                    if (flags != 0.toULong() || lastSize != _state.value.currentSize || texture == null || image == null) {
                        if (lastSize != currentState.currentSize) {
                            _compat.resizeMPVTexture(currentState.currentSize.width, currentState.currentSize.height, _compat.renderContext!!.textureFormat)
                            lastSize = currentState.currentSize
                        }
                        context.resetGLAll()

                        if (texture == null || lastSize != currentState.currentSize) {
                            texture = BackendTexture.makeGL(
                                width = lastSize.width,
                                height = lastSize.height,
                                isMipmapped = false,
                                textureId = _compat.renderContext!!.textureID,
                                textureTarget = 0x0DE1,
                                textureFormat = _compat.renderContext!!.textureFormat,
                            )
                            image = Image.adoptTextureFrom(
                                context,
                                backendTexture = texture!!,
                                SurfaceOrigin.BOTTOM_LEFT,
                                ColorType.RGBA_F16
                            )
                        }


                        _renderContext?.let { renderContext ->
                            renderContext.render(
                                params = listOf(
                                    RenderParam.Companion.OpenGLFBO(
                                        fbo = _compat.renderContext!!.framebuffer,
                                        w = lastSize.width,
                                        h = lastSize.height,
                                        internalFormat = _compat.renderContext!!.textureFormat,
                                    ),
                                    RenderParam.Companion.FlipY(true)
                                )
                            )
                        }

                        _compat.flush()
                    }
                }
            }

            true
        }


    }

    private fun init() = GlobalCloakScope.scope.runOnRenderThread { engine, ctx ->
        val procAddress = GlobalCloakScope.scope.library.getProcAddress()
        val context = GlobalCloakScope.scope.library.getCurrentContext() ?: 0

        _compat.createRenderContext(context = engine.contextPointer, format = 0x881A)
        _mpv.requestLogMessages(MpvLogLevel.WARN)
        _mpv.setOption("hwdec", "auto")
        _mpv.setOption("vo", "libmpv")
        _mpv.setOption("video-timing-offset", "0")
        _mpv.setOption("vf", "format=rgba64")
        _mpv.observeProperty<Boolean>("pause") {
            println("Pausing $it")
            _state.value = _state.value.copy(playing = it)
        }
        _mpv.init()

        _renderContext = _mpv.renderContextCreate(
            MpvRenderApiType.OPENGL,
            listOf(
                RenderParam.Companion.OpenGLInitParams(
                    getProcAddress = procAddress,
                    getProcAddressCtx = context
                )
            )
        )
         false
    }

    init {
        GlobalCloakScope.scope.runOnRenderThread { engine, context ->
            _mpv = Mpv()
            _compat = MPVCompat()

            false
        }
        this.init()
    }
}