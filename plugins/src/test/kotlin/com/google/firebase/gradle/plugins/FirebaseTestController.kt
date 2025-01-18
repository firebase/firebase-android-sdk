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

import io.kotest.matchers.nulls.shouldNotBeNull
import java.io.File
import kotlin.io.path.Path
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder

/**
 * Util class for providing a common ground for tests that need to dynamically create their own
 * [projects][FirebaseTestProject].
 *
 * This class effectively acts as the root controller during multi module testing. The general
 * workflow can be defined as such:
 * - Wrap your test in a [FirebaseTestController]
 * - Create your test [projects][FirebaseTestProject]
 * - Link your test projects with the controller via [withProjects]
 * - Use the [GradleRunner] to run your build at the [rootDirectory]
 * - Use any of the provided extension methods to inspect the build files and make assertions in
 *   your test
 *
 * Some example of the provided extension methods are:
 * - [FirebaseTestProject.buildFile]
 * - [FirebaseTestProject.pom]
 *
 * To see a more involved example of this workflow, you can take a look at test classes that
 * implement this class, eg; [UpdatePinnedDependenciesTests] and [PublishingPluginTests].
 *
 * @property rootDirectory the root [TemporaryFolder] that all files will be created under.
 * @see project
 * @see createReleaseWithConfig
 * @see createReleaseWithProjects
 * @see withProjects
 */
class FirebaseTestController(val rootDirectory: TemporaryFolder) {

  /**
   * The `build.gradle.kts` file for this project under the [rootDirectory].
   *
   * @see project
   */
  val TestProject.buildFile: File
    get() = project(this).childFile("build.gradle.kts")

  /**
   * The compiled [Pom] for this [FirebaseTestProject], or null if not found.
   *
   * Looks for the [Pom] file under the root `m2repository` for this [FirebaseTestProject]. The file
   * is then converted into a [Pom] via [Pom.parse].
   *
   * It's expected that the pom lives under the directory:
   * ```
   * <ROOT_PROJECT>/build/m2repository/com/google/firebase/<library>/<version>/
   * ```
   *
   * @see pom
   */
  fun FirebaseTestProject.pomOrNull(): Pom? {
    val projectReleaseBuild =
      rootDirectory.root
        .childFile("build/m2repository")
        .childFile(group.replace('.', '/'))
        .childFile("$name/$expectedVersion")

    val pomFile = projectReleaseBuild.walk().find { it.isFile && it.name.endsWith(".pom") }

    return pomFile?.let { Pom.parse(it) }
  }

  /**
   * The compiled [Pom] for this [FirebaseTestProject].
   *
   * A variant of [pomOrNull] that makes an assertion that the [Pom] should not be null.
   *
   * @see pomOrNull
   * @see shouldNotBeNull
   */
  val FirebaseTestProject.pom: Pom
    get() = pomOrNull().shouldNotBeNull()

  /**
   * The sub directory for the given [project] under the parent [rootDirectory].
   *
   * *Alternatively known as the project's directory*
   *
   * @see include
   */
  fun project(project: TestProject) = rootDirectory.root.childFile(project.name)

  /**
   * Creates a subdirectory for the given [project] under [rootDirectory].
   *
   * Will use [FirebaseTestProject.generateBuildFile] to create the relevant [buildFile], and will
   * populate the [AndroidManifest.xml][ANDROID_MANIFEST] accordingly.
   *
   * Usually, you don't want to invoke this method yourself, as it will not include the [project] in
   * the build process. What you're probably looking for is [withProjects].
   *
   * @see project
   * @see withProjects
   */
  fun include(project: TestProject) {
    rootDirectory.newFolder("${project.name}/src/main")
    rootDirectory.newFile("${project.name}/build.gradle.kts").writeText(project.generateBuildFile())
    rootDirectory
      .newFile("${project.name}/src/main/AndroidManifest.xml")
      .writeText(ANDROID_MANIFEST)
  }

  /**
   * Creates the build files for the root project, and subsequently the provided [projects].
   *
   * All of the provided [projects] will be included in the root `settings.gradle.kts` file, such
   * that they are invoked during the build process.
   *
   * @param projects a variable amount of [FirebaseTestProject] to create subdirectories for and
   *   include in the build process.
   * @see include
   */
  fun withProjects(vararg projects: TestProject) {
    rootDirectory.newFile("build.gradle.kts").writeText(ROOT_PROJECT)
    rootDirectory
      .newFile("settings.gradle.kts")
      .writeText("$ROOT_SETTINGS\n${projects.joinToString("\n") { "include(\"${it.path}\")" }}")

    projects.forEach(this::include)
  }

  /**
   * Creates the actual files for list of source files, under this project's test directory.
   *
   * Writes the content of the source files as well.
   */
  fun sourceFiles(project: TestProject, vararg files: SourceFile) {
    for (file in files) {
      val path = "${project.name}/src/main/java/${file.path}"
      rootDirectory.newFolder("${Path(path).parent}")
      rootDirectory.newFile(path).writeText(file.content)
    }
  }

  /**
   * Creates a `release.json` file at the [rootDirectory].
   *
   * @param release the [ReleaseConfig] to convert into a `json` file.
   * @see createReleaseWithProjects
   */
  fun createReleaseWithConfig(release: ReleaseConfig) {
    release.toFile(rootDirectory.newFile("release.json"))
  }

  /**
   * Creates a `release.json` file at the [rootDirectory].
   *
   * The release will have a [name][ReleaseConfig.name] of `test`. If you would rather have a
   * different name, then pass your own [ReleaseConfig] instead via [createReleaseWithConfig].
   *
   * @param projects a variable amount of [FirebaseTestProject] to include in the release file.
   * @see createReleaseWithConfig
   */
  fun createReleaseWithProjects(vararg projects: FirebaseTestProject) {
    createReleaseWithConfig(ReleaseConfig("test", projects.map { it.path }))
  }

  companion object {
    const val ROOT_SETTINGS =
      """
pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
    maven("https://storage.googleapis.com/android-ci/mvn/") { metadataSources { artifact() } }
  }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven("https://storage.googleapis.com/android-ci/mvn/") { metadataSources { artifact() } }
  }
}
      """
    const val ROOT_PROJECT =
      """
plugins {
  id("PublishingPlugin")
}
      """

    const val ANDROID_MANIFEST =
      """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-sdk android:minSdkVersion="14"/>
</manifest>
     """
  }
}

/**
 * A source file to use in a test.
 *
 * @param path The relative path of the source file.
 * @param content The text content of the source file.
 * @see FirebaseTestController.sourceFiles
 */
data class SourceFile(val path: String, val content: String)
