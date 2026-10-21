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

// Fase 0: capa de datos y sistema de diseño.
include(":core:database")
include(":core:design")

// Se activan conforme se implementan (Fase 0 diseño, Fase 1 MVP):
// include(":core:model")
// include(":core:common")
// include(":core:audio")
// include(":core:sync")
// include(":core:ai")
// include(":app")
// include(":wear")
