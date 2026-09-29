import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Google Cloud config lives in local.properties (not committed). See docs/google-cloud-setup.md.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(name: String) = "\"${localProps.getProperty(name, "")}\""

android {
    namespace = "net.osipiuk.bucklog"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "net.osipiuk.bucklog"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 4
        versionName = "0.4.0"
        buildConfigField("String", "PICKER_API_KEY", localProp("bucklog.pickerApiKey"))
        buildConfigField("String", "CLOUD_PROJECT_NUMBER", localProp("bucklog.cloudProjectNumber"))
    }

    // Release key lives outside the repo (see README "Release"); without it, release builds are unsigned.
    val releaseStore = localProps.getProperty("bucklog.release.storeFile")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = localProps.getProperty("bucklog.release.storePassword")
                keyAlias = localProps.getProperty("bucklog.release.keyAlias")
                keyPassword = localProps.getProperty("bucklog.release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work)
    implementation(libs.sqldelight.android.driver)
}
