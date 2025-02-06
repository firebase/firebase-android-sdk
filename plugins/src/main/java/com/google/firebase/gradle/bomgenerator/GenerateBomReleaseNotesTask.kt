/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.gradle.bomgenerator

import com.google.firebase.gradle.plugins.createIfAbsent
import com.google.firebase.gradle.plugins.datamodels.ArtifactDependency
import com.google.firebase.gradle.plugins.datamodels.PomElement
import com.google.firebase.gradle.plugins.datamodels.fullArtifactName
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates the release notes for a bom.
 *
 * @see GenerateBomTask
 */
abstract class GenerateBomReleaseNotesTask : DefaultTask() {
  @get:InputFile abstract val currentBom: RegularFileProperty

  @get:Input abstract val previousBom: Property<PomElement>

  @get:OutputFile abstract val releaseNotesFile: RegularFileProperty

  @get:Internal abstract val previousBomVersions: MapProperty<String, String?>

  @TaskAction
  fun generate() {
    val bom = PomElement.fromFile(currentBom.asFile.get())
    val currentDeps = bom.dependencyManagement?.dependencies.orEmpty()
    val previousDeps = previousBom.get().dependencyManagement?.dependencies.orEmpty()
    previousBomVersions.set(previousDeps.associate { it.fullArtifactName to it.version })

    val sortedDependencies = currentDeps.sortedBy { it.toString() }

    val headingId = "{: #bom_v${bom.version.replace(".", "-")}}"

    releaseNotesFile.asFile
      .get()
      .createIfAbsent()
      .writeText(
        """
         |### {{firebase_bom_long}} ({{bill_of_materials}}) version ${bom.version} $headingId
         |{% comment %}
         |These library versions must be flat-typed, do not use variables.
         |The release note for this BoM version is a library-version snapshot.
         |{% endcomment %}
         |
         |<section class="expandable">
         |  <p class="showalways">
         |    Firebase Android SDKs mapped to this {{bom}} version
         |  </p>
         |  <p>
         |    Libraries that were versioned with this release are in highlighted rows.
         |    <br>Refer to a library's release notes (on this page) for details about its
         |    changes.
         |  </p>
         |  <table>
         |    <thead>
         |      <th>Artifact name</th>
         |      <th>Version mapped<br>to previous {{bom}} v${previousBom.get().version}</th>
         |      <th>Version mapped<br>to this {{bom}} v${bom.version}</th>
         |    </thead>
         |    <tbody>
         |${sortedDependencies.joinToString("\n") { artifactToListEntry(it) }.prependIndent("      ")}
         |    </tbody>
         |  </table>
         |</section>
         |
        """
          .trimMargin()
      )
  }

  private fun artifactToListEntry(artifact: ArtifactDependency): String {
    val previousVersion = previousBomVersions.get()[artifact.fullArtifactName] ?: "N/A"
    val artifactName = "${artifact.groupId}:${artifact.artifactId}"

    return if (artifact.version != previousVersion) {
      """
       |<tr class="alt">
       |  <td><b>${artifactName}</b></td>
       |  <td><b>$previousVersion</b></td>
       |  <td><b>${artifact.version}</b></td>
       |</tr>
      """
        .trimMargin()
    } else {
      """
       |<tr>
       |  <td>${artifactName}</td>
       |  <td>$previousVersion</td>
       |  <td>${artifact.version}</td>
       |</tr>
      """
        .trimMargin()
    }
  }
}
