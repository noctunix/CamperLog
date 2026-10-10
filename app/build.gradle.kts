import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// SemVer version (MAJOR.MINOR.PATCH); release tag: v<appVersion>.
// appVersionCode must be MAJOR * 10000 + MINOR * 100 + PATCH (1.2.3 -> 10203). It is a literal
// rather than computed because F-Droid's update check reads both values from this file.
val appVersion = "1.23.0"
val appVersionCode = 12300
appVersion.split(".").map(String::toInt).also {
    require(it.size == 3) { "appVersion must be MAJOR.MINOR.PATCH: $appVersion" }
}.let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "MINOR and PATCH must be below 100: $appVersion" }
    val expected = major * 10_000 + minor * 100 + patch
    require(appVersionCode == expected) { "appVersionCode must be $expected for $appVersion" }
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
            // The embedded commit hash depends on the checkout and breaks reproducible builds.
            vcsInfo.include = false
        }
    }

    // F-Droid rejects this blob: it is encrypted with a Google key and unreadable for anyone else.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets.getByName("main") {
        assets.directories.add(layout.buildDirectory.dir("generated/licenseAsset").get().asFile.path)
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

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// The About screen shows the Apache-2.0 license text offline, read from this generated asset
// rather than a checked-in copy - copying it at build time from the repository root LICENSE is
// what makes it impossible for the two to drift apart.
val copyLicenseAsset = tasks.register<Copy>("copyLicenseAsset") {
    from(rootProject.file("LICENSE"))
    into(layout.buildDirectory.dir("generated/licenseAsset"))
}

tasks.named("preBuild") {
    dependsOn(copyLicenseAsset)
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
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Fail clearly instead of silently creating an unsigned release APK. Builders that sign
// separately (F-Droid) opt out with -PunsignedRelease.
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    val hasSigning = releaseSigningFile.exists()
    val unsignedAllowed = providers.gradleProperty("unsignedRelease").isPresent
    doLast {
        check(hasSigning || unsignedAllowed) {
            "keystore.properties is missing (see keystore.properties.example)."
        }
    }
}
tasks.named { it == "assembleRelease" || it == "bundleRelease" }.configureEach {
    dependsOn(checkReleaseSigning)
}
