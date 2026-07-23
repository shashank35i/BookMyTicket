plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.bookmyticket"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bookmyticket"
        minSdk = 25
        targetSdk = 34
        versionCode = 1
        versionName = "3.7"
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // Firebase BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)

    // AndroidX & Material UI
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("androidx.core:core:1.12.0") // For Animator, AnimatorListenerAdapter
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.scenecore) // For SplashScreen API

    // Testing
    testImplementation(libs.junit)

    // Camera
    implementation(libs.barcode.scanning)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.compiler)

    // ZXing
    implementation(libs.core)
    implementation(libs.zxing.core.v351)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Firebase UI
    implementation("com.firebaseui:firebase-ui-database:8.0.2")

    // Networking & Payment
    implementation(libs.okhttp)
    implementation(libs.checkout)
    implementation("com.razorpay:checkout:1.6.26")
    implementation("com.razorpay:razorpay-java:1.4.3")

    // Google Play Services
    implementation(libs.play.services.safetynet)
    implementation(libs.play.services.tasks.v1802)
    implementation("com.google.android.gms:play-services-basement:18.1.0")

    // ML Kit
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.0")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.0")
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06") // Unified version

    // Others
    implementation(libs.gson)
    implementation(libs.shimmer)
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.google.firebase:firebase-appcheck-playintegrity:17.1.0")


    implementation (libs.firebase.messaging)
}