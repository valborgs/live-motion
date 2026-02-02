include(":feature:home")
include(":domain")
include(":core:tracking")
include(":core:face")
include(":core:live2d")
include(":core:storage")
include(":data")
include(":core:common")
include(":core:navigation")
include(":core:ui")
include(":feature:settings")
include(":feature:studio")

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
