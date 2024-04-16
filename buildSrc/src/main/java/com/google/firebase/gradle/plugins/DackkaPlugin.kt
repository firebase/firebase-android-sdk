/*
 * Copyright 2022 Google LLC
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

import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.gradle.LibraryExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * # Dackka Plugin
 *
 * The Dackka Plugin is a wrapper around internal tooling at Google called Dackka, which generates
 * documentation for [Firesite](https://firebase.google.com/docs/reference)
 *
 * ## Dackka
 *
 * Dackka is an internal-purposed Dokka plugin. Google hosts its documentation on an internal
 * service called **Devsite**. Firebase hosts their documentation on a variant of Devsite called
 * **Firesite**. You can click [here](https://firebase.google.com/docs/reference) to see how that
 * looks. Essentially, it's just Google's way of decorating (and organizing) documentation.
 *
 * Devsite expects its files to be in a very specific format. Previously, we would use an internal
 * Javadoc doclet called [Doclava](https://code.google.com/archive/p/doclava/), which allowed us to
 * provide sensible defaults as to how the Javadoc should be rendered. Then, we would do some
 * further transformations to get the Javadoc output in-line with what Devsite expects. This was a
 * lengthy process, and came with a lot of overhead. Furthermore, Doclava does not support kotlindoc
 * and has been unmaintained for many years.
 *
 * Dackka is an internal solution to that. Dackka provides a devsite plugin for Dokka that will
 * handle the job of doclava. Not only does this mean we can cut out a huge portion of our
 * transformation systems- but the overhead for maintaining such systems is deferred away to the
 * AndroidX team (the maintainers of Dackka).
 *
 * ## Dackka Usage
 *
 * The Dackka we use is a fat jar pulled periodically from Dackka nightly builds, and moved to our
 * own maven repo bucket. Since it's recommended from the AndroidX team to run Dackka on the command
 * line, the fat jar allows us to ignore all the miscenalionous dependencies of Dackka (in regards
 * to Dokka especially).
 *
 * The general process of using Dackka is that you collect the dependencies and source sets of the
 * gradle project, create a
 * [Dokka appropriate JSON file](https://kotlin.github.io/dokka/1.7.10/user_guide/cli/usage/#example-using-json)
 * , run the Dackka fat jar with the JSON file as an argument, and publish the output folder.
 *
 * ## Implementation
 *
 * Our implementation of Dackka falls into three separate files, and two separate tasks.
 *
 * ### [GenerateDocumentationTask]
 *
 * This task is the meat of our Dackka implementation. It's what actually handles the running of
 * Dackka itself. The task exposes a gradle extension called [GenerateDocumentationTaskExtension]
 * with various configuration points for Dackka. This will likely be expanded upon in the future, as
 * configurations are needed.
 *
 * The job of this task is to **just** run Dackka. What happens after-the-fact does not matter to
 * this task. It will take the provided inputs, organize them into the expected JSON file, and run
 * Dackka with the JSON file as an argument.
 *
 * ### [FiresiteTransformTask]
 *
 * Dackka was designed with Devsite in mind. The problem though, is that we use Firesite. Firesite
 * is very similar to Devsite, but there *are* minor differences.
 *
 * The job of this task is to transform the Dackka output from a Devsite purposed format, to a
 * Firesite purposed format. This includes removing unnecessary files and headers, fixing links, and
 * so forth.
 *
 * There are open bugs for each transformation, as in an ideal world- they are instead exposed as
 * configurations from Dackka. Should these configurations be adopted by Dackka, this task could
 * become unnecessary itself- as we could just configure the task during generation.
 *
 * ### DackkaPlugin
 *
 * This plugin is the mind of our Dackka implementation. It manages registering, and configuring all
 * the tasks for Dackka (that is, the already established tasks above). While we do not currently
 * offer any configuration for the Dackka plugin, this could change in the future as needed.
 * Currently, the DackkaPlugin provides sensible defaults to output directories, package lists, and
 * so forth.
 *
 * The DackkaPlugin also provides two extra tasks: [cleanDackkaDocumentation]
 * [registerCleanDackkaDocumentation] and [copyDocsToCommonDirectory]
 * [registerCopyDocsToCommonDirectoryTask].
 *
 * _cleanDackkaDocumentation_ is exactly what it sounds like, a task to clean up (delete) the output
 * of Dackka. This is useful when testing Dackka outputs itself- and shouldn't be apart of the
 * normal flow. The reasoning is that it would otherwise invalidate the gradle cache.
 *
 * _copyDocsToCommonDirectory_ copies the transformed Dackka output, and pastes it in a common
 * directory under the root project's build directory. This makes it easier to zip the doc files for
 * staging.
 */
