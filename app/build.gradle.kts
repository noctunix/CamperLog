import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Single SemVer version declaration (MAJOR.MINOR.PATCH); release tag: v<version>.
// The derived versionCode (1.2.3 -> 10203) increases with each version for Android updates.
val appVersion = "1.0.3"
val appVersionCode = appVersion.split(".").map(String::toInt).also {
    require(it.size == 3) { "appVersion must be MAJOR.MINOR.PATCH: $appVersion" }
}.let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "MINOR and PATCH must be below 100: $appVersion" }
    major * 10_000 + minor * 100 + patch
}

// Release signing configuration is stored locally and never committed.
val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use(::load)
}

android {
    namespace = "app.restvolt.camperlog"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.restvolt.camperlog"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseSigningFile.exists()) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Release builds target arm64 devices only.
            ndk { abiFilters += "arm64-v8a" }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // Release APKs target arm64 only; ChromeOS/x86_64 is not supported.
        disable += "ChromeOsAbiSupport"
    }

    // Keep both English and German Material calendar resources in the app bundle.
    bundle {
        language {
            enableSplit = false
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Fail clearly instead of silently creating an unsigned release APK.
val checkReleaseSigning by tasks.registering {
    val hasSigning = releaseSigningFile.exists()
    doLast {
        check(hasSigning) {
            "keystore.properties is missing (see keystore.properties.example)."
        }
    }
}
tasks.named { it == "assembleRelease" || it == "bundleRelease" }.configureEach {
    dependsOn(checkReleaseSigning)
}
