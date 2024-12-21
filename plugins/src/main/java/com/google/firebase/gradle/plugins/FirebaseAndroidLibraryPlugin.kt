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
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.firebase.gradle.plugins.LibraryType.ANDROID
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer
import com.google.firebase.gradle.plugins.license.LicenseResolverPlugin
import com.google.firebase.gradle.plugins.semver.ApiDiffer
import com.google.firebase.gradle.plugins.semver.ApiDifferNew
import com.google.firebase.gradle.plugins.semver.GmavenCopier
import com.google.firebase.gradle.plugins.semver.GmavenCopierNew
import org.gradle.api.Incubating
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Copy
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
      project.extensions.create<FirebaseLibraryExtension>("firebaseLibrary", project, ANDROID)
    val android = project.extensions.getByType<LibraryExtension>()

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
    setupApiInformationAnalysis(project)
    setupStaticAnalysis(project, firebaseLibrary)
    getIsPomValidTask(project, firebaseLibrary)
    setupVersionCheckTasks(project, firebaseLibrary)
    configurePublishing(project, firebaseLibrary, android)
  }

  private fun setupVersionCheckTasks(project: Project, firebaseLibrary: FirebaseLibraryExtension) {

    val components = project.extensions.getByType<LibraryAndroidComponentsExtension>()
    val releaseSelector = components.selector().withBuildType("release")

    val gmavenHelper = project.provider {
      GmavenHelper(firebaseLibrary.groupId.get(), firebaseLibrary.artifactId.get())
    }

    components.onVariants(releaseSelector) {
      val aarFile = it.artifacts.get(SingleArtifact.AAR)

      // TODO: there may be a better unzip task
      val extractCurrent = project.tasks.register<Sync>("extractCurrentClasses") {
        from(project.zipTree(aarFile))
        into(project.layout.buildDirectory.dir("semver/current"))
      }

      val copyPrevious = project.tasks.register<GmavenCopierNew>("copyPreviousArtifacts") {
        onlyIf { gmavenHelper.get().isPresentInGmaven() }

        groupId.set(firebaseLibrary.groupId)
        artifactId.set(firebaseLibrary.artifactId)
        aarAndroidFile.set(true)
        outputFile.set(project.layout.buildDirectory.file("semver/previous.aar"))
      }

      val extractPrevious = project.tasks.register<Sync>("extractPreviousClasses") {
        from(project.zipTree(copyPrevious.flatMap { it.outputFile }))
        into(project.layout.buildDirectory.dir("semver/previous"))
      }

      val currentJarFile = extractCurrent.map { it.destinationDir.childFile("classes.jar") }
      val previousJarFile = extractPrevious.map { it.destinationDir.childFile("classes.jar") }

      project.tasks.register<ApiDifferNew>("semverCheck") {
        currentJar.set(project.layout.file(currentJarFile))
        previousJar.set(project.layout.file(previousJarFile))
        version.set(firebaseLibrary.version)
        previousVersionString.set(gmavenHelper.map { it.getLatestReleasedVersion() })
      }
    }

    project.tasks.register<GmavenVersionChecker>("gmavenVersionCheck") {
      groupId.set(firebaseLibrary.groupId)
      artifactId.set(firebaseLibrary.artifactId)
      version.set(firebaseLibrary.version)
      latestReleasedVersion.set(firebaseLibrary.latestReleasedVersion.orElse(""))
    }

//    project.mkdir("semver")
//    project.mkdir("semver/previous-version")
//    project.tasks.register<GmavenCopier>("copyPreviousArtifacts") {
//      dependsOn("bundleReleaseAar")
//      project.file("semver/previous.aar").delete()
//
//      groupId.set(firebaseLibrary.groupId)
//      artifactId.set(firebaseLibrary.artifactId)
//      aarAndroidFile.set(true)
//      filePath.set(project.file("semver/previous.aar").absolutePath)
//    }


//    val artifact = firebaseLibrary.artifactId.map {
//      if (it.contains("-ktx")) "ktx-release.aar" else "$it-release.aar"
//    }
//    project.tasks.register<Copy>("extractCurrentClasses") {
//      dependsOn("bundleReleaseAar")
//
//      from(artifact.map {
//        project.zipTree("build/outputs/aar/$it")
//      })
//      into(project.file("semver/current-version"))
//    }

//    project.tasks.register<Copy>("extractPreviousClasses") {
//      dependsOn("copyPreviousArtifacts")
//
//      onlyIf { gmavenHelper.get().isPresentInGmaven() }
//
//      from(project.zipTree("semver/previous.aar"))
//      into(project.file("semver/previous-version"))
//    }

//    val currentJarFile = project.file("semver/current-version/classes.jar").absolutePath
//
//    val previousJarFile = project.file("semver/previous-version/classes.jar").absolutePath
//    project.tasks.register<ApiDiffer>("semverCheck") {
//      dependsOn("extractCurrentClasses")
//      dependsOn("extractPreviousClasses")
//
//      currentJar.set(currentJarFile)
//      previousJar.set(previousJarFile)
//      version.set(firebaseLibrary.version)
//      previousVersionString.set(gmavenHelper.map { it.getLatestReleasedVersion() })
//    }
  }

  @Suppress("UnstableApiUsage")
  private fun setupApiInformationAnalysis(project: Project) {
    val components = project.extensions.getByType<LibraryAndroidComponentsExtension>()
    val releaseSelector = components.selector().withBuildType("release")

    components.onVariants(releaseSelector) {
      val kotlinFiles = project.files(it.sources.kotlin?.all)
      val javaFiles = project.files(it.sources.java?.all)
      val classpath = project.files(it.compileClasspath)

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
