// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

plugins {
  id("org.jetbrains.kotlin.jvm")
}

dependencies {
  compileOnly(libs.android.lint.api)
  compileOnly(libs.android.lint.checks)
  compileOnly(libs.kotlin.stdlib)

  testImplementation(libs.android.lint)
  testImplementation(libs.android.lint.tests)
  testImplementation(libs.android.lint.testutils)
  testImplementation(libs.junit)
}

tasks.jar {
  manifest {
    attributes("Lint-Registry-v2" to "com.google.firebase.lint.checks.CheckRegistry")
  }
}
