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

package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.plugins.semver.ApiDiffer
import com.google.firebase.gradle.plugins.semver.GmavenCopier
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.utils.named

/**
 * Plugin for Java Firebase Libraries.
 *
 * ```kts
 * plugins {
 *   id("firebase-java-library")
 * }
 * ```
 *
 * @see [FirebaseAndroidLibraryPlugin]
 * @see [BaseFirebaseLibraryPlugin]
 * @see [FirebaseLibraryExtension]
 */
class FirebaseJavaLibraryPlugin : BaseFirebaseLibraryPlugin() {

  override fun apply(project: Project) {
    project.apply<JavaLibraryPlugin>()

    setupFirebaseLibraryExtension(project)
    registerMakeReleaseNotesTask(project)

    project.apply<DackkaPlugin>()

    // reduce the likelihood of kotlin module files colliding.
    project.tasks.withType<KotlinCompile> {
      kotlinOptions.freeCompilerArgs = listOf("-module-name", kotlinModuleName(project))
    }
  }

  private fun setupFirebaseLibraryExtension(project: Project) {
    val firebaseLibrary =
      project.extensions.create<FirebaseLibraryExtension>(
        "firebaseLibrary",
        project,
        LibraryType.JAVA,
      )

    setupDefaults(project, firebaseLibrary)
    setupStaticAnalysis(project, firebaseLibrary)
    setupApiInformationAnalysis(project)
    getIsPomValidTask(project, firebaseLibrary)
    registerGmavenVersionCheck(project, firebaseLibrary)
    setupSemverTasks(project, firebaseLibrary)
    configurePublishing(project, firebaseLibrary)
  }

  private fun setupSemverTasks(project: Project, firebaseLibrary: FirebaseLibraryExtension) {
    val copyPrevious =
      project.tasks.register<GmavenCopier>("copyPreviousArtifacts") {
        version.set(firebaseLibrary.latestGMavenVersion)
        artifactId.set(firebaseLibrary.artifactId)
        outputFile.set(project.layout.buildDirectory.file("semver/previous.jar"))
      }

    project.tasks.register<ApiDiffer>("semverCheck") {
      currentJar.set(project.tasks.named<Jar>("jar").flatMap { it.archiveFile })
      previousJar.set(copyPrevious.flatMap { it.outputFile })
      version.set(firebaseLibrary.version)
      previousVersion.set(firebaseLibrary.latestGMavenVersion)
    }
  }

  private fun setupApiInformationAnalysis(project: Project) {
    // TODO(protobuf-gradle-plugin/issues/694): Use named instead of getByName when fixed
    val mainSourceSet =
      project.extensions.getByType<JavaPluginExtension>().sourceSets.getByName("main")

    val srcDirs = project.layout.files(mainSourceSet.java.srcDirs)
    val classpath = project.layout.files(mainSourceSet.compileClasspath)

    registerApiInfoTask(project, srcDirs, classpath)
    registerGenerateApiTxtFileTask(project, srcDirs, classpath)
    registerDocStubsTask(project, srcDirs, classpath)
  }
}
