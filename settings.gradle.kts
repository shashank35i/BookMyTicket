pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral() // For other dependencies
        gradlePluginPortal() // For Gradle plugins
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()  // Google's repository
        mavenCentral()  // Maven Central repository
    }
}

rootProject.name = "bookmyticket"
include(":app")  // Include the app module for this project
