pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.orbitalhq.com/release") {
            content {
                includeGroupByRegex("org\\.taxilang.*")
            }
        }
    }
}

rootProject.name = "taxi-kotlin-codegen"

include("generated-example")

include("benchmarks")
