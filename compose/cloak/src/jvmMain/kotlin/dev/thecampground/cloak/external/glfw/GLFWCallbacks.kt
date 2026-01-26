package dev.thecampground.cloak.external.glfw

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentLinkedQueue

object GLFWCallbacks {
    val glfw = GLFW.getInstance()
    val queue: ConcurrentLinkedQueue<GLFWEvent> = ConcurrentLinkedQueue()

   val getProcAddressHandle = MethodHandles.lookup()
       .findStatic(
           GLFWCallbacks::class.java,
           "skikoGetProcAddress",
           MethodType.methodType(
               MemorySegment::class.java,
               MemorySegment::class.java,
               MemorySegment::class.java
           )
       )
    val getProcAddressStub = glfw.api.linker.upcallStub(
        getProcAddressHandle,
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
        ),

        glfw.arena
    )


    @JvmStatic
    fun skikoGetProcAddress(ctx: MemorySegment, name: MemorySegment): MemorySegment {
        return glfw.api.getProcAddress(name) as MemorySegment
    }

    private fun getMouseButton(window: MemorySegment): GLFWKeyAction {
        return when(glfw.api.getMouseButton(window, 0) as Int) {
            0 -> GLFWKeyAction.RELEASE
            1 -> GLFWKeyAction.PRESS

            else -> GLFWKeyAction.UNKNOWN
        }
    }

    val cursorPosCallback = GLFWCallback(
        desc = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE,
        ),
        handle = MethodHandles.lookup()
            .findStatic(GLFWCallbacks::class.java,
                "onCursorPositionCallback",
                MethodType.methodType(
                    Void.TYPE,

                    MemorySegment::class.java,
                    Double::class.java,
                    Double::class.java,
                )
            )
    )
    @JvmStatic
    fun onCursorPositionCallback(window: MemorySegment, x: Double, y: Double) {
        queue.offer(GLFWEvent.CursorPosition(x, y, getMouseButton(window)))
    }

    val cursorEnterCallback = GLFWCallback(
        desc = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_BOOLEAN,
        ),
        handle = MethodHandles.lookup()
            .findStatic(GLFWCallbacks::class.java,
                "onCursorEnterCallback",
                MethodType.methodType(
                    Void.TYPE,

                    MemorySegment::class.java,
                    Boolean::class.java,
                )
            )
    )
    @JvmStatic
    fun onCursorEnterCallback(window: MemorySegment, entered: Boolean) {
        val xPtr = glfw.arena.allocate(ValueLayout.JAVA_DOUBLE)
        val yPtr = glfw.arena.allocate(ValueLayout.JAVA_DOUBLE)

        glfw.api.getCursorPosition(window, xPtr, yPtr)

        val x = xPtr.get(ValueLayout.JAVA_DOUBLE, 0)
        val y = yPtr.get(ValueLayout.JAVA_DOUBLE, 0)
        val mouseButton = getMouseButton(window)

        val event = when (entered) {
            true -> GLFWEvent.CursorEnter(x, y, mouseButton)
            false -> GLFWEvent.CursorExit(x, y, mouseButton)
        }

        queue.offer(event)
    }

    val mouseButtonCallback = GLFWCallback(
        desc = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ),
        handle = MethodHandles.lookup()
            .findStatic(GLFWCallbacks::class.java,
                "onMouseButtonCallback",
                MethodType.methodType(
                    Void.TYPE,

                    MemorySegment::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                )
            )
    )
    @JvmStatic
    fun onMouseButtonCallback(window: MemorySegment, button: Int, action: Int, mods: Int) {
        val type = when (action) {
            0 -> GLFWKeyAction.RELEASE
            1 -> GLFWKeyAction.PRESS

            else -> GLFWKeyAction.UNKNOWN
        }
        queue.offer(GLFWEvent.MouseButton(button, type, mods))
    }

    val mouseScrollCallback = GLFWCallback(
        desc = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE,
        ),
        handle = MethodHandles.lookup()
            .findStatic(GLFWCallbacks::class.java,
                "onMouseScrollCallback",
                MethodType.methodType(
                    Void.TYPE,

                    MemorySegment::class.java,
                    Double::class.java,
                    Double::class.java,
                )
            )
    )
    @JvmStatic
    fun onMouseScrollCallback(window: MemorySegment, scrollX: Double, scrollY: Double) {
        queue.offer(GLFWEvent.MouseScroll(scrollX, scrollY))
    }

    val windowResizeCallback = GLFWCallback(
        desc = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ),
        handle = MethodHandles.lookup()
            .findStatic(GLFWCallbacks::class.java,
                "onWindowResize",
                MethodType.methodType(
                    Void.TYPE,

                    MemorySegment::class.java,
                    Int::class.java,
                    Int::class.java,
                )
            )
    )
    @JvmStatic
    fun onWindowResize(window: MemorySegment, width: Int, height: Int) {
        queue.offer(GLFWEvent.WindowResize(width, height))
    }

    val keyCallback = GLFWCallback(
        desc = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ),
        handle = MethodHandles.lookup()
            .findStatic(GLFWCallbacks::class.java,
                "onKeyCallback",
                MethodType.methodType(
                    Void.TYPE,

                    MemorySegment::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                )
            )
    )
    @JvmStatic
    fun onKeyCallback(window: MemorySegment, key: Int, scancode: Int, action: Int, mods: Int) {
        val type = when (action) {
            0 -> GLFWKeyAction.RELEASE
            1 -> GLFWKeyAction.PRESS

            else -> GLFWKeyAction.UNKNOWN
        }

        queue.offer(GLFWEvent.Key(key, scancode, type, mods))
    }

    val charCallback = GLFWCallback(
        desc = FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
        ),
        handle = MethodHandles.lookup()
            .findStatic(GLFWCallbacks::class.java,
                "onCharCallback",
                MethodType.methodType(
                    Void.TYPE,

                    MemorySegment::class.java,
                    Int::class.java,
                )
            )
    )
    @JvmStatic
    fun onCharCallback(window: MemorySegment, char: Int) {
        queue.offer(GLFWEvent.Char(char.toUInt()))
    }

    class GLFWCallback(val desc: FunctionDescriptor, val handle: MethodHandle)
}