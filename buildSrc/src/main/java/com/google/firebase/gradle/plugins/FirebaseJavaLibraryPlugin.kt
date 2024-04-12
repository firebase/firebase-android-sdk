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

import com.google.common.collect.ImmutableList
import com.google.firebase.gradle.plugins.LibraryType.JAVA
import com.google.firebase.gradle.plugins.semver.ApiDiffer
import com.google.firebase.gradle.plugins.semver.GmavenCopier
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getPlugin
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class FirebaseJavaLibraryPlugin : BaseFirebaseLibraryPlugin() {

  override fun apply(project: Project) {
    project.apply<JavaLibraryPlugin>()
    project.apply<DackkaPlugin>()

    setupFirebaseLibraryExtension(project)
    registerMakeReleaseNotesTask(project)

    // reduce the likelihood of kotlin module files colliding.
    project.tasks.withType<KotlinCompile> {
      kotlinOptions.freeCompilerArgs = ImmutableList.of("-module-name", kotlinModuleName(project))
    }
  }

  private fun setupFirebaseLibraryExtension(project: Project) {
    val firebaseLibrary =
      project.extensions.create<FirebaseLibraryExtension>("firebaseLibrary", project, JAVA)

    setupStaticAnalysis(project, firebaseLibrary)
    setupApiInformationAnalysis(project)
    getIsPomValidTask(project, firebaseLibrary)
    setupVersionCheckTasks(project, firebaseLibrary)
    configurePublishing(project, firebaseLibrary)
  }

  private fun setupVersionCheckTasks(project: Project, firebaseLibrary: FirebaseLibraryExtension) {
    project.tasks.register<GmavenVersionChecker>("gmavenVersionCheck") {
      groupId.value(firebaseLibrary.groupId.get())
      artifactId.value(firebaseLibrary.artifactId.get())
      version.value(firebaseLibrary.version)
      latestReleasedVersion.value(firebaseLibrary.latestReleasedVersion.orElseGet { "" })
    }
    project.mkdir("semver")
    project.tasks.register<GmavenCopier>("copyPreviousArtifacts") {
      dependsOn("jar")
      project.file("semver/previous.jar").delete()
      groupId.value(firebaseLibrary.groupId.get())
      artifactId.value(firebaseLibrary.artifactId.get())
      aarAndroidFile.value(false)
      filePath.value(project.file("semver/previous.jar").absolutePath)
    }
    val currentJarFile =
      project
        .file("build/libs/${firebaseLibrary.artifactId.get()}-${firebaseLibrary.version}.jar")
        .absolutePath
    val previousJarFile = project.file("semver/previous.jar").absolutePath
    project.tasks.register<ApiDiffer>("semverCheck") {
      currentJar.value(currentJarFile)
      previousJar.value(previousJarFile)
      version.value(firebaseLibrary.version)
      previousVersionString.value(
        GmavenHelper(firebaseLibrary.groupId.get(), firebaseLibrary.artifactId.get())
          .getLatestReleasedVersion()
      )

      dependsOn("copyPreviousArtifacts")
    }
  }

  private fun setupApiInformationAnalysis(project: Project) {
    val srcDirs =
      project.convention.getPlugin<JavaPluginConvention>().sourceSets.getByName("main").java.srcDirs

    val apiInfo = getApiInfo(project, srcDirs)

    val generateApiTxt = getGenerateApiTxt(project, srcDirs)
    val docStubs = getDocStubs(project, srcDirs)

    project.tasks.getByName("check").dependsOn(docStubs)
    project.afterEvaluate {
      val classpath =
        configurations
          .getByName("runtimeClasspath")
          .incoming
          .artifactView {
            attributes { attribute(Attribute.of("artifactType", String::class.java), "jar") }
          }
          .artifacts
          .artifactFiles
      apiInfo.configure { classPath = classpath }
      generateApiTxt.configure { classPath = classpath }
      docStubs.configure { classPath = classpath }
    }
  }
}
