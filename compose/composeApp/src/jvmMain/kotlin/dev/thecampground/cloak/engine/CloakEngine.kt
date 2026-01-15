package dev.thecampground.cloak.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.thecampground.cloak.external.CloakLibrary
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.GLAssembledInterface
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.jetbrains.skia.impl.NativePointer
import org.jetbrains.skia.makeGLWithInterface
import org.jetbrains.skiko.currentNanoTime
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.measureTime


@OptIn(InternalComposeUiApi::class)
internal class CloakEngine
    @OptIn(InternalComposeUiApi::class, DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    constructor(
        internal val cloak: CloakLibrary.Companion,
        contextPointer: NativePointer = cloak.getCurrentContext()!!,
        procAddressPointer: NativePointer = cloak.getProcAddress(),
        dispatcher: CoroutineContext = Dispatchers.Main,
        private val content: @Composable (CloakScope) -> Unit,
    ) { 
    // When true, the application will safely shut down   
    private var shouldClose = false 
    // Force at least one frame to render at startup
    private var isDirty = true
    private var size = cloak.getFramebufferSize()
    var stats = EngineFrameStats()

    private val scope = CloakScope(engine = this)
//    private val platformContext: ComposeSceneContext = CloakSceneContext()
    private val context = DirectContext.makeGLWithInterface(
        assembledInterface = GLAssembledInterface.createFromNativePointers(
            ctxPtr = contextPointer,
            fPtr = procAddressPointer,
        ),
    )
    internal val composeSceneContext = ComposeSceneContext.Empty

    @OptIn(InternalComposeUiApi::class)
    val scene = PlatformLayersComposeScene(
        size = size,
        coroutineContext = dispatcher,
        composeSceneContext = composeSceneContext
    )

    private val inputHandler = CloakInputHandler(
        cloak = this.cloak,
        scene = this.scene,
        onResize = { size ->
            this.size = size
        },
        composeSceneContext = composeSceneContext,
        onDirty = {
            this.isDirty = true
        }
    )

    private fun startFrame() {
        val frameTime = measureTime {
            val pollingTime = measureTime {
                this.cloak.pollWindowEvents()
            }

            val inputTime = measureTime {
                this.inputHandler.pollInputEvents()
            }

            // Render the UI
            val drawTime = measureTime {
                if (this.shouldRender()) {
                    this.draw() // Draw the frame to screen!
                    this.stats.incrementDrawFrame()
                }
            }

            this.stats.addTotalSubframeTime(
                drawDuration = drawTime,
                inputDuration = inputTime,
                pollDuration = pollingTime,
            )
        } // After frame

        this.stats.addTotalFrameTime(frameTime)
    }

    @OptIn(InternalComposeUiApi::class)
    private fun shouldRender(): Boolean {
        return this.isDirty || this.scene.hasInvalidations()
    }
    /**
     * This should only handle drawing of the frame to the window.
     */
    @OptIn(InternalComposeUiApi::class)
    private fun draw() {
        this.cloak.makeContextCurrent()

        val renderTarget = BackendRenderTarget.makeGL(
            width = size.width,
            height = size.height,
            sampleCnt = 0,
            stencilBits = 8,
            fbId = 0, // Default framebuffer
            fbFormat = 0x8058 // GR_GL_RGBA8
        )

        val surface = Surface.makeFromBackendRenderTarget(
            this.context,
            renderTarget,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.sRGB
        )

        val canvas = surface!!.canvas

        canvas.clear(Color.TRANSPARENT)

        this.scene.render(canvas.asComposeCanvas(), currentNanoTime())

        // Cleanup
        surface.flushAndSubmit()
        surface.close()
        renderTarget.close()

        // Swap buffer on-screen
        this.cloak.swapBuffers()

        // Frame no longer has pending changes, thus is not dirty
        this.isDirty = false
    }

    private fun cleanup() {
        println("[Cloak->Engine]: Cleaning up resources...")
        this.scene.close()
    }
    
    fun close() {
        println("[Cloak->Engine]: Closing the window gracefully...")
        this.shouldClose = true
    }
    
    init {
        this.scene.setContent {
            CompositionLocalProvider(LocalCloakScope provides this.scope) {
                content(this.scope)
            }
        }

        val firstDrawTime = measureTime {
            this.draw()
        }

        println("[Cloak->Engine]: First draw time took: $firstDrawTime")
        this.cloak.showWindow() // Perhaps let the user define this?

        while (!this.cloak.shouldClose()) {
            if (shouldClose) break
            this.startFrame()
        }

        // Time to clean up thread
        this.cleanup()
    }

    internal data class EngineFrameStats(
        var totalFrames: Long = 0,
        var totalDrawFrames: Long = 0,

        var totalDrawDuration: Duration = Duration.ZERO,
        var totalInputDuration: Duration = Duration.ZERO,
        var totalPollDuration: Duration = Duration.ZERO,
        var totalFrameDuration: Duration = Duration.ZERO,

        var longestFrameDuration: Duration = Duration.ZERO,
    ) {
        fun incrementDrawFrame() = totalDrawFrames++

        fun addTotalSubframeTime(
            drawDuration: Duration,
            inputDuration: Duration,
            pollDuration: Duration
        ) {
            totalFrames++
            totalDrawDuration += drawDuration
            totalInputDuration += inputDuration
            totalPollDuration += pollDuration
        }

        fun addTotalFrameTime(frameDuration: Duration) {
            if (frameDuration > longestFrameDuration) longestFrameDuration = frameDuration

            totalFrameDuration += frameDuration
        }

        override fun toString(): String {
            val avgRender = totalDrawDuration.inWholeNanoseconds / totalFrames // Avg render time in milliseconds
            val avgInput = totalInputDuration.inWholeNanoseconds / totalFrames // Avg input polling time in milliseconds
            val avgPoll = totalPollDuration.inWholeNanoseconds  / totalFrames // Avg polling time in milliseconds
            val avgTotalTimePerFrame = totalFrameDuration.inWholeNanoseconds / totalFrames // Avg total time spent to render each frame in milliseconds
            val sb = StringBuilder()
            fun toMs(nanos: Long) = nanos / 1_000_000.0
            fun percent(part: Long) = (part.toDouble() / totalFrameDuration.inWholeNanoseconds) * 100


            sb.appendLine("=== FINAL SESSION METRICS ===")
            sb.appendLine("Total Frames Processed: $totalFrames")
            sb.appendLine("Render Frames Processed: $totalDrawFrames")
            sb.appendLine("Average Frame Time:     ${"%.3f".format(toMs(avgTotalTimePerFrame))}ms")
            sb.appendLine("Longest Frame Time:     ${"%.3f".format(longestFrameDuration.inWholeMilliseconds.toFloat())}ms")
            sb.appendLine("-----------------------------")
            sb.appendLine("Average Render:         ${"%.3f".format(toMs(avgRender))}ms (${"%.2f".format(percent(totalDrawDuration.inWholeNanoseconds))}%)")
            sb.appendLine("Average Input:          ${"%.3f".format(toMs(avgInput))}ms (${"%.2f".format(percent(totalInputDuration.inWholeNanoseconds))}%)")
            sb.appendLine("Average OS Poll:        ${"%.3f".format(toMs(avgPoll))}ms (${"%.2f".format(percent(totalPollDuration.inWholeNanoseconds))}%)")
            sb.appendLine("=============================")

            return sb.toString()
        }
    }
    
//    internal class CloakSceneContext : ComposeSceneContext {
//        private val platformContextImpl =
//            CloakPlatformContext(CloakScreenReader())
//
//        override val platformContext: PlatformContext get() = platformContextImpl
//    }
//    internal class CloakScreenReader : PlatformScreenReader {
//        override val isActive = false
//    }
//    @InternalComposeUiApi
//    internal class CloakTextInputService : PlatformTextInputService {
//
//        private var onEditCommand: ((List<EditCommand>) -> Unit)? = null
//        private var onImeAction: ((ImeAction) -> Unit)? = null
//
//        override fun startInput(
//            value: TextFieldValue,
//            imeOptions: ImeOptions,
//            onEditCommand: (List<EditCommand>) -> Unit,
//            onImeActionPerformed: (ImeAction) -> Unit
//        ) {
//            println("Start input!")
//            this.onEditCommand = onEditCommand
//            this.onImeAction = onImeActionPerformed
//        }
//
//        override fun stopInput() {
//            onEditCommand = null
//            onImeAction = null
//        }
//
//        override fun showSoftwareKeyboard() {}
//        override fun hideSoftwareKeyboard() {}
//
//        override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {}
//
//        /** Call this from your engine when text is produced */
//        fun commitText(text: String) {
//            onEditCommand?.invoke(
//                listOf(CommitTextCommand(text, 1))
//            )
//        }
//
//        fun deleteBackwards() {
//            onEditCommand?.invoke(
//                listOf(DeleteSurroundingTextCommand(1, 0))
//            )
//        }
//    }
//
//    class CloakWindowInfo : WindowInfo {
//
//        override var isWindowFocused: Boolean by mutableStateOf(true)
//
//        override var keyboardModifiers: PointerKeyboardModifiers by mutableStateOf(
//            PointerKeyboardModifiers()
//        )
//
//        override var containerSize: IntSize by mutableStateOf(IntSize.Zero)
//    }
//
//
//    internal class CloakPlatformContext(override val screenReader: PlatformScreenReader) : PlatformContext {
//
//        val textInputServiceImpl = CloakTextInputService()
//        private val inputModeManagerImpl = CloakInputModeManager()
//
//        override val windowInfo: WindowInfo = CloakWindowInfo()
//
//        override val inputModeManager: InputModeManager
//            get() = inputModeManagerImpl
//
//        override val textInputService: PlatformTextInputService
//            get() = textInputServiceImpl
//    }
//
//    internal class CloakInputModeManager(
//        initialMode: InputMode = InputMode.Keyboard
//    ) : InputModeManager {
//
//        private var _inputMode by mutableStateOf(initialMode)
//
//        override val inputMode: InputMode
//            get() = _inputMode
//
//        override fun requestInputMode(inputMode: InputMode): Boolean {
//            _inputMode = inputMode
//            return true
//        }
//    }
}