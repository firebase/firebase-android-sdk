/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.google.common.truth.Truth.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class CrashlyticsExtensionTest {

  @Test
  fun `extension properties have correct default values`() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("com.android.application")
    project.plugins.apply("com.google.firebase.crashlytics")

    val android = project.extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = 33
    android.namespace = "com.google.firebase.crashlytics.test"

    android.buildTypes.getByName("debug") { buildType ->
      val crashlyticsExtension = buildType.extensions.getByType(CrashlyticsExtension::class.java)
      assertThat(crashlyticsExtension.mappingFileUploadEnabled).isNull()
      assertThat(crashlyticsExtension.nativeSymbolUploadEnabled).isNull()
      assertThat(crashlyticsExtension.unstrippedNativeLibsDir).isNull()
      assertThat(crashlyticsExtension.symbolGeneratorType).isNull()
      assertThat(crashlyticsExtension.breakpadBinary).isNull()
      assertThat(crashlyticsExtension.symbolGenerator).isNull()
    }
  }
}
