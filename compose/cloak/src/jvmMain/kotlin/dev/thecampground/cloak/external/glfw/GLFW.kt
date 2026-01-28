package dev.thecampground.cloak.external.glfw

import dev.thecampground.cloak.external.opengl.OpenGLApi
import org.jetbrains.skia.impl.NativePointer
import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup

class GLFW private constructor(
    val api: GLFWApi,
    val arena: Arena,
) {
    var gl: OpenGLApi? = null

    val getProcAddress: NativePointer = api.getProcAddressPtr.address()

    fun createWindow(width: Int, height: Int, title: String, hints: List<GLFWWindow.Companion.Hint> = listOf(), arena: Arena = Arena.ofConfined()): GLFWWindow {
        return GLFWWindow(this.api, width, height, title, arena, hints)
    }

    fun setSwapInterval(interval: Int) {
        api.swapInterval(interval)
    }

    fun pollEvents() {
        api.pollEvents()
    }


    fun getProcAddress(name: String, arena: Arena): MemorySegment{
        val allocatedNameStr = arena.allocateFrom(name)
        val funcPtr = api.getProcAddress(allocatedNameStr) as MemorySegment

        if (funcPtr == MemorySegment.NULL) {
            error("Function '$name' has not been found")
        }

        return funcPtr
    }

    fun getCurrentContext(): Long {
        return (api.getCurrentContext() as MemorySegment).address()
    }

    fun loadGL() {
        this.gl = OpenGLApi(api.linker, api.symbols, api, arena)
    }

    companion object {
        var glfw: GLFW? = null

        internal fun getInstance(): GLFW {
            requireNotNull(glfw) { "GLFW is not initialized" }

            return glfw!!
        }
        fun load(path: String, platform: GLFWPlatform = GLFWPlatform.Any(), arena: Arena): GLFW {
            System.load(path)

            val linker = Linker.nativeLinker()
            val symbols = SymbolLookup.loaderLookup()

            val api = GLFWApi(linker, symbols)

            api.initHint(platform.key, platform.value)
            if ((api.init() as Int) == 0) {
                error("GLFW initialization failed")
            }

            glfw = GLFW(api, arena, )


            return glfw!!
        }


    }
}
