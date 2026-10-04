import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// X.Y.Z from gradle.properties; versionCode = X·10000 + Y·100 + Z.
val appVersion = providers.gradleProperty("termoakVersion").get()
val appVersionCode = appVersion.substringBefore('-').split('.').map { it.toInt() }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// Signing for published versions: keystore.properties (or the
// TERMOAK_ANDROID_* environment variables). Without it, the release APK is unsigned.
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
    System.getenv("TERMOAK_ANDROID_KEYSTORE")?.let { setProperty("storeFile", it) }
    System.getenv("TERMOAK_ANDROID_KEYSTORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("TERMOAK_ANDROID_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("TERMOAK_ANDROID_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}

android {
    namespace = "com.termoak.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.termoak"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
        // The ABIs the engine is built for (scripts/release-local.sh).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        // Server suggested on the sign-in screen.
        buildConfigField(
            "String",
            "DEFAULT_SERVER",
            "\"${providers.gradleProperty("termoakServer").getOrElse("https://termoak.com")}\"",
        )
    }

    signingConfigs {
        if (signing.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword") ?: signing.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Lists the languages of the values-<lang> folders for the system and
        // for the language picker (AppLanguage). English is the default
        // (res/resources.properties).
        generateLocaleConfig = true
    }

    bundle {
        // The language can be changed in the app: every App Bundle install keeps all the translations.
        language { enableSplit = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The Rust .so files are already stripped of debug symbols.
        jniLibs.keepDebugSymbols += "**/libtermoak_ffi.so"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":termoak"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Per-app language on Android 12 and older (AppCompatDelegate.setApplicationLocales).
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