abstract class DackkaPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    prepareJavadocConfiguration(project)
    registerCleanDackkaDocumentation(project)
    val kotlinDoc = registerKotlinDocTask(project)

    // TODO(b/270576405): remove afterEvalutate after fixed
    project.afterEvaluate {
      if (weShouldPublish(this)) {
        val dackkaOutputDirectory = provider { fileFromBuildDir("dackkaRawOutput") }
        val transformedFilesDirectory = provider { fileFromBuildDir("dackkaTransformedFiles") }

        val generateDocumentation =
          registerGenerateDackkaDocumentationTask(project, dackkaOutputDirectory)
        val firesiteTransform =
          registerFiresiteTransformTask(project, dackkaOutputDirectory, transformedFilesDirectory)
        val copyDocsToCommonDirectory =
          registerCopyDocsToCommonDirectoryTask(project, transformedFilesDirectory)

        kotlinDoc.configure {
          dependsOn(generateDocumentation, firesiteTransform, copyDocsToCommonDirectory)

          outputs.dir(copyDocsToCommonDirectory.map { it.destinationDir })
        }
      }
    }
  }

  private fun registerKotlinDocTask(project: Project) =
    project.tasks.register("kotlindoc") { group = "documentation" }

  /**
   * Checks if the [Project] has opted in to releasing their documentation.
   *
   * This is done via the [FirebaseLibraryExtension.publishJavadoc] property.
   */
  private fun weShouldPublish(project: Project) = project.firebaseLibrary.publishJavadoc

  /**
   * Applies common configuration to the [javadocConfig], that is otherwise not present.
   *
   * @see javadocConfig
   */
  private fun prepareJavadocConfiguration(project: Project) {
    project.javadocConfig.apply {
      dependencies += project.dependencies.create("com.google.code.findbugs:jsr305:3.0.2")
      dependencies +=
        project.dependencies.create("com.google.errorprone:error_prone_annotations:2.15.0")
      attributes.attribute(BuildTypeAttr.ATTRIBUTE, project.objects.named("release"))
    }
  }

  private fun registerGenerateDackkaDocumentationTask(
    project: Project,
    targetDirectory: Provider<File>,
  ): TaskProvider<GenerateDocumentationTask> =
    project.tasks.register<GenerateDocumentationTask>("generateDackkaDocumentation") {
      with(project.extensions.getByType<LibraryExtension>()) {
        libraryVariants.all {
          if (name == "release") {
            val classpath =
              compileConfiguration.jars + project.javadocConfig.jars + project.files(bootClasspath)

            val sourceDirectories =
              sourceSets.flatMap { it.javaDirectories } +
                sourceSets.flatMap { it.kotlinDirectories }

            sources.set(sourceDirectories)
            dependencies.set(classpath)
            outputDirectory.set(targetDirectory)
            suppressedFiles.set(emptyList())
            packageListFiles.set(fetchPackageLists(project))

            applyCommonConfigurations()
          }
        }
      }
    }

  private fun fetchPackageLists(project: Project) =
    project.rootProject
      .fileTree("kotlindoc/package-lists")
      .matching { include("**/package-list") }
      .toList()

  private fun GenerateDocumentationTask.applyCommonConfigurations() {
    dependsOnAndMustRunAfter("createFullJarRelease")

    val dackkaFile = project.provider { project.dackkaConfig.singleFile }

    dackkaJarFile.set(dackkaFile)
    clientName.set(project.firebaseLibrary.artifactId)
  }

  private fun registerFiresiteTransformTask(
    project: Project,
    dackkaOutputDirectory: Provider<File>,
    targetDirectory: Provider<File>
  ) =
    project.tasks.register<FiresiteTransformTask>("firesiteTransform") {
      dependsOnAndMustRunAfter("generateDackkaDocumentation")

      dackkaFiles.set(dackkaOutputDirectory.childFile("docs/reference"))
      outputDirectory.set(targetDirectory)
    }

  private fun registerCopyDocsToCommonDirectoryTask(
    project: Project,
    transformedFilesDirectory: Provider<File>,
  ) =
    project.tasks.register<Copy>("copyDocsToCommonDirectory") {
      mustRunAfter("firesiteTransform")

      from(transformedFilesDirectory)
      into(project.rootProject.fileFromBuildDir("firebase-kotlindoc"))
    }

  /**
   * Useful for local testing, but may not be desired for standard use.
   *
   * As such, this task has to be ran explicitly- it won't be invoked otherwise.
   */
  private fun registerCleanDackkaDocumentation(project: Project) =
    project.tasks.register<Delete>("cleanDackkaDocumentation") {
      group = "cleanup"

      val outputDirs = listOf("dackkaRawOutput", "dackkaTransformedFiles")

      delete(outputDirs.map { project.fileFromBuildDir(it) })
      delete(project.rootProject.fileFromBuildDir("firebase-kotlindoc"))
    }
}
