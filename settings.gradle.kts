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
        maven {
            url = uri("http://192.168.1.186:8081/repository/mlaixr-snapshot/")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://192.168.1.186:8081/repository/mlaixr-release/")
            isAllowInsecureProtocol = true
        }
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
        maven {
            url = uri("http://192.168.1.186:8081/repository/mlaixr-snapshot/")
            isAllowInsecureProtocol = true
        }
        maven {
            url = uri("http://192.168.1.186:8081/repository/mlaixr-release/")
            isAllowInsecureProtocol = true
        }
    }
}

rootProject.name = "spp"
include(":app")
include(":common")
include(":spp-server")
include(":spp-client")