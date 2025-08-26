plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

group = "com.github.nanaki_93"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)

    jvm()
    js(IR) {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
    }
}