plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.scrollingo.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scrollingo.app"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Base URL the app fetches expanded word packs from over WiFi.
        // Leave empty to disable online sync (app then uses bundled words only).
        // Expected layout at this URL: manifest.json + words_<code>.json files.
        buildConfigField("String", "WORDPACK_BASE_URL", "\"\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            // Surface assertion failures from JVM unit tests in the console.
            all { it.testLogging { events("passed", "skipped", "failed") } }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Test-only. These never reach the APK.
    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM: the android.jar stub returns null for every
    // method, so Dictionary's parser cannot be tested without it.
    testImplementation("org.json:json:20240303")
}
