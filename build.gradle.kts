plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kobweb.application) apply false
    alias(libs.plugins.kobwebx.markdown) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    group = "com.github.nanaki_93"
    version = "1.0-SNAPSHOT"


}