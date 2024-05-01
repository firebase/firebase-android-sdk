// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.jetbrainsKotlinAndroid) apply false

  id("com.google.gms.google-services") version "4.4.1" apply false
  id("com.google.firebase.crashlytics") version "2.9.9" apply false
}
