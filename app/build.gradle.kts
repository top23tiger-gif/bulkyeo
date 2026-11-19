plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bulkyeo.english"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bulkyeo.english"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // 엄마 폰에 직접 설치하는 가족용 앱이라 서명 키를 저장소에 함께 둔다.
    // 같은 키로 서명해야 나중에 새 버전을 덮어서 설치할 수 있다(알림 설정 유지).
    signingConfigs {
        create("family") {
            storeFile = rootProject.file("keystore/family.jks")
            storePassword = "bulkyeo-family"
            keyAlias = "family"
            keyPassword = "bulkyeo-family"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("family")
        }
        debug {
            signingConfig = signingConfigs.getByName("family")
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
}
