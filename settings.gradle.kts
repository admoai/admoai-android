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
        // maven { url = uri("https://jitpack.io") }  // TODO: Add when implementing vast-client-java
    }
}

rootProject.name = "admoai-android-sdk"
include(":sample")
include(":sdk")
project(":sample").projectDir = file("sample")
