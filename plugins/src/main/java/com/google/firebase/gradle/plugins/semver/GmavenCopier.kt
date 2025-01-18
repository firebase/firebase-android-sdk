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

import com.google.firebase.gradle.plugins.ModuleVersion
import com.google.firebase.gradle.plugins.services.GMavenService
import com.google.firebase.gradle.plugins.skipGradleTask
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Fetch the published artifact for a library from GMaven.
 *
 * You can use this instead of [GMavenService] when you want to cache the artifact between builds.
 *
 * @property groupId The library's group id
 * @property artifactId The library's artifact id
 * @property version The version of the artifact to fetch
 * @property outputFile Where to save the downloaded artifact to
 */
abstract class GmavenCopier : DefaultTask() {
  @get:Input abstract val groupId: Property<String>

  @get:Input abstract val artifactId: Property<String>

  @get:Input @get:Optional abstract val version: Property<ModuleVersion>

  @get:OutputFile abstract val outputFile: RegularFileProperty

  @get:ServiceReference("gmaven") abstract val gmaven: Property<GMavenService>

  @TaskAction
  fun run() {
    if (!version.isPresent) skipGradleTask("Library hasn't been published")

    val artifact = gmaven.get().artifact(groupId.get(), artifactId.get(), version.get().toString())

    artifact.copyTo(outputFile.get().asFile)
  }
}
