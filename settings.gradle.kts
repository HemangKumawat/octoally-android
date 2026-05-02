pluginManagement {
    repositories {
        google()
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
    // Gradle 8.4+ auto-creates the `libs` catalog from gradle/libs.versions.toml.
    // Declaring it explicitly causes "from() called more than once" — do not add back.
}

rootProject.name = "OctoAlly"

include(":app")
include(":core:network")
include(":core:data")
include(":core:ui")
include(":feature:projects")
include(":feature:session")
include(":feature:settings")
include(":feature:git")
include(":feature:files")
