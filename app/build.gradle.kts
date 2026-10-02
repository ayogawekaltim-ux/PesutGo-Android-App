import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pesutgo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pesutgo.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField(
            "String",
            "PESUTGO_WEB_ORIGIN",
            "\"https://www.pesutgo.com\""
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            buildConfigField(
                "Boolean",
                "ENABLE_WEBVIEW_DEBUG",
                "true"
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField(
                "Boolean",
                "ENABLE_WEBVIEW_DEBUG",
                "false"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/*
 * Kotlin 2.3.x menggunakan compilerOptions DSL.
 * Jangan menggunakan kotlinOptions { jvmTarget = "17" }
 * karena sudah deprecated/error pada Kotlin 2.3.
 */
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
