// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

allprojects {
    group = "com.microlumin.xlink"
    version = "1.0.1-SNAPSHOT" // 您可以根据需要修改版本号

    // 从 local.properties 加载凭据
    val localProperties = java.util.Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    extra["mavenUsername"] = localProperties.getProperty("maven.username") ?: ""
    extra["mavenPassword"] = localProperties.getProperty("maven.password") ?: ""
}