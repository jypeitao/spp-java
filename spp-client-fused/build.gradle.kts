plugins {
    alias(libs.plugins.android.fusedlibrary)
    id("maven-publish")
}

androidFusedLibrary {
    namespace = "com.microlumin.xlink.spp.client.fused"
    minSdk {
        version = release(libs.versions.minSdk.get().toInt())
    }

    // If aarMetadata is not explicitly specified,
    // aar metadata will be generated based on dependencies.
    aarMetadata {
        minCompileSdk = 31
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["fusedLibraryComponent"])
            groupId = "my-company"
            artifactId = "spp-client-fused"
        }
    }
    repositories {
        maven {
            val repositoryUrl = if (version.toString().endsWith("SNAPSHOT")) {
                "http://192.168.1.186:8081/repository/mlaixr-snapshot/"
            } else {
                "http://192.168.1.186:8081/repository/mlaixr-release/"
            }
            url = uri(repositoryUrl)
            isAllowInsecureProtocol = true
            credentials {
                username = project.findProperty("mavenUsername") as? String ?: ""
                password = project.findProperty("mavenPassword") as? String ?: ""
            }
        }
    }
}

dependencies {
    include(project(":common"))
    include(project(":spp-client"))
}