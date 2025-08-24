import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication
import kotlinx.html.meta

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kobweb.application)
    alias(libs.plugins.kobwebx.markdown)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlinx.serialization)
}

group = "com.github.nanaki_93"
version = "1.0-SNAPSHOT"

kobweb {
    app {
        index {
            description.set("Learn Hiragana with gamification!")
            head.add {
                meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
                meta(name = "description", content = "Interactive Hiragana learning game with gamification elements")
                meta(name = "keywords", content = "hiragana, japanese, learning, game, education")
            }
        }

    }

}

kotlin {
    // This example is frontend only. However, for a fullstack app, you can uncomment the includeServer parameter
    // and the `jvmMain` source set below.
    configAsKobwebApplication("hiragame" , includeServer = true)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
        }

        jsMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.html.core)
            implementation(libs.kobweb.core)
            implementation(libs.kobweb.silk)
            implementation(libs.kobwebx.markdown)
            implementation(libs.silk.icons.fa)
        }


        jvmMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            compileOnly(libs.kobweb.api) // Provided by Kobweb backend at runtime
        }
    }
}



