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
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class CrashlyticsVariantExtensionTest {

  @Test
  fun `variant extension properties are set correctly`() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("com.android.application")
    project.plugins.apply("com.google.firebase.crashlytics")

    val android = project.extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = 33
    android.namespace = "com.google.firebase.crashlytics.test"
    android.buildTypes {
      getByName("debug") { buildType ->
        buildType.extensions.configure(CrashlyticsExtension::class.java) { crashlyticsExtension ->
          crashlyticsExtension.mappingFileUploadEnabled = true
          crashlyticsExtension.nativeSymbolUploadEnabled = true
          crashlyticsExtension.unstrippedNativeLibsDir = "path/to/libs"
          crashlyticsExtension.symbolGeneratorType = "csym"
          crashlyticsExtension.breakpadBinary = File("path/to/breakpad")
        }
      }
    }

    val androidComponents =
      project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

    androidComponents.onVariants(androidComponents.selector().withName("debug")) { variant ->
      val variantExtension = variant.getExtension(CrashlyticsVariantExtension::class.java)

      assertThat(variantExtension).isNotNull()
      variantExtension?.let {
        assertThat(it.mappingFileUploadEnabled.get()).isTrue()
        assertThat(it.nativeSymbolUploadEnabled.get()).isTrue()
        assertThat(it.unstrippedNativeLibsOverride.singleFile.path).endsWith("path/to/libs")
        assertThat(it.symbolGeneratorType.get()).isEqualTo("csym")
        assertThat(it.breakpadBinary.get().asFile.path).endsWith("path/to/breakpad")
      }
    }

    // Evaluate the project to trigger onVariants
    (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
  }
}
