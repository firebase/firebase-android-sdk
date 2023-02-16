// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.register

class FireEscapeArtifactPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.afterEvaluate {
      if (supportsMavenPublishing(target)) {
        val apiTxtFile = registerApiTxtFileTask(project)
        val proguardMappingFile = registerProguardMappingFileTask(project)
        val javadoc = registerJavadocTask(project)

        val zippedArtifact =
          project.tasks.register<Zip>("maven") {
            from(apiTxtFile)
            if (project.isAndroid()) {
              from(proguardMappingFile)
            }
          }

        extensions.configure<PublishingExtension> {
          publications.getByName<MavenPublication>("mavenAar") {
            artifact(zippedArtifact) { classifier = "fireescape" }
            artifact(javadoc)
          }
        }
      }
    }
  }
  private fun supportsMavenPublishing(project: Project): Boolean =
    project.plugins.hasPlugin(MavenPublishPlugin::class.java)

  private fun registerProguardMappingFileTask(project: Project): TaskProvider<Task> =
    project.tasks.register("fireEscapeProguardMapping") {
      outputs.file(project.fileFromBuildDir("outputs/mapping/release/mapping.txt"))
    }

  private fun registerApiTxtFileTask(project: Project): TaskProvider<Task> =
    project.tasks.register("fireEscapeApiText") {
      dependsOn(JAVADOC_TASK_NAME)
      outputs.file(project.fileFromBuildDir("tmp/javadoc/api.txt"))
    }

  private fun registerJavadocTask(project: Project): TaskProvider<Jar> =
    project.tasks.register<Jar>("fireescapeJavadocJar") {
      dependsOn(JAVADOC_TASK_NAME)
      project.fileFromBuildDir("/docs/javadoc/reference")
      include("**/*")
      archiveFileName.set("fireescape-javadoc.jar")
      archiveClassifier.set("javadoc")
    }
}
