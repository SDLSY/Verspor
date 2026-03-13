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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "new start"
include(
    ":app-shell",
    ":core-common",
    ":core-model",
    ":core-data",
    ":core-ble",
    ":core-network",
    ":core-db",
    ":core-ml",
    ":feature-home",
    ":feature-device",
    ":feature-doctor",
    ":feature-relax",
    ":feature-trend",
    ":feature-profile"
)
 
