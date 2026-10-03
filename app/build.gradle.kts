import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Signaturdaten für Release-Builds; die Datei liegt nur lokal und wird nicht eingecheckt.
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
        versionCode = 1
        versionName = "1.0"
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
            // Zielgeräte sind ausschließlich arm64; andere ABIs landen nicht in der Release-APK.
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
        // Release-APK ist bewusst nur arm64 (siehe buildTypes); ChromeOS/x86_64 ist kein Ziel.
        disable += "ChromeOsAbiSupport"
    }

    // Der Kalender läuft immer auf Deutsch (DATE_LOCALE); ohne Sprach-Split bleiben die deutschen
    // Material-Texte auch auf anderssprachigen Geräten im Bundle.
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
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Ohne Signatur entstünde still eine nicht installierbare APK; daher lieber klar abbrechen.
val checkReleaseSigning by tasks.registering {
    val hasSigning = releaseSigningFile.exists()
    doLast {
        check(hasSigning) {
            "keystore.properties fehlt im Projektordner (siehe keystore.properties.example)."
        }
    }
}
tasks.named { it == "assembleRelease" || it == "bundleRelease" }.configureEach {
    dependsOn(checkReleaseSigning)
}
