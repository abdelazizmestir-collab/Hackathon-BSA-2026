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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SwissWallet"

// Shared design system + cross-app contract
include(":core")
// App 1: Swissquote (concept) — trading app with the Wallet tab
include(":swissquote")
// App 2: SwissWallet — the ERC-4337 smart wallet (separate APK)
include(":swisswallet")
