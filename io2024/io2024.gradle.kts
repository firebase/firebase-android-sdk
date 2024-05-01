plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.gms.google-services")
  id("com.google.firebase.crashlytics")
}

android {
  namespace = "com.firebase.io2024.whoami"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.firebase.io2024.whoami"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
  buildFeatures {
    compose = true
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.4.8"
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation("com.google.firebase:firebase-crashlytics:18.6.4")
  implementation("com.google.firebase:firebase-vertexai:16.0.0-alpha03")

  implementation("androidx.activity:activity-compose:1.5.1")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.core:core-ktx:1.8.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
  implementation(platform("androidx.compose:compose-bom:2022.10.00"))
}
