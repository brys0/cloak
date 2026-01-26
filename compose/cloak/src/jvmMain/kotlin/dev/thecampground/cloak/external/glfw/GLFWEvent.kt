package dev.thecampground.cloak.external.glfw

sealed class GLFWEvent {
    open class CursorPosition(val x: Double, val y: Double, val action: GLFWKeyAction) : GLFWEvent()
    class CursorEnter(x: Double, y: Double, action: GLFWKeyAction) : CursorPosition(x, y, action)
    class CursorExit(x: Double, y: Double, action: GLFWKeyAction) : CursorPosition(x, y, action)

    class MouseButton(val button: Int, val action: GLFWKeyAction, val mods: Int) : GLFWEvent()
    class MouseScroll(val deltaX: Double, val deltaY: Double) : GLFWEvent()
    class WindowResize(val width: Int, val height: Int) : GLFWEvent()
    class Key(val key: Int, val scancode: Int, val action: GLFWKeyAction, val mods: Int) : GLFWEvent()
    class Char(val char: UInt) : GLFWEvent()
}

enum class GLFWKeyAction {
    PRESS,
    RELEASE,
    UNKNOWN
}