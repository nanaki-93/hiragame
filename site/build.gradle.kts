import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication
import kotlinx.html.meta

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kobweb.application)
    alias(libs.plugins.kobwebx.markdown)
}

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
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
    configAsKobwebApplication("hiragame")

    sourceSets {


        val jsMain by getting {
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.html.core)
                implementation(libs.kobweb.core)
                implementation(libs.kobweb.silk)
                implementation(libs.kobwebx.markdown)
                implementation(libs.silk.icons.fa)

                // Shared models/logic
                implementation(project(":shared"))

                // Ktor client for JS
                implementation("io.ktor:ktor-client-core:2.3.11")
                implementation("io.ktor:ktor-client-js:2.3.11")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")
            }
        }
    }
}