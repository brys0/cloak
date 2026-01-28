package dev.thecampground.cloak.external

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.pathString

object NativeExtractor {
    val GLFW3 = "libglfw3.so"
    val GLAD = "libglad.so"

    private val extractedDir: Path by lazy {
        Files.createTempDirectory("cloak-native-").also {
            it.toFile().deleteOnExit()
        }
    }

    fun extract(libname: String): Path {
        val target = extractedDir.resolve(libname)

        if (Files.exists(target)) return target

        val resourcePath = "/$libname"
        val stream = NativeExtractor::class.java.getResourceAsStream(resourcePath) ?: error("Could not find native resource to extract at: $resourcePath")

        stream.use {
            Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING)
        }

        target.toFile().deleteOnExit()
        return target
    }
}