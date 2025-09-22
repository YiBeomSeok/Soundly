pluginManagement {
    includeBuild("build-logic")
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
    }
}

rootProject.name = "Soundly"

include(":app")

include(
    ":data:recording",
)

include(
    ":domain:recording",
)

include(
    ":feature:nav-graph",
)
include(
    ":feature:main",
    ":feature:onboarding",
    ":feature:recording",
    ":feature:recording-api",
)

include(
    ":core:designsystem",
)

include(":soundly-sdk:record")
include(":soundly-sdk:decoder-flac")
include(":soundly-sdk:player")
