package dev.thecampground.cloak.example.presentation.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import dev.thecampground.cloak.engine.GlobalCloakScope
import dev.thecampground.cloak.engine.RenderSideEffect
import dev.thecampground.cloak.example.intent.PlayerIntent
import dev.thecampground.cloak.external.glfw.GLFWCallbacks
import dev.thecampground.cloak.external.opengl.FBO
import dev.thecampground.cloak.external.opengl.OpenGlEnum
import dev.thecampground.cloak.external.opengl.Texture
import dev.zt64.mpvkt.Mpv
import dev.zt64.mpvkt.MpvEvent
import dev.zt64.mpvkt.MpvLogLevel
import dev.zt64.mpvkt.command
import dev.zt64.mpvkt.getPropertyDouble
import dev.zt64.mpvkt.getPropertyFlag
import dev.zt64.mpvkt.observeProperty
import dev.zt64.mpvkt.render.MpvRenderApiType
import dev.zt64.mpvkt.render.MpvRenderContext
import dev.zt64.mpvkt.render.RenderParam
import dev.zt64.mpvkt.render.renderContextCreate
import dev.zt64.mpvkt.setOption
import dev.zt64.mpvkt.setProperty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.BackendTexture
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.SurfaceOrigin

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
//    private lateinit var _compat: MPVCompat
    private val gl = GlobalCloakScope.scope.glfw.gl!!
    private var _renderContext: MpvRenderContext? = null
    private var _renderFunc: RenderSideEffect? = null
    private var lastSize: IntSize = IntSize.Zero

    private var fbo: FBO? = null
    private var tex: Texture? = null

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
                GlobalCloakScope.scope.runOnRenderThread { _, _ ->
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
        GlobalCloakScope.scope.runOnRenderThread { _, _ ->
            _mpv.setProperty("pause", false)
            _state.value = _state.value.copy(playing = true)
            if (_mpv.getPropertyFlag("pause") ?: false) {
                return@runOnRenderThread true
            }

            false
        }
    }

    private fun pause() {
        GlobalCloakScope.scope.runOnRenderThread { _, _ ->
            _mpv.setProperty("pause", true)
            _state.value = _state.value.copy(playing = false)

            false
        }
    }

    private fun start() {
        GlobalCloakScope.scope.runOnRenderThread { _, _ ->
            println("load file")
            _mpv.command("loadfile", _state.value.path)
            false
        }

        _renderFunc = GlobalCloakScope.scope.runOnRenderThread { _, context ->
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
                              gl.glBindTexture(tex!!)
                              gl.glTexImage2D(0, 0x881A, currentState.currentSize.width, currentState.currentSize.height, 0, 0x1908)
//                            _compat.resizeMPVTexture(currentState.currentSize.width, currentState.currentSize.height, _compat.renderContext!!.textureFormat)
                            lastSize = currentState.currentSize
                        }
                        context.resetGLAll()

                        if (texture == null || lastSize != currentState.currentSize) {
                            texture = BackendTexture.makeGL(
                                width = lastSize.width,
                                height = lastSize.height,
                                isMipmapped = false,
                                textureId = tex!!.value,
                                textureTarget = 0x0DE1,
                                textureFormat = 0x881A,
                            )
                            image = Image.adoptTextureFrom(
                                context,
                                backendTexture = texture!!,
                                SurfaceOrigin.BOTTOM_LEFT,
                                ColorType.RGBA_F16
                            )
                        }


                        _renderContext?.render(
                            params = listOf(
                                RenderParam.Companion.OpenGLFBO(
                                    fbo = fbo!!.value,
                                    w = lastSize.width,
                                    h = lastSize.height,
                                    internalFormat = tex!!.value,
                                ),
                                RenderParam.Companion.FlipY(true)
                            )
                        )

                        gl.glFlush()
                    }
                }
            }

            true
        }


    }

    private fun init() = GlobalCloakScope.scope.runOnRenderThread { _, _ ->
        val procAddress = GLFWCallbacks.getProcAddressStub.address()
        val context = GlobalCloakScope.scope.glfw.getCurrentContext()

        fbo = gl.glGenFramebuffers(1)
        gl.glBindFramebuffer(fbo!!)

        tex = gl.glGenTextures(1)
        gl.glBindTexture(tex!!)
        gl.glTexImage2D(0, 0x881A, 512, 512, 0, 0x1908)
        gl.glTexParameteri(OpenGlEnum.GL_TEXTURE_MIN_FILTER, OpenGlEnum.GL_LINEAR)
        gl.glTexParameteri(OpenGlEnum.GL_TEXTURE_MAG_FILTER, OpenGlEnum.GL_LINEAR)
        gl.glFramebufferTexture2D(OpenGlEnum.GL_COLOR_ATTACHMENT0, tex!!, 0)

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

    override fun onCleared() {
        gl.glDeleteFramebuffers(1, fbo!!)
        gl.glDeleteTextures(1, tex!!)

        super.onCleared()
    }
    init {
        GlobalCloakScope.scope.runOnRenderThread { _, _ ->
            _mpv = Mpv()

            false
        }
        this.init()
    }
}