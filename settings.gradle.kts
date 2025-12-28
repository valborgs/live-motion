import org.gradle.kotlin.dsl.flatDir
import org.gradle.kotlin.dsl.repositories

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        flatDir { dirs("libs") }
    }
}

rootProject.name = "LiveMotion"
include(":app")
include(":live2d:framework")
project(":live2d:framework").projectDir = file("live2d/framework")
