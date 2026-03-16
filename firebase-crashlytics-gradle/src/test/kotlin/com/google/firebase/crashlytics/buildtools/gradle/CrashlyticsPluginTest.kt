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

class CrashlyticsPluginTest {

  @Test
  fun `plugin registers tasks`() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("com.android.application")
    val android = project.extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = 33
    android.namespace = "com.google.firebase.crashlytics.test"
    project.plugins.apply("com.google.firebase.crashlytics")

    // Evaluate the project to trigger task creation
    project.getTasksByName("preBuild", false)

    assertThat(project.tasks.findByName("injectCrashlyticsMappingFileIdDebug")).isNotNull()
    assertThat(project.tasks.findByName("injectCrashlyticsVersionControlInfoDebug")).isNotNull()
  }
}
