import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// X.Y.Z from gradle.properties; versionCode = X·10000 + Y·100 + Z.
val appVersion = providers.gradleProperty("termoakVersion").get()
val appVersionCode = appVersion.substringBefore('-').split('.').map { it.toInt() }
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// The ABIs of the published APKs (one per ABI plus a universal one with both;
// scripts/release-local.sh builds the engine for them). Debug builds add x86_64.
val releaseAbis = listOf("arm64-v8a", "armeabi-v7a")

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
        // Official server: the fallback of the engine's officialServerUrl() (release-local.sh
        // passes TERMOAK_OFFICIAL_SERVER to both).
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

    // One APK per ABI plus a universal one (scripts/release-local.sh): each
    // device downloads only its engine (libtermoak_ffi.so is most of the APK).
    // x86_64 (emulator) only goes into the debug universal APK: the ABIs that
    // are packaged are chosen in androidComponents below.
    splits {
        abi {
            isEnable = true
            reset()
            include(*releaseAbis.toTypedArray())
            isUniversalApk = true
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

    testOptions {
        // Robolectric (the screenshot tests of app/src/test/.../ui) needs the app's resources.
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        // The Rust .so files are already stripped of debug symbols.
        jniLibs.keepDebugSymbols += "**/libtermoak_ffi.so"
    }
}

androidComponents {
    onVariants { variant ->
        // Only the ABIs the engine is built for: release, arm64-v8a and
        // armeabi-v7a; debug, also x86_64 for the emulator. JNA ships more
        // (x86, armeabi, mips...), without the engine they would break the app.
        val abis = if (variant.buildType == "release") releaseAbis else releaseAbis + "x86_64"
        variant.packaging.jniLibs.excludes.addAll(
            listOf("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64", "mips", "mips64")
                .filter { it !in abis }.map { "**/$it/**" },
        )
        // versionCode·10 + ABI (universal 0, armeabi-v7a 1, arm64-v8a 2). Every
        // APK of a version is above every APK of the previous one, and above the
        // single APK of earlier releases (plain versionCode): any of them
        // updates any earlier install, whichever APK it came from.
        val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2)
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            output.versionCode.set(appVersionCode * 10 + (abiCodes[abi] ?: 0))
        }
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
    // Window size classes: split view of terminals on tablets and unfolded foldables.
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    // Per-app language on Android 12 and older (AppCompatDelegate.setApplicationLocales).
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // JVM unit tests (app/src/test) of the code without Android: keys, paths...
    testImplementation("junit:junit:4.13.2")
    // Screenshot tests of the layout on the JVM (Robolectric with its native graphics): no device needed.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
