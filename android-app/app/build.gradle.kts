plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tcm.admin"
    compileSdk = 36
    buildFeatures { compose = true; buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    val configuredApiBaseUrl = providers.gradleProperty("API_BASE_URL")
        .orElse(providers.environmentVariable("API_BASE_URL"))
        .orElse("https://api.tcm.example.com")
        .get()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val debugApiBaseUrl = providers.gradleProperty("API_BASE_URL")
        .orElse(providers.environmentVariable("API_BASE_URL"))
        .orElse("http://10.0.2.2:3000")
        .get()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    val signingStoreFile = rootProject.file("release.keystore")
    val signingStorePassword = providers.gradleProperty("ANDROID_KEYSTORE_PASSWORD")
        .orElse(providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD"))
        .orNull
    val signingKeyAlias = providers.gradleProperty("ANDROID_KEY_ALIAS")
        .orElse(providers.environmentVariable("ANDROID_KEY_ALIAS"))
        .orNull
    val signingKeyPassword = providers.gradleProperty("ANDROID_KEY_PASSWORD")
        .orElse(providers.environmentVariable("ANDROID_KEY_PASSWORD"))
        .orNull

    defaultConfig {
        applicationId = "com.tcm.admin"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (signingStoreFile.exists() && signingStorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
            create("release") {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            manifestPlaceholders["cleartextTraffic"] = true
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$configuredApiBaseUrl\"")
            manifestPlaceholders["cleartextTraffic"] = false
            if (signingStoreFile.exists() && signingStorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
