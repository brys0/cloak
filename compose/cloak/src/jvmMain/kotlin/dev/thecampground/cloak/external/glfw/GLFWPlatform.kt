package dev.thecampground.cloak.external.glfw

sealed class GLFWPlatform(
    val key: Int = 0x00050003,
    val value: Int
) {
    class Any : GLFWPlatform(value = 0x00060000)
    class Win32 : GLFWPlatform(value = 0x00060001)
    class Cocoa : GLFWPlatform(value = 0x00060002)
    class Wayland : GLFWPlatform(value = 0x00060003)
    class X11 : GLFWPlatform(value = 0x00060004)
}