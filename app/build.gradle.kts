plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dharmil.displayoff"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dharmil.displayoff"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // All four Bouncy Castle artifacts carry the same license text.
            // Keep one copy in the APK instead of dropping the notice.
            pickFirsts += "META-INF/LICENSE.md"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Pinned release; Apache-2.0. It implements Android Wireless Debugging's
    // TLS pairing/ADB transport without bundling an adb executable.
    implementation("com.github.rhythmcache:adb-kt:v1.0.13")

    // adb-kt currently requests 1.85. Pin the current upstream hardening
    // release for the ADB TLS implementation and its transitive users.
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.86")
    implementation("org.bouncycastle:bctls-jdk18on:1.86")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
