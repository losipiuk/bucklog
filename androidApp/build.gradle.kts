import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Google Cloud and signing config: local.properties (not committed), or environment variables on CI
// (bucklog.release.storeFile → BUCKLOG_RELEASE_STORE_FILE). See docs/google-cloud-setup.md and README.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun setting(name: String): String? =
    localProps.getProperty(name) ?: System.getenv(name.replace(Regex("([a-z])([A-Z])"), "$1_$2").replace('.', '_').uppercase())
fun localProp(name: String) = "\"${setting(name).orEmpty()}\""

android {
    namespace = "net.osipiuk.bucklog"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "net.osipiuk.bucklog"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 8
        versionName = "0.6.1"
        buildConfigField("String", "PICKER_API_KEY", localProp("bucklog.pickerApiKey"))
        buildConfigField("String", "CLOUD_PROJECT_NUMBER", localProp("bucklog.cloudProjectNumber"))
    }

    // Release key lives outside the repo (see README "Release"); without it, release builds are unsigned.
    val releaseStore = setting("bucklog.release.storeFile")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = setting("bucklog.release.storePassword")
                keyAlias = setting("bucklog.release.keyAlias")
                keyPassword = setting("bucklog.release.keyPassword")
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
