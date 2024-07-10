/*
 * Copyright 2024 Google LLC
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
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabExtension
import java.io.File
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.maven.MavenPom
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke

// TODO(b/XXXX): Identify proper property usage
// TODO(b/XXXX): Ensure correct downstream property usage
// TODO(b/XXXX): Move functions and extra variables to proper location
abstract class FirebaseLibraryExtension {
  abstract val project: Property<Project>
  abstract val type: Property<LibraryType>

  abstract val publishJavadoc: Property<Boolean>
  abstract val publishReleaseNotes: Property<Boolean>
  abstract val publishSources: Property<Boolean>

  abstract val previewMode: Property<String>

  abstract val groupId: Property<String>
  abstract val artifactId: Property<String>

  abstract val libraryGroup: Property<String>

  fun commonConfiguration(target: Project, libraryType: LibraryType) {
    project.convention(target)
    type.convention(libraryType)
    publishJavadoc.convention(true)
    publishReleaseNotes.convention(true)
    publishSources.convention(true)
    previewMode.convention("")
    target.extensions.create<FirebaseTestLabExtension>("testLab", target.objects)

    if (target.name === "ktx" && target.parent !== null) {
      artifactId.convention("${target.parent?.name}-ktx")
      groupId.convention(target.parent?.group?.toString())
    } else {
      artifactId.convention(target.name)
      groupId.convention(target.group.toString())
    }

    val lintProjects =
      target.provideProperty<String>("firebase.checks.lintProjects").map { it.orEmpty().split(",") }

    target.extensions.create<FirebaseStaticAnalysis>("staticAnalysis", lintProjects.get().toSet())
    libraryGroup.convention(artifactId)
  }

  fun testLab(action: Action<FirebaseTestLabExtension>) {
    project.get().extensions.configure<FirebaseTestLabExtension> { action.execute(this) }
  }

  fun customizePom(pom: MavenPom) {
    pom.licenses {
      license {
        name.set("The Apache Software License, Version 2.0")
        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }
    pom.scm {
      connection.set("scm:git:https://github.com/firebase/firebase-android-sdk.git")
      url.set("https://github.com/firebase/firebase-android-sdk")
    }
  }

  fun libraryGroup(name: String) {
    libraryGroup.set(name)
  }

  val version: Provider<String>
    get() = project.map { it.version.toString() }

  val latestReleasedVersion: Provider<String?>
    get() = project.flatMap { it.provideProperty<String>("latestReleasedVersion") }

  val mavenName: Provider<String>
    get() = groupId.flatMap { groupId -> artifactId.map { artifactId -> "$groupId:$artifactId" } }

  val path: Provider<String>
    get() = project.map { it.path }

  val srcDirs: Provider<Set<File>>
    get() =
      project.flatMap { project ->
        type.map {
          when (it) {
            LibraryType.ANDROID ->
              project.extensions
                .getByType<LibraryExtension>()
                .sourceSets
                .getByName("main")
                .java
                .srcDirs
            LibraryType.JAVA ->
              project.extensions
                .getByType<JavaPluginExtension>()
                .sourceSets
                .getByName("main")
                .java
                .srcDirs
            null -> throw IllegalStateException("Project type was unset.")
          }
        }
      }

  val runtimeClasspath: Provider<String>
    get() =
      type.map { if (it === LibraryType.ANDROID) "releaseRuntimeClasspath" else "runtimeClasspath" }

  val staticAnalysis: FirebaseStaticAnalysis
    get() = project.get().extensions.getByType()

  val testLab: FirebaseTestLabExtension
    get() = project.get().extensions.getByType()

  override fun toString(): String {
    return """FirebaseLibraryExtension{name="${mavenName.get()}", project="${project.get()}", type="${type.get()}" """
  }
}
