pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// The following block registers dependencies to enable Kobweb snapshot support. It is safe to delete or comment out
// this block if you never plan to use them.
gradle.settingsEvaluated {
    fun RepositoryHandler.kobwebSnapshots() {
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            mavenContent {
                includeGroupByRegex("com\\.varabyte\\.kobweb.*")
                snapshotsOnly()
            }
        }
    }

    pluginManagement.repositories { kobwebSnapshots() }
    dependencyResolutionManagement.repositories { kobwebSnapshots() }
}

rootProject.name = "hiragame"

include(":site")
include(":shared")
// Legacy backend source retained for possible data export; excluded from the application build.
