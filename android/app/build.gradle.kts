plugins {
    id("com.android.application")
}

android {
    namespace = "com.easyweb.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.easyweb.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
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

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("org.libsdl.android:SDL2:2.28.5")
    implementation("org.libsdl.android:SDL2_ttf:2.20.2")
    implementation("org.libsdl.android:SDL2_image:2.6.3")
}