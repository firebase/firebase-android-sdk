plugins {
  id("firebase-library")
  kotlin("android")
  kotlin("plugin.serialization") version "1.7.20"
}

firebaseLibrary {
  publishSources = true
}

android {
  val targetSdkVersion : Int by rootProject

  compileSdk = targetSdkVersion
  defaultConfig {
    minSdk = 16
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    getByName("main") {
      java.srcDirs("src/main/kotlin")
    }
    getByName("test") {
      java.srcDirs("src/test/kotlin")
    }
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":firebase-common"))
  implementation(project(":firebase-common:ktx"))
  implementation(project(":firebase-components"))
  implementation(project(":firebase-authexchange-interop"))

  implementation(libs.androidx.annotation)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
  implementation(libs.kotlin.stdlib)


  testImplementation(libs.androidx.test.core)
  testImplementation(libs.truth)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
}
