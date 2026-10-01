import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication
import kotlinx.html.meta
import org.gradle.kotlin.dsl.kotlin

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

    js {
        browser()
        nodejs()
    }
    sourceSets {
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
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
                implementation("io.ktor:ktor-client-core:3.2.3")
                implementation("io.ktor:ktor-client-js:3.2.3")
                implementation("io.ktor:ktor-client-content-negotiation:3.2.3")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.3")
            }
        }
    }
}

// Keep generated distribution inputs outside the Sync destination, even on repeated runs.
// Sync prunes stale hosted content and obsolete configuration without touching the source tree.
tasks.register<Sync>("reorganizeOutput") {
    dependsOn("jsBrowserDistribution")

    val productionDir = layout.buildDirectory.dir("dist/js/productionExecutable")
    into(productionDir.map { it.dir("public") })

    from(productionDir) {
        include("**/*.js", "**/*.js.map")
        exclude("public/**")
        into("hiragame")
    }
    // Kobweb generates the shell and processes public assets outside the webpack output.
    val processedPublic = layout.buildDirectory.dir("processedResources/js/main/public")
    from(processedPublic) {
        include("index.html")
    }
    from(processedPublic) {
        include("*.ico", "*.png", "*.svg")
        into("hiragame")
    }
    from("src/jsMain/resources/public/content") {
        into("hiragame/content")
    }
}

tasks.named("build") {
    finalizedBy("reorganizeOutput")
}