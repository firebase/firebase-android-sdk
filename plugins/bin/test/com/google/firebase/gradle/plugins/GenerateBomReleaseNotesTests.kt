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

package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.bomgenerator.GenerateBomReleaseNotesTask
import com.google.firebase.gradle.plugins.datamodels.ArtifactDependency
import com.google.firebase.gradle.plugins.datamodels.DependencyManagementElement
import com.google.firebase.gradle.plugins.datamodels.PomElement
import com.google.firebase.gradle.shouldBeText
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.file.shouldExist
import java.io.File
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GenerateBomReleaseNotesTests : FunSpec() {
  @Rule @JvmField val testProjectDir = TemporaryFolder()

  @Test
  fun `generates the release notes`() {
    val dependencies =
      listOf(
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-auth",
          version = "10.0.0",
        ),
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-firestore",
          version = "10.0.0",
        ),
      )
    val bom = makeBom("1.0.0", dependencies)
    val file = makeReleaseNotes(bom, bom)

    file.readText().trim() shouldBeText
      """
            ### {{firebase_bom_long}} ({{bill_of_materials}}) version 1.0.0 {: #bom_v1-0-0}
            {% comment %}
            These library versions must be flat-typed, do not use variables.
            The release note for this BoM version is a library-version snapshot.
            {% endcomment %}
           
            <section class="expandable">
              <p class="showalways">
                Firebase Android SDKs mapped to this {{bom}} version
              </p>
              <p>
                Libraries that were versioned with this release are in highlighted rows.
                <br>Refer to a library's release notes (on this page) for details about its
                changes.
              </p>
              <table>
                <thead>
                  <th>Artifact name</th>
                  <th>Version mapped<br>to previous {{bom}} v1.0.0</th>
                  <th>Version mapped<br>to this {{bom}} v1.0.0</th>
                </thead>
                <tbody>
                  <tr>
                    <td>com.google.firebase:firebase-auth</td>
                    <td>10.0.0</td>
                    <td>10.0.0</td>
                  </tr>
                  <tr>
                    <td>com.google.firebase:firebase-firestore</td>
                    <td>10.0.0</td>
                    <td>10.0.0</td>
                  </tr>
                </tbody>
              </table>
            </section>
        """
        .trimIndent()
  }

  @Test
  fun `sorts the entries alphabetically`() {
    val dependencies =
      listOf(
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-firestore",
          version = "10.0.0",
        ),
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-auth",
          version = "10.0.0",
        ),
      )
    val bom = makeBom("1.0.0", dependencies)
    val file = makeReleaseNotes(bom, bom)

    file.readText().trim() shouldBeText
      """
            ### {{firebase_bom_long}} ({{bill_of_materials}}) version 1.0.0 {: #bom_v1-0-0}
            {% comment %}
            These library versions must be flat-typed, do not use variables.
            The release note for this BoM version is a library-version snapshot.
            {% endcomment %}
           
            <section class="expandable">
              <p class="showalways">
                Firebase Android SDKs mapped to this {{bom}} version
              </p>
              <p>
                Libraries that were versioned with this release are in highlighted rows.
                <br>Refer to a library's release notes (on this page) for details about its
                changes.
              </p>
              <table>
                <thead>
                  <th>Artifact name</th>
                  <th>Version mapped<br>to previous {{bom}} v1.0.0</th>
                  <th>Version mapped<br>to this {{bom}} v1.0.0</th>
                </thead>
                <tbody>
                  <tr>
                    <td>com.google.firebase:firebase-auth</td>
                    <td>10.0.0</td>
                    <td>10.0.0</td>
                  </tr>
                  <tr>
                    <td>com.google.firebase:firebase-firestore</td>
                    <td>10.0.0</td>
                    <td>10.0.0</td>
                  </tr>
                </tbody>
              </table>
            </section>
        """
        .trimIndent()
  }

  @Test
  fun `correctly formats changed dependencies`() {
    val oldDependencies =
      listOf(
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-auth",
          version = "10.0.0",
        ),
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-analytics",
          version = "10.0.0",
        ),
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-vertexai",
          version = "10.0.0",
        ),
      )
    val newDependencies =
      listOf(
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-auth",
          version = "10.0.0",
        ),
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-firestore",
          version = "10.0.0",
        ),
        ArtifactDependency(
          groupId = "com.google.firebase",
          artifactId = "firebase-vertexai",
          version = "11.0.0",
        ),
      )
    val oldBom = makeBom("1.0.0", oldDependencies)
    val newBom = makeBom("2.0.0", newDependencies)
    val file = makeReleaseNotes(oldBom, newBom)

    file.readText().trim() shouldBeText
      """
          ### {{firebase_bom_long}} ({{bill_of_materials}}) version 2.0.0 {: #bom_v2-0-0}
          {% comment %}
          These library versions must be flat-typed, do not use variables.
          The release note for this BoM version is a library-version snapshot.
          {% endcomment %}

          <section class="expandable">
            <p class="showalways">
              Firebase Android SDKs mapped to this {{bom}} version
            </p>
            <p>
              Libraries that were versioned with this release are in highlighted rows.
              <br>Refer to a library's release notes (on this page) for details about its
              changes.
            </p>
            <table>
              <thead>
                <th>Artifact name</th>
                <th>Version mapped<br>to previous {{bom}} v1.0.0</th>
                <th>Version mapped<br>to this {{bom}} v2.0.0</th>
              </thead>
              <tbody>
                <tr>
                  <td>com.google.firebase:firebase-auth</td>
                  <td>10.0.0</td>
                  <td>10.0.0</td>
                </tr>
                <tr class="alt">
                  <td><b>com.google.firebase:firebase-firestore</b></td>
                  <td><b>N/A</b></td>
                  <td><b>10.0.0</b></td>
                </tr>
                <tr class="alt">
                  <td><b>com.google.firebase:firebase-vertexai</b></td>
                  <td><b>10.0.0</b></td>
                  <td><b>11.0.0</b></td>
                </tr>
              </tbody>
            </table>
          </section>
      """
        .trimIndent()
  }

  private fun makeTask(
    configure: GenerateBomReleaseNotesTask.() -> Unit
  ): TaskProvider<GenerateBomReleaseNotesTask> {
    val project = ProjectBuilder.builder().withProjectDir(testProjectDir.root).build()

    return project.tasks.register<GenerateBomReleaseNotesTask>("generateBomReleaseNotes") {
      releaseNotesFile.set(project.layout.buildDirectory.file("bomReleaseNotes.md"))

      configure()
    }
  }

  private fun makeReleaseNotes(previousBom: PomElement, currentBom: PomElement): File {
    val currentBomFile = testProjectDir.newFile("current.bom")
    currentBom.toFile(currentBomFile)

    val task = makeTask {
      this.currentBom.set(currentBomFile)
      this.previousBom.set(previousBom)
    }

    val file =
      task.get().let {
        it.generate()
        it.releaseNotesFile.asFile.get()
      }

    file.shouldExist()

    return file
  }

  private fun makeBom(version: String, dependencies: List<ArtifactDependency>): PomElement {
    return PomElement.fromFile(emptyBom)
      .copy(version = version, dependencyManagement = DependencyManagementElement(dependencies))
  }

  companion object {
    private val resourcesDirectory = File("src/test/resources")
    private val testResources = resourcesDirectory.childFile("Bom")
    private val emptyBom = testResources.childFile("empty-bom.pom")
  }
}
