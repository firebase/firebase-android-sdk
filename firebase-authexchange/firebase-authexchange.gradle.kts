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
  val googleTruthVersion: String by rootProject
  val kotlinVersion: String by rootProject
  val robolectricVersion: String by rootProject

  implementation(project(":firebase-common"))
  implementation(project(":firebase-common:ktx"))
  implementation(project(":firebase-components"))
  implementation(project(":firebase-authexchange-interop"))

  implementation("androidx.annotation:annotation:1.5.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
  implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")


  testImplementation("androidx.test:core:1.5.0")
  testImplementation("com.google.truth:truth:$googleTruthVersion")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:$robolectricVersion")
}
