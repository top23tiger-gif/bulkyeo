plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 서명 키: GitHub Secrets 에 키가 있으면 그 키로, 없으면 임시 키로 서명
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "com.bulkyeo.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bulkyeo.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 6
        versionName = "6.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 디버그 기능이 꺼진 정식(release) 빌드
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 기기 안에서만 동작하는 음성 인식 (인터넷 불필요)
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
