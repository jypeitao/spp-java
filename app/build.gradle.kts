import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.microlumin.xlink.spp.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.microlumin.xlink.spp.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 获取 git 提交号和分支名
        val gitCommitId = try {
            val stdout = ByteArrayOutputStream()
            rootProject.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                standardOutput = stdout
            }
            stdout.toString().trim()
        } catch (e: Exception) {
            "unknown"
        }

        val gitBranch = try {
            val stdout = ByteArrayOutputStream()
            rootProject.exec {
                commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
                standardOutput = stdout
            }
            stdout.toString().trim()
        } catch (e: Exception) {
            "unknown"
        }

        buildConfigField("String", "GIT_VERSION", "\"$gitCommitId\"")
        buildConfigField("String", "GIT_BRANCH", "\"$gitBranch\"")
    }

    buildFeatures {
        buildConfig = true
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

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(project(":common"))
    implementation(project(":spp-server"))
    implementation(project(":spp-client"))
    implementation(project(":br"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}