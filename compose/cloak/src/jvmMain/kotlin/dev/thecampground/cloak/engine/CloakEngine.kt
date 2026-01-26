package dev.thecampground.cloak.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.scene.PlatformLayersComposeScene
import androidx.compose.ui.unit.IntSize
import dev.thecampground.cloak.external.glfw.GLFW
import dev.thecampground.cloak.external.glfw.GLFWCallbacks
import dev.thecampground.cloak.external.glfw.GLFWWindow
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
class CloakEngine
    @OptIn(InternalComposeUiApi::class, DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    constructor(
        val glfw: GLFW,
        val window: GLFWWindow,
        val contextPointer: NativePointer = glfw.getCurrentContext(),
        procAddressPointer: NativePointer = GLFWCallbacks.getProcAddressStub.address(),
        dispatcher: CoroutineContext = Dispatchers.Default,
        private val content: @Composable (CloakScope) -> Unit,
    ) {
    val renderQueue = RenderQueue()
    // When true, the application will safely shut down   
    private var shouldClose = false 
    // Force at least one frame to render at startup
    private var isDirty = true
    private var size = window.size
    internal var stats = EngineFrameStats()
    var lastSize = IntSize.Zero

    private val scope = CloakScope(engine = this, renderQueue = this.renderQueue)
    internal val context = DirectContext.makeGLWithInterface(
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
        window  = this.window,
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
                this.glfw.pollEvents()
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
        return this.isDirty || this.scene.hasInvalidations() || this.renderQueue.size() > 0
    }
    /**
     * This should only handle drawing of the frame to the window.
     */
    @OptIn(InternalComposeUiApi::class)
    private fun draw() {
        this.window.makeContextCurrent()
        this.renderQueue.drain() // Drain render queue
        // Render at least once to create the texture


        // Get the actual texture ID from mpv
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

        try {
            this.scene.render(canvas.asComposeCanvas(), currentNanoTime())
        } catch (t: Throwable) {
            t.printStackTrace()
            println("Something went wrong while rendering scene: ${t}")
        }
        // Cleanup
        surface.flushAndSubmit()
        surface.close()
        renderTarget.close()

        this.window.swapBuffers()

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
        GlobalCloakScope.init { this.scope }
        RenderQueueDispatcher.init { this to this.scope }
        this.scene.setContent {
            CompositionLocalProvider(LocalCloakScope provides this.scope) {
                content(this.scope)
            }
        }

        val firstDrawTime = measureTime {
            this.draw()
        }

        println("[Cloak->Engine]: First draw time took: $firstDrawTime")

        this.glfw.setSwapInterval(1)
        this.window.isVisible = true

        while (!this.window.shouldClose) {
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

    inner class RenderQueue {

        private val tasks = ArrayDeque<RenderSideEffect>()
        fun size() = tasks.size

        fun post(action: RenderSideEffect): RenderSideEffect {
            synchronized(tasks) {
                tasks.add(action)
            }

            return action
        }

        fun remove(action: RenderSideEffect) {
            synchronized(tasks) {
                tasks.remove(action)
            }
        }

        internal fun drain() {
                val snapshot: List<RenderSideEffect>

                synchronized(tasks) {
                    snapshot = tasks.toList()
                }

                for (it in snapshot) {
                    val dequeue = !it(this@CloakEngine, this@CloakEngine.context)
                    if (dequeue) remove(it)
                }
            }
    }
}