// Copyright 2022 Google LLC
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

import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.gradle.LibraryExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.getByType
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
 * Javadoc doclet called [Doclava](https://code.google.com/archive/p/doclava/)
 * - which allowed us to provide sensible defaults as to how the Javadoc should be rendered. Then,
 * we would do some further transformations to get the Javadoc output in-line with what Devsite
 * expects. This was a lengthy process, and came with a lot of overhead. Furthermore, Doclava does
 * not support kotlindoc and has been unmaintained for many years.
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
 * Our implementation of Dackka falls into three separate files, and four separate tasks.
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
 * Firesite purposed format. This includes removing unnecessary files, fixing links, removing
 * unnecessary headers, and so forth.
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
 * The DackkaPlugin also provides four extra tasks: [cleanDackkaDocumentation]
 * [registerCleanDackkaDocumentation], [separateJavadocAndKotlinDoc]
 * [registerSeparateJavadocAndKotlinDoc] [copyJavaDocToCommonDirectory]
 * [registerCopyJavaDocToCommonDirectoryTask] and [copyKotlinDocToCommonDirectory]
 * [registerCopyKotlinDocToCommonDirectoryTask].
 *
 * _cleanDackkaDocumentation_ is exactly what it sounds like, a task to clean up (delete) the output
 * of Dackka. This is useful when testing Dackka outputs itself- and shouldn't be apart of the
 * normal flow. The reasoning is that it would otherwise invalidate the gradle cache.
 *
 * _separateJavadocAndKotlinDoc_ copies the Javadoc and Kotlindoc directories from Dackka into their
 * own subdirectories- for easy and consistent differentiation.
 *
 * _copyJavaDocToCommonDirectory_ copies the JavaDoc variant of the Dackka output for each sdk, and
 * pastes it in a common directory under the root project's build directory. This makes it easier to
 * zip the doc files for staging.
 *
 * _copyKotlinDocToCommonDirectory_ copies the KotlinDoc variant of the Dackka output for each sdk,
 * and pastes it in a common directory under the root project's build directory. This makes it
 * easier to zip the doc files for staging.
 *
 * Currently, the DackkaPlugin builds Java sources separate from Kotlin Sources. There is an open
 * bug for Dackka in which hidden parent classes and annotations do not hide themselves from
 * children classes. To work around this, we are currently generating stubs for Java sources via
 * metalava, and feeding the stubs to Dackka. This will be removed when the bug is fixed, per
 * b/243954517
 */
abstract class DackkaPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    prepareJavadocConfiguration(project)
    registerCleanDackkaDocumentation(project)
    project.afterEvaluate {
      if (shouldWePublish(project)) {
        val dackkaOutputDirectory = project.provider { fileFromBuildDir("dackkaRawOutput") }
        val separatedFilesDirectory = project.provider { fileFromBuildDir("dackkaSeparatedFiles") }
        val transformedDackkaFilesDirectory =
          project.provider { fileFromBuildDir("dackkaTransformedFiles") }

        val generateDocumentation =
          registerGenerateDackkaDocumentationTask(project, dackkaOutputDirectory)
        val separateJavadocAndKotlinDoc =
          registerSeparateJavadocAndKotlinDoc(
            project,
            dackkaOutputDirectory,
            separatedFilesDirectory
          )
        val firesiteTransform =
          registerFiresiteTransformTask(
            project,
            separatedFilesDirectory,
            transformedDackkaFilesDirectory
          )
        val copyJavaDocToCommonDirectory =
          registerCopyJavaDocToCommonDirectoryTask(project, transformedDackkaFilesDirectory)
        val copyKotlinDocToCommonDirectory =
          registerCopyKotlinDocToCommonDirectoryTask(project, transformedDackkaFilesDirectory)

        project.tasks.register("kotlindoc") {
          group = "documentation"
          dependsOn(
            generateDocumentation,
            separateJavadocAndKotlinDoc,
            firesiteTransform,
            copyJavaDocToCommonDirectory,
            copyKotlinDocToCommonDirectory
          )
        }
      } else {
        project.tasks.register("kotlindoc") { group = "documentation" }
      }
    }
  }

  fun <T> Project.firebaseConfigValue(getter: FirebaseLibraryExtension.() -> T): T =
    project.extensions.getByType<FirebaseLibraryExtension>().getter()

  private fun shouldWePublish(project: Project) = project.firebaseConfigValue { publishJavadoc }

  private fun prepareJavadocConfiguration(project: Project) {
    val javadocConfig = project.javadocConfig
    javadocConfig.dependencies +=
      project.dependencies.create("com.google.code.findbugs:jsr305:3.0.2")
    javadocConfig.dependencies +=
      project.dependencies.create("com.google.errorprone:error_prone_annotations:2.15.0")
    javadocConfig.attributes.attribute(
      BuildTypeAttr.ATTRIBUTE,
      project.objects.named(BuildTypeAttr::class.java, "release")
    )
  }

  // TODO(b/243324828): Refactor when fixed, so we no longer need stubs
  private fun registerGenerateDackkaDocumentationTask(
    project: Project,
    targetDirectory: Provider<File>
  ): Provider<GenerateDocumentationTask> {
    val docStubs = project.tasks.register<GenerateStubsTask>("docStubsForDackkaInput")
    val docsTask = project.tasks.register<GenerateDocumentationTask>("generateDackkaDocumentation")
    with(project.extensions.getByType<LibraryExtension>()) {
      libraryVariants.all {
        if (name == "release") {
          val isKotlin = project.plugins.hasPlugin("kotlin-android")

          val classpath =
            compileConfiguration.getJars() +
              project.javadocConfig.getJars() +
              project.files(bootClasspath)

          val sourceDirectories = sourceSets.flatMap { it.javaDirectories.map { it.absoluteFile } }

          docStubs.configure {
            classPath = classpath
            sources.set(project.provider { sourceDirectories })
          }

          docsTask.configure {
            if (!isKotlin) dependsOn(docStubs)

            val packageLists = fetchPackageLists(project)

            val excludedFiles = projectSpecificSuppressedFiles(project)
            val fixedSourceDirectories =
              if (!isKotlin) listOf(project.docStubs) else sourceDirectories

            sources.set(fixedSourceDirectories + projectSpecificSources(project))
            suppressedFiles.set(excludedFiles)
            packageListFiles.set(packageLists)

            dependencies.set(classpath)
            outputDirectory.set(targetDirectory)

            applyCommonConfigurations()
          }
        }
      }
    }
    return docsTask
  }

  private fun fetchPackageLists(project: Project) =
    project.rootProject
      .fileTree("kotlindoc/package-lists")
      .matching { include("**/package-list") }
      .toList()

  // TODO(b/243534168): Remove when fixed
  private fun projectSpecificSources(project: Project) =
    when (project.name) {
      "firebase-common" -> {
        project.project(":firebase-firestore").files("src/main/java/com/google/firebase").toList()
      }
      else -> emptyList()
    }

  // TODO(b/243534168): Remove when fixed
  private fun projectSpecificSuppressedFiles(project: Project): List<File> =
    when (project.name) {
      "firebase-common" -> {
        project
          .project(":firebase-firestore")
          .files("src/main/java/com/google/firebase/firestore")
          .toList()
      }
      "firebase-firestore" -> {
        project.files("${project.docStubs}/com/google/firebase/Timestamp.java").toList()
      }
      else -> emptyList()
    }

  private fun GenerateDocumentationTask.applyCommonConfigurations() {
    dependsOnAndMustRunAfter("createFullJarRelease")

    val dackkaFile = project.provider { project.dackkaConfig.singleFile }

    dackkaJarFile.set(dackkaFile)
    clientName.set(project.firebaseConfigValue { artifactId })
  }

  // TODO(b/248302613): Remove when dackka exposes configuration for this
  private fun registerSeparateJavadocAndKotlinDoc(
    project: Project,
    dackkaOutputDirectory: Provider<File>,
    outputDirectory: Provider<File>
  ): TaskProvider<Task> {
    val outputJavadocFolder = project.childFile(outputDirectory, "android")
    val outputKotlindocFolder = project.childFile(outputDirectory, "kotlin")

    val separateJavadoc =
      project.tasks.register<Copy>("separateJavadoc") {
        dependsOn("generateDackkaDocumentation")

        val javadocClientFolder = project.childFile(dackkaOutputDirectory, "reference/client")
        val javadocComFolder = project.childFile(dackkaOutputDirectory, "reference/com")

        fromDirectory(javadocClientFolder)
        fromDirectory(javadocComFolder)

        into(outputJavadocFolder)
      }

    val separateKotlindoc =
      project.tasks.register<Copy>("separateKotlindoc") {
        dependsOn("generateDackkaDocumentation")

        val kotlindocFolder = project.childFile(dackkaOutputDirectory, "reference/kotlin")

        from(kotlindocFolder)

        into(outputKotlindocFolder)
      }

    return project.tasks.register("separateJavadocAndKotlinDoc") {
      dependsOn(separateJavadoc, separateKotlindoc)
    }
  }

  private fun registerFiresiteTransformTask(
    project: Project,
    separatedFilesDirectory: Provider<File>,
    targetDirectory: Provider<File>
  ): TaskProvider<Task> {
    val transformJavadoc =
      project.tasks.register<FiresiteTransformTask>("firesiteTransformJavadoc") {
        dependsOnAndMustRunAfter("separateJavadoc")

        removeGoogleGroupId.set(true)
        referencePath.set("/docs/reference/android")
        referenceHeadTagsPath.set("docs/reference/android")
        dackkaFiles.set(project.childFile(separatedFilesDirectory, "android"))
        outputDirectory.set(project.childFile(targetDirectory, "android"))
      }

    val transformKotlindoc =
      project.tasks.register<FiresiteTransformTask>("firesiteTransformKotlindoc") {
        dependsOnAndMustRunAfter("separateKotlindoc")

        referenceHeadTagsPath.set("docs/reference/kotlin")
        referencePath.set("/docs/reference/kotlin")
        dackkaFiles.set(project.childFile(separatedFilesDirectory, "kotlin"))
        outputDirectory.set(project.childFile(targetDirectory, "kotlin"))
      }

    return project.tasks.register("firesiteTransform") {
      dependsOn(transformJavadoc, transformKotlindoc)
    }
  }

  // TODO(b/246593212): Migrate doc files to single directory
  private fun registerCopyJavaDocToCommonDirectoryTask(
    project: Project,
    outputDirectory: Provider<File>
  ) =
    project.tasks.register<Copy>("copyJavaDocToCommonDirectory") {
      mustRunAfter("firesiteTransform")

      val outputFolder = project.rootProject.fileFromBuildDir("firebase-kotlindoc")
      val javaFolder = project.childFile(outputDirectory, "android")

      fromDirectory(javaFolder)

      into(outputFolder)
    }

  // TODO(b/246593212): Migrate doc files to single directory
  private fun registerCopyKotlinDocToCommonDirectoryTask(
    project: Project,
    outputDirectory: Provider<File>
  ) =
    project.tasks.register<Copy>("copyKotlinDocToCommonDirectory") {
      mustRunAfter("firesiteTransform")

      val outputFolder = project.rootProject.fileFromBuildDir("firebase-kotlindoc")
      val kotlinFolder = project.childFile(outputDirectory, "kotlin")

      fromDirectory(kotlinFolder)

      into(outputFolder)
    }

  // Useful for local testing, but may not be desired for standard use (that's why it's not depended
  // on)
  private fun registerCleanDackkaDocumentation(project: Project) =
    project.tasks.register<Delete>("cleanDackkaDocumentation") {
      group = "cleanup"

      val outputDirs = listOf("dackkaRawOutput", "dackkaSeparatedFiles", "dackkaTransformedFiles")

      delete(outputDirs.map { project.fileFromBuildDir(it) })
      delete(project.rootProject.fileFromBuildDir("firebase-kotlindoc"))
    }
}
