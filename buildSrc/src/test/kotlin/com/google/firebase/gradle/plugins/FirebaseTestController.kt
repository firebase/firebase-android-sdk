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
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder

/**
 * Util class for providing a common ground for tests that need to dynamically create [projects]
 * [Project].
 *
 * This class effectively acts as the root controller during multi module testing. The general
 * workflow can be defined as such:
 * - Wrap your test in a [FirebaseTestController]
 * - Create your test [projects][Project]
 * - Link your test projects with the controller via [withProjects]
 * - Use the [GradleRunner] to run your build at the [rootDirectory]
 * - Use any of the provided extension methods to inspect the build files and make assertions in
 * your test
 *
 * Some example of the provided extension methods are:
 * - [Project.buildFile]
 * - [Project.pom]
 *
 * To see a more involved example of this workflow, you can take a look at test classes that
 * implement this class, eg; [UpdatePinnedDependenciesTests] and [PublishingPluginTests].
 *
 * @see project
 * @see createReleaseWithConfig
 * @see createReleaseWithProjects
 * @see withProjects
 *
 * @property rootDirectory the root [TemporaryFolder] that all files will be created under.
 */
class FirebaseTestController(val rootDirectory: TemporaryFolder) {

  /**
   * The `build.gradle` file for this project under the [rootDirectory].
   *
   * @see project
   */
  val Project.buildFile: File
    get() = project(this).childFile("build.gradle")

  /**
   * The compiled [Pom] for this [Project], or null if not found.
   *
   * Looks for the [Pom] file under the root `m2repository`, using a REGEX pattern to find matches
   * for this [Project]. The file is then converted into a [Pom] via [Pom.parse].
   *
   * @see pom
   */
  fun Project.pomOrNull(): Pom? {
    val regex = Regex(".*/${group.replace('.', '/')}/$name/$expectedVersion.*/.*\\.pom$")
    val repository = rootDirectory.root.childFile("build/m2repository")
    val pomFile = repository.walk().find { it.isFile && it.path.matches(regex) }

    return pomFile?.let { Pom.parse(it) }
  }

  /**
   * The compiled [Pom] for this [Project].
   *
   * A variant of [pomOrNull] that makes an assertion that the [Pom] should not be null.
   *
   * @see pomOrNull
   * @see shouldNotBeNull
   */
  val Project.pom: Pom
    get() = pomOrNull().shouldNotBeNull()

  /**
   * The sub directory for the given [project] under the parent [rootDirectory].
   *
   * *Alternatively known as the project's directory*
   *
   * @see include
   */
  fun project(project: Project) = rootDirectory.root.childFile(project.name)

  /**
   * Creates a subdirectory for the given [project] under [rootDirectory].
   *
   * Will use [Project.generateBuildFile] to create the relevant [buildFile], and will populate the
   * [AndroidManifest.xml][ANDROID_MANIFEST] accordingly.
   *
   * Usually, you don't want to invoke this method yourself, as it will not include the [project] in
   * the build process. What you're probably looking for is [withProjects].
   *
   * @see project
   * @see withProjects
   */
  fun include(project: Project) {
    rootDirectory.newFolder("${project.name}/src/main")
    rootDirectory.newFile("${project.name}/build.gradle").writeText(project.generateBuildFile())
    rootDirectory
      .newFile("${project.name}/src/main/AndroidManifest.xml")
      .writeText(ANDROID_MANIFEST)
  }

  /**
   * Creates the build files for the root project, and subsequently the provided [projects].
   *
   * All of the provided [projects] will be included in the root `settings.gradle` file, such that
   * they are invoked during the build process.
   *
   * @param projects a variable amount of [Project] to create subdirectories for and include in the
   * build process.
   *
   * @see include
   */
  fun withProjects(vararg projects: Project) {
    rootDirectory.newFile("build.gradle").writeText(ROOT_PROJECT)
    rootDirectory
      .newFile("settings.gradle")
      .writeText(projects.joinToString("\n") { "include '${it.path}'" })

    projects.forEach(this::include)
  }

  /**
   * Creates a `release.json` file at the [rootDirectory].
   *
   * @param release the [ReleaseConfig] to convert into a `json` file.
   *
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
   * @param projects a variable amount of [Project] to include in the release file.
   *
   * @see createReleaseWithConfig
   */
  fun createReleaseWithProjects(vararg projects: Project) {
    createReleaseWithConfig(ReleaseConfig("test", projects.map { it.path }))
  }

  companion object {
    const val ROOT_PROJECT =
      """
        buildscript {
            repositories {
                google()
                jcenter()
                maven {
                    url 'https://storage.googleapis.com/android-ci/mvn/'
                    metadataSources {
                        artifact()
                    }
                }
            }
        }
        plugins {
            id 'PublishingPlugin'
        }

        configure(subprojects) {
            repositories {
                google()
                jcenter()
                maven {
                    url 'https://storage.googleapis.com/android-ci/mvn/'
                    metadataSources {
                          artifact()
                    }
                }
            }
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
