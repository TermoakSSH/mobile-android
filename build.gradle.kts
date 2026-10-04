// AGP 9 has built-in Kotlin: the kotlin-android plugin is not applied, only
// declared to pin the Kotlin version (the same as the Compose compiler's).
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
