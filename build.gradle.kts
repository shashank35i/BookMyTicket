// Top-level build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.services) apply false
}

buildscript {
    repositories {
        google()  // ✅ Ensure 'google()' repository is present
        mavenCentral()
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.2")  }
}
