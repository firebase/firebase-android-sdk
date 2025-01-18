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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer
import com.google.firebase.gradle.plugins.license.LicenseResolverPlugin
import com.google.firebase.gradle.plugins.semver.ApiDiffer
import com.google.firebase.gradle.plugins.semver.GmavenCopier
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Plugin for Android Firebase Libraries.
 *
 * ```kts
 * plugins {
 *   id("firebase-library")
 * }
 * ```
 *
 * @see [FirebaseJavaLibraryPlugin]
 * @see [BaseFirebaseLibraryPlugin]
 * @see [FirebaseLibraryExtension]
 */
class FirebaseAndroidLibraryPlugin : BaseFirebaseLibraryPlugin() {

  override fun apply(project: Project) {
    project.apply<LibraryPlugin>()
    project.apply<LicenseResolverPlugin>()

    setupAndroidLibraryExtension(project)
    registerMakeReleaseNotesTask(project)

    // reduce the likelihood of kotlin module files colliding.
    project.tasks.withType<KotlinCompile> {
      kotlinOptions.freeCompilerArgs = listOf("-module-name", kotlinModuleName(project))
      kotlinOptions.jvmTarget = "1.8"
    }

    project.apply<DackkaPlugin>()
    project.apply<GitSubmodulePlugin>()

    project.tasks.named("preBuild").dependsOn("updateGitSubmodules")
  }

  private fun setupAndroidLibraryExtension(project: Project) {
    val firebaseLibrary =
      project.extensions.create<FirebaseLibraryExtension>(
        "firebaseLibrary",
        project,
        LibraryType.ANDROID,
      )
    val android = project.extensions.getByType<LibraryExtension>()
    val components = project.extensions.getByType<LibraryAndroidComponentsExtension>()

    with(android) {
      compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
      }

      buildFeatures.buildConfig = true

      /**
       * If a library's signing config only affects the instrumentation test APK, we need it signed
       * with default debug credentials for Test Lab to accept it.
       */
      buildTypes { named("release") { signingConfig = getByName("debug").signingConfig } }

      defaultConfig { buildConfigField("String", "VERSION_NAME", "\"${project.version}\"") }

      // TODO(b/372022757): Remove when we update robolectric
      testOptions.unitTests.all {
        it.systemProperty("robolectric.dependency.repo.id", "central")
        it.systemProperty("robolectric.dependency.repo.url", "https://repo1.maven.org/maven2")
        it.systemProperty("javax.net.ssl.trustStoreType", "JKS")
      }

      testServer(FirebaseTestServer(project, firebaseLibrary.testLab, android))
    }

    setupDefaults(project, firebaseLibrary)
    setupApiInformationAnalysis(project, android, components)
    setupStaticAnalysis(project, firebaseLibrary)
    getIsPomValidTask(project, firebaseLibrary)
    setupSemverTasks(project, firebaseLibrary, components)
    configurePublishing(project, firebaseLibrary, android)
  }

  private fun setupSemverTasks(
    project: Project,
    firebaseLibrary: FirebaseLibraryExtension,
    components: LibraryAndroidComponentsExtension,
  ) {
    components.onReleaseVariants {
      val aarFile = it.artifacts.get(SingleArtifact.AAR)

      val extractCurrent =
        project.tasks.register<Sync>("extractCurrentClasses") {
          from(project.zipTree(aarFile))
          into(project.layout.buildDirectory.dir("semver/current"))
        }

      val copyPrevious =
        project.tasks.register<GmavenCopier>("copyPreviousArtifacts") {
          groupId.set(firebaseLibrary.groupId)
          version.set(firebaseLibrary.latestGMavenVersion)
          artifactId.set(firebaseLibrary.artifactId)
          outputFile.set(project.layout.buildDirectory.file("semver/previous.aar"))
        }

      val extractPrevious =
        project.tasks.register<Sync>("extractPreviousClasses") {
          from(project.zipTree(copyPrevious.flatMap { it.outputFile }))
          into(project.layout.buildDirectory.dir("semver/previous"))
        }

      val currentJarFile = extractCurrent.map { it.destinationDir.childFile("classes.jar") }
      val previousJarFile = extractPrevious.map { it.destinationDir.childFile("classes.jar") }

      project.tasks.register<ApiDiffer>("semverCheck") {
        currentJar.set(project.layout.file(currentJarFile))
        previousJar.set(project.layout.file(previousJarFile))
        version.set(firebaseLibrary.version)
        previousVersion.set(firebaseLibrary.latestGMavenVersion)
      }
    }
  }

  @Suppress("UnstableApiUsage")
  private fun setupApiInformationAnalysis(
    project: Project,
    android: LibraryExtension,
    components: LibraryAndroidComponentsExtension,
  ) {
    components.onReleaseVariants {
      val kotlinFiles = project.layout.files(it.sources.kotlin?.all)
      val javaFiles = project.layout.files(android.sourceSets.named("main").map { it.java.srcDirs })
      val classpath = it.compileClasspath

      registerApiInfoTask(project, kotlinFiles, classpath)
      registerGenerateApiTxtFileTask(project, kotlinFiles, classpath)
      registerDocStubsTask(project, javaFiles, classpath)
    }
  }

  private fun configurePublishing(
    project: Project,
    firebaseLibrary: FirebaseLibraryExtension,
    android: LibraryExtension,
  ) {
    android.publishing.singleVariant("release") { withSourcesJar() }

    project.tasks.withType<GenerateModuleMetadata> { isEnabled = false }

    configurePublishing(project, firebaseLibrary)
  }
}
