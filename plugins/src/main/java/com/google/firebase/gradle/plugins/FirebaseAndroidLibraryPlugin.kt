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

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.google.firebase.gradle.plugins.LibraryType.ANDROID
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestServer
import com.google.firebase.gradle.plugins.license.LicenseResolverPlugin
import com.google.firebase.gradle.plugins.semver.ApiDiffer
import com.google.firebase.gradle.plugins.semver.GmavenCopier
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.Copy
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

    project.tasks.getByName("preBuild").dependsOn("updateGitSubmodules")
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
    setupApiInformationAnalysis(project, android)
    setupStaticAnalysis(project, firebaseLibrary)
    getIsPomValidTask(project, firebaseLibrary)
    setupVersionCheckTasks(project, firebaseLibrary)
    configurePublishing(project, firebaseLibrary, android)
  }

  private fun setupVersionCheckTasks(project: Project, firebaseLibrary: FirebaseLibraryExtension) {
    project.tasks.register<GmavenVersionChecker>("gmavenVersionCheck") {
      groupId.set(firebaseLibrary.groupId)
      artifactId.set(firebaseLibrary.artifactId)
      version.set(firebaseLibrary.version)
      latestReleasedVersion.set(firebaseLibrary.latestReleasedVersion.orElse(""))
    }
    project.mkdir("semver")
    project.mkdir("semver/previous-version")
    project.tasks.register<GmavenCopier>("copyPreviousArtifacts") {
      dependsOn("bundleReleaseAar")
      project.file("semver/previous.aar").delete()

      groupId.value(firebaseLibrary.groupId)
      artifactId.value(firebaseLibrary.artifactId)
      aarAndroidFile.value(true)
      filePath.value(project.file("semver/previous.aar").absolutePath)
    }
    val artifact = firebaseLibrary.artifactId.get()
    val releaseAar = if (artifact.contains("-ktx")) "ktx-release.aar" else "${artifact}-release.aar"
    project.tasks.register<Copy>("extractCurrentClasses") {
      dependsOn("bundleReleaseAar")

      from(project.zipTree("build/outputs/aar/${releaseAar}"))
      into(project.file("semver/current-version"))
    }

    project.tasks.register<Copy>("extractPreviousClasses") {
      dependsOn("copyPreviousArtifacts")
      if (
        GmavenHelper(firebaseLibrary.groupId.get(), firebaseLibrary.artifactId.get())
          .isPresentInGmaven()
      ) {
        from(project.zipTree("semver/previous.aar"))
        into(project.file("semver/previous-version"))
      }
    }

    val currentJarFile = project.file("semver/current-version/classes.jar").absolutePath

    val previousJarFile = project.file("semver/previous-version/classes.jar").absolutePath
    project.tasks.register<ApiDiffer>("semverCheck") {
      dependsOn("extractCurrentClasses")
      dependsOn("extractPreviousClasses")
      currentJar.value(currentJarFile)
      previousJar.value(previousJarFile)
      version.value(firebaseLibrary.version)
      previousVersionString.value(
        GmavenHelper(firebaseLibrary.groupId.get(), firebaseLibrary.artifactId.get())
          .getLatestReleasedVersion()
      )
    }

    setupMetalavaSemver(project, firebaseLibrary)
  }

  private fun setupApiInformationAnalysis(project: Project, android: LibraryExtension) {
    val srcDirs = project.files(android.sourceSets.getByName("main").java.srcDirs)

    val mainSourceSets = android.sourceSets.getByName("main")
    val getKotlinDirectories = mainSourceSets::class.java.getDeclaredMethod("getKotlinDirectories")
    val kotlinSrcDirs = getKotlinDirectories.invoke(mainSourceSets)

    val apiInfo = getApiInfo(project, project.files(kotlinSrcDirs))
    val generateApiTxt = getGenerateApiTxt(project, project.files(kotlinSrcDirs))
    val docStubs = getDocStubs(project, srcDirs)

    project.tasks.getByName("check").dependsOn(docStubs)
    android.libraryVariants.all {
      if (name == "release") {
        val jars =
          compileConfiguration.incoming
            .artifactView {
              attributes {
                attribute(Attribute.of("artifactType", String::class.java), "android-classes")
              }
            }
            .artifacts
            .artifactFiles
        apiInfo.configure { classPath = jars }
        generateApiTxt.configure { classPath = jars }
        docStubs.configure { classPath = jars }
      }
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
