import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
//    androidTarget {
//        compilerOptions {
//            jvmTarget.set(JvmTarget.JVM_11)
//        }
//    }


    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.androidx.lifecycle.viewmodelCompose)
                implementation(libs.androidx.lifecycle.runtimeCompose)
                implementation(project(":cloak"))
                implementation("dev.chrisbanes.haze:haze:1.7.1")
                implementation("dev.chrisbanes.haze:haze-materials:1.7.1")
                implementation("dev.zt64.mpvkt:mpvkt")
//                implementation(project(":library"))
//                implementation(project(":annotation"))

                // Voyager Nav

//                implementation(libs.voyager.navigator)
//                implementation(libs.voyager.screenModel)
//                implementation(libs.voyager.transitions)
//                implementation(libs.highlights)


            }
        }
    }
}

dependencies {
//    add("kspCommonMainMetadata", project(":processor"))


//    add("kspJvm", project(":processor"))
}


compose.desktop {
    application {
        mainClass = "dev.thecampground.cloak.example.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.thecampground.cloak.example.MainKt"
            packageVersion = "1.0.0"
        }
    }
}

//dependencies {
//
////    add("kspJvm", project(":processor"))
//}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
//    if (name != "kspCommonMainKotlinMetadata") {
//        dependsOn("kspCommonMainKotlinMetadata")
//    }
//}