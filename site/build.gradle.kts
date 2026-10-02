import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication
import kotlinx.html.meta
import kotlinx.html.link
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

// The site project's Gradle version is the single source for the JS backup metadata.
// Generate a source (not a checked-in literal) so exports identify the build that made them.
val siteVersionText = version.toString()
val siteVersionSource = layout.buildDirectory.dir("generated/sources/siteVersion/jsMain")
val generateSiteVersion by tasks.registering {
    inputs.property("siteVersion", siteVersionText)
    outputs.dir(siteVersionSource)
    doLast {
        val source = siteVersionSource.get().file("com/github/nanaki_93/storage/SiteBuildInfo.kt").asFile
        source.parentFile.mkdirs()
        val quoted = siteVersionText.replace("\\", "\\\\").replace("\"", "\\\"")
        source.writeText("package com.github.nanaki_93.storage\n\ninternal object SiteBuildInfo { const val VERSION = \"$quoted\" }\n")
    }
}

// Generate Silk's required numeric palette values from the accepted CSS token source.
val generateThemeTokens by tasks.registering {
    val tokens = rootProject.file(".mockups/design-system/tokens.css")
    val output = layout.buildDirectory.dir("generated/sources/theme/jsMain")
    inputs.file(tokens)
    outputs.dir(output)
    doLast {
        val css = tokens.readText()
        val light = css.substringBefore("[data-theme=\"dark\"] {")
        val dark = css.substringAfter("[data-theme=\"dark\"] {")
        fun color(block: String, name: String) = Regex("--color-$name: #([0-9a-fA-F]{6});").find(block)!!.groupValues[1]
        val file = output.get().file("com/github/nanaki_93/ThemeTokens.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("""package com.github.nanaki_93
internal object ThemeTokens {
    const val LIGHT_BG = 0x${color(light, "bg-primary")}
    const val LIGHT_TEXT = 0x${color(light, "text-primary")}
    const val DARK_BG = 0x${color(dark, "bg-primary")}
    const val DARK_TEXT = 0x${color(dark, "text-primary")}
}
""")
    }
}

kobweb {
    app {
        index {
            description.set("Local-first Japanese for everyday engineering conversations")
            head.add {
                link(rel = "stylesheet", href = "/hiragame/tokens.css")
                link(rel = "stylesheet", href = "/hiragame/learning.css")
                meta(name = "keywords", content = "hiragana, japanese, learning, game, education")
            }
        }

    }
}

kotlin {
    configAsKobwebApplication("hiragame")

    js {
        browser()
        nodejs { testTask { useMocha { timeout = "10s" } } }
    }
    sourceSets {
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        val jsMain by getting {
            kotlin.srcDir(generateSiteVersion)
            kotlin.srcDir(generateThemeTokens)
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.html.core)
                implementation(libs.kobweb.core)
                implementation(libs.kobweb.silk)
                implementation(libs.kobwebx.markdown)
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
        include("*.ico", "*.png", "*.svg", "*.css")
        into("hiragame")
    }
    from("src/jsMain/resources/public/content") {
        into("hiragame/content")
    }
}

tasks.named("build") {
    finalizedBy("reorganizeOutput")
}
// Copy the canonical selected tokens; never maintain a second authored palette.
tasks.named<ProcessResources>("jsProcessResources") {
    from(rootProject.file(".mockups/design-system/tokens.css")) { into("public") }
}
