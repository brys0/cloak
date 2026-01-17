package dev.thecampground.cloak.mpv

private val cloakPath =
    "cloak"

class MPVCompat {
    @JvmField
    var renderContext: MPVRenderContext? = null

    companion object {
        init {
            // This must match the name of the shared_library in your meson.build
            System.loadLibrary(cloakPath)
        }
    }

    /**
     * Java side passes the desired OpenGL format (e.g., 0x881A for RGBA16F).
     * C++ creates the FBO/Texture and returns a populated MPVRenderContext object.
     */
    external fun createRenderContext(format: Int): Boolean

    external fun resizeMPVTexture(width: Int, height: Int, format: Int): Boolean
    external fun cleanup(): Boolean
}

data class MPVRenderContext(
    val framebuffer: Int,
    val textureID: Int,
    val textureFormat: Int,
)