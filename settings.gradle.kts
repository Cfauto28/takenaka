pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

fun includeComposite(name: String, vararg modules: String) {
    modules.forEach { module ->
        include(":$name-$module")
        project(":$name-$module").projectDir = file("$name/$module")
    }
}

rootProject.name = "takenaka"

include("core")
includeComposite("generator", "common", "web", "web-cli", "accessor", "accessor-runtime", "accessor-plugin")
