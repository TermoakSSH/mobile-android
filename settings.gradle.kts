// Termoak Android app (Jetpack Compose on top of the Rust engine).
// The engine comes from the bindings module (UniFFI) of TermoakSSH/core, a
// git submodule in core/ (git submodule update --init).
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

rootProject.name = "Termoak"
include(":app")
include(":termoak")
project(":termoak").projectDir = file("core/bindings/kotlin")
