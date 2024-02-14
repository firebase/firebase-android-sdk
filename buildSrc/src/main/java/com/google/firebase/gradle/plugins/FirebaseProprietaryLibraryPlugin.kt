/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

class FirebaseProprietaryLibraryPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.apply(plugin = "firebase-library")

    val library = project.extensions.getByType(FirebaseLibraryExtension::class.java)
    library.publishSources = false
    library.customizePom {
      licenses {
        license {
          name.set("Android Software Development Kit License")
          url.set("https://developer.android.com/studio/terms.html")
        }
      }
    }
  }
}
