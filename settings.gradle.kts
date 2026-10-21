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
}

rootProject.name = "onvexa_recorder"

// Fase 0: base del proyecto (datos, diseño y esqueletos de las apps).
include(":core:database")
include(":core:design")
include(":app")
include(":wear")

// Se activan conforme se implementan (Fase 1 MVP):
// include(":core:model")
// include(":core:common")
// include(":core:audio")
// include(":core:sync")
// include(":core:ai")
