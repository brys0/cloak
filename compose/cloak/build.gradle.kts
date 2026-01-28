plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation("dev.chrisbanes.haze:haze:1.7.1")
            implementation("dev.chrisbanes.haze:haze-materials:1.7.1")
            implementation("dev.zt64.mpvkt:mpvkt")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // https://mvnrepository.com/artifact/net.java.dev.jna/jna
            implementation("net.java.dev.jna:jna:5.18.1")
        }
    }
}

val gladDep = "libglad.so"
val glfw3Dep = "libglfw3.so"

val nativeBuildDir: Directory = layout.projectDirectory.dir("native/buildDir")

val glfw3DepSrc: RegularFile = nativeBuildDir.file("subprojects/glfw-3.4/$glfw3Dep")
val gladDepSrc: RegularFile = nativeBuildDir.file("subprojects/glad/$gladDep")

val buildNativeLibs by tasks.registering(Exec::class) {
    workingDir = layout.projectDirectory.dir("native").asFile

    commandLine(
        "meson", "compile",
        "-C", nativeBuildDir.asFile.absolutePath
    )

    outputs.files(glfw3DepSrc, gladDepSrc)
}

val copyNativeLibs by tasks.registering(Copy::class) {
    dependsOn(buildNativeLibs)

    from(glfw3DepSrc)
    from(gladDepSrc)

    into(layout.projectDirectory.dir("src/jvmMain/resources"))

    outputs.files(
        layout.projectDirectory.file("src/jvmMain/resources/$glfw3Dep"),
        layout.projectDirectory.file("src/jvmMain/resources/$gladDep")
    )
}

tasks.named("build") {
    dependsOn(copyNativeLibs)
}