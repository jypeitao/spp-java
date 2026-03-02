import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "com.microlumin.xlink.spp.client"
    compileSdk = libs.versions.compileSdk.get().toInt()

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

afterEvaluate {
    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["release"])
                artifactId = "spp-client"
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
                    username = project.extra["mavenUsername"] as String
                    password = project.extra["mavenPassword"] as String
                }
            }
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}