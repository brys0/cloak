package dev.thecampground.cloak.external.glfw

import androidx.compose.ui.unit.IntSize
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

class GLFWWindow(
    private val api: GLFWApi,
    val width: Int,
    val height: Int,
    val title: String,
    val arena: Arena,
    val hints: List<Hint> = listOf(),
) {
    val callbacks = GLFWCallbacks
    val handle: MemorySegment
    var isVisible: Boolean = hints.find { it is Hint.Visible }?.value == 1
        set(value) {
            field = value
            when (value) {
                true -> this.show()
                false -> this.hide()
            }
            field = value
        }
    val size: IntSize
        get() {
            val widthPtr = arena.allocate(ValueLayout.JAVA_INT)
            val heightPtr = arena.allocate(ValueLayout.JAVA_INT)

            api.getFramebufferSize(handle, widthPtr, heightPtr)

            val width = widthPtr.get(ValueLayout.JAVA_INT, 0)
            val height = heightPtr.get(ValueLayout.JAVA_INT, 0)

            return IntSize(width, height)
        }

    val shouldClose: Boolean
        get() = api.windowShouldClose(handle) as Boolean

    fun makeContextCurrent() {
        api.makeContextCurrent(handle)
    }

    fun swapBuffers() {
        api.swapBuffers(handle)
    }

    fun show() {
        api.showWindow(handle)
    }

    fun hide() {
        api.hideWindow(handle)
    }

    fun close() {
        api.setWindowShouldClose(handle, true)
    }

    private fun setCallbacks() {
        val cursorPosUpcall = api.linker.upcallStub(
            GLFWCallbacks.cursorPosCallback.handle,
            GLFWCallbacks.cursorPosCallback.desc,
            arena
        )
        val cursorEnterUpcall = api.linker.upcallStub(
            GLFWCallbacks.cursorEnterCallback.handle,
            GLFWCallbacks.cursorEnterCallback.desc,
            arena
        )
        val mouseButtonUpcall = api.linker.upcallStub(
            GLFWCallbacks.mouseButtonCallback.handle,
            GLFWCallbacks.mouseButtonCallback.desc,
            arena
        )
        val mouseScrollUpcall = api.linker.upcallStub(
            GLFWCallbacks.mouseScrollCallback.handle,
            GLFWCallbacks.mouseScrollCallback.desc,
            arena
        )
        val windowResizeUpcall = api.linker.upcallStub(
            GLFWCallbacks.windowResizeCallback.handle,
            GLFWCallbacks.windowResizeCallback.desc,
            arena
        )
        val keyUpcall = api.linker.upcallStub(
            GLFWCallbacks.keyCallback.handle,
            GLFWCallbacks.keyCallback.desc,
            arena
        )
        val charUpcall = api.linker.upcallStub(
            GLFWCallbacks.charCallback.handle,
            GLFWCallbacks.charCallback.desc,
            arena
        )

        api.setCursorPositionCallback(handle, cursorPosUpcall)
        api.setCursorEnterCallback(handle, cursorEnterUpcall)
        api.setMouseButtonCallback(handle, mouseButtonUpcall)
        api.setScrollCallback(handle, mouseScrollUpcall)
        api.setWindowSizeCallback(handle, windowResizeUpcall)
        api.setKeyCallback(handle, keyUpcall)
        api.setCharCallback(handle, charUpcall)
    }
    init {
        val titleStringAllocation = arena.allocateFrom(title)

        for (hint in hints) {
            api.windowHint(hint.key, hint.value)
        }

        handle = api.createWindow(
            width,
            height,
            titleStringAllocation,
            MemorySegment.NULL,
            MemorySegment.NULL
        ) as MemorySegment

        require(handle != MemorySegment.NULL) {
            "GLFW window couldn't be created."
        }

        this.setCallbacks()
    }

    companion object {
        sealed class Hint(val key: Int, val value: Int) {
            class Resizable(resizable: Boolean) : Hint(
                key = 0x00020003,
                value = if (resizable) 1 else 0
            )
            class Visible(visible: Boolean) : Hint(
                key = 0x00020004,
                value = if (visible) 1 else 0
            )
            class Decorated(decorated: Boolean) : Hint(
                key = 0x00020005,
                value = if (decorated) 1 else 0
            )
            class Focused(focused: Boolean) : Hint(
                key = 0x00020001,
                value = if (focused) 1 else 0
            )
            class AutoIconify(iconify: Boolean) : Hint(
                key = 0x00020006,
                value = if (iconify) 1 else 0
            )
            class Floating(floating: Boolean) : Hint(
                key = 0x00020007,
                value = if (floating) 1 else 0
            )
            class Maximized(maximized: Boolean) : Hint(
                key = 0x00020008,
                value = if (maximized) 1 else 0
            )
            class TransparentFramebuffer(transparentFramebuffer: Boolean) : Hint(
                key = 0x0002000A,
                value = if (transparentFramebuffer) 1 else 0
            )
        }
    }
}




