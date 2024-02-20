/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.gradle.plugins.semver

import com.google.firebase.gradle.plugins.GmavenHelper
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class GmavenCopier : DefaultTask() {

  @get:Input abstract val groupId: Property<String>

  @get:Input abstract val aarAndroidFile: Property<Boolean>

  @get:Input abstract val artifactId: Property<String>

  @get:Input abstract val filePath: Property<String>

  @TaskAction
  fun run() {
    val mavenHelper = GmavenHelper(groupId.get(), artifactId.get())
    if (!mavenHelper.isPresentInGmaven()) {
      return
    }
    val gMavenPath =
      mavenHelper.getArtifactForVersion(
        mavenHelper.getLatestReleasedVersion(),
        !aarAndroidFile.get()
      )
    URL(gMavenPath).openStream().use { Files.copy(it, Paths.get(filePath.get())) }
  }
}
