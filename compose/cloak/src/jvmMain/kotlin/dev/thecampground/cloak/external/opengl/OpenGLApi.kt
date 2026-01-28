package dev.thecampground.cloak.external.opengl

import dev.thecampground.cloak.external.ApiLayer
import dev.thecampground.cloak.external.glfw.GLFWApi
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

@JvmInline
value class FBO(val value: Int)

@JvmInline
value class Texture(val value: Int)

object OpenGlEnum {
    const val GL_TEXTURE_2D = 0x0DE1
    const val GL_FRAMEBUFFER = 0x8D40
    @Suppress("unused")
    const val GL_RGBA = 0x1908
    const val GL_UNSIGNED_BYTE = 0x1401

    const val GL_TEXTURE_MAG_FILTER = 0x2800
    const val GL_TEXTURE_MIN_FILTER = 0x2801

    const val GL_LINEAR = 0x2601

    const val GL_COLOR_ATTACHMENT0 = 0x8CE0
}

class OpenGLApi(linker: Linker, symbols: SymbolLookup, val glfwApi: GLFWApi, val arena: Arena) : ApiLayer(linker, symbols) {
    private val glGenFramebuffersMH = load(
        "glGenFramebuffers",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
    fun glGenFramebuffers(amount: Int): FBO {
        val fboPtr = arena.allocate(ValueLayout.JAVA_INT)

        glGenFramebuffersMH(amount, fboPtr)

        return FBO(fboPtr.get(ValueLayout.JAVA_INT, 0))
    }

    private val glBindFramebufferMH = load(
        "glBindFramebuffer",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
    )
    fun glBindFramebuffer(fbo: FBO)  {
        glBindFramebufferMH(OpenGlEnum.GL_FRAMEBUFFER, fbo.value)
    }

    private val glGenTexturesMH = load(
        "glGenTextures",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
    fun glGenTextures(amount: Int): Texture {
        val texIdPtr = arena.allocate(ValueLayout.JAVA_INT)

        glGenTexturesMH(amount, texIdPtr)

        return Texture(texIdPtr.get(ValueLayout.JAVA_INT, 0))
    }

    private val glBindTextureMH = load(
        "glBindTexture",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
    )
    fun glBindTexture(tex: Texture) {
        glBindTextureMH(OpenGlEnum.GL_TEXTURE_2D, tex.value)
    }

    private val glTexImage2DMH = load(
        "glTexImage2D",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
    fun glTexImage2D(level: Int, internalFormat: Int, width: Int, height: Int, border: Int, format: Int) {
        glTexImage2DMH(OpenGlEnum.GL_TEXTURE_2D, level, internalFormat, width, height, border, format, OpenGlEnum.GL_UNSIGNED_BYTE, MemorySegment.NULL)
    }

    private val glTexParameteriMH = load(
        "glTexParameteri",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
    )
    fun glTexParameteri(filter: Int, type: Int) {
        glTexParameteriMH(OpenGlEnum.GL_TEXTURE_2D, filter, type)
    }

    private val glFramebufferTexture2DMH = load(
        "glFramebufferTexture2D",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        )
    )
    fun glFramebufferTexture2D(colorBuffer: Int, tex: Texture, level: Int) {
        glFramebufferTexture2DMH(OpenGlEnum.GL_FRAMEBUFFER, colorBuffer, OpenGlEnum.GL_TEXTURE_2D, tex.value, level)
    }

    private val glFlushMH = load(
        "glFlush",
        FunctionDescriptor.ofVoid()
    )
    fun glFlush() {
        glFlushMH()
    }

    private val glDeleteFramebuffersMH = load(
        "glDeleteFramebuffers",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
    fun glDeleteFramebuffers(amount: Int, fbo: FBO) {
        val fboPtr = arena.allocate(ValueLayout.JAVA_INT)
        fboPtr.set(ValueLayout.JAVA_INT, 0, fbo.value)

        glDeleteFramebuffersMH(amount, fboPtr)
    }

    private val glDeleteTexturesMH = load(
        "glDeleteTextures",
        FunctionDescriptor.ofVoid(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
        )
    )
    fun glDeleteTextures(amount: Int, tex: Texture) {
        val texIdPtr = arena.allocate(ValueLayout.JAVA_INT)
        texIdPtr.set(ValueLayout.JAVA_INT, 0, tex.value)

        glDeleteTexturesMH(amount, texIdPtr)
    }

    private fun load(
        name: String,
        descriptor: FunctionDescriptor
    ): MethodHandle {
        val cName = arena.allocateFrom(name)
        val addr = glfwApi.getProcAddress(cName) as MemorySegment

        if (addr == MemorySegment.NULL) {
            throw UnsatisfiedLinkError("OpenGL function $name not found")
        }

        return linker.downcallHandle(addr, descriptor)
    }
}