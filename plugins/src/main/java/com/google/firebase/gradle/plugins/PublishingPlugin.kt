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

import com.google.firebase.gradle.bomgenerator.GenerateBomReleaseNotesTask
import com.google.firebase.gradle.bomgenerator.GenerateBomTask
import com.google.firebase.gradle.bomgenerator.GenerateTutorialBundleTask
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.BUILD_BOM_ZIP_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.BUILD_KOTLINDOC_ZIP_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.BUILD_MAVEN_ZIP_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.BUILD_RELEASE_NOTES_ZIP_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.FIREBASE_PUBLISH_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.GENERATE_BOM_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.GENERATE_KOTLINDOC_FOR_RELEASE_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.PUBLISH_RELEASING_LIBS_TO_BUILD_TASK
import com.google.firebase.gradle.plugins.services.gmavenService
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Plugin for providing tasks to release [FirebaseLibrary][FirebaseAndroidLibraryPlugin] projects.
 *
 * Projects to release are computed via [computeReleaseMetadata]. A multitude of tasks are then
 * registered at the root project.
 *
 * The following pertain specifically to a release:
 * - [CHECK_HEAD_DEPS_TASK][registerCheckHeadDependenciesTask]
 * - [VALIDATE_POM_TASK][registerValidatePomForReleaseTask]
 * - [VALIDATE_PROJECTS_TO_PUBLISH_TASK][registerValidateProjectsToPublishTask]
 * - [BUILD_MAVEN_ZIP_TASK] -> Creates a zip file of the contents of
 *   [PUBLISH_RELEASING_LIBS_TO_BUILD_TASK] [registerPublishReleasingLibrariesToBuildDirTask]
 * - [BUILD_KOTLINDOC_ZIP_TASK] -> Creates a zip file of the contents of
 *   [GENERATE_KOTLINDOC_FOR_RELEASE_TASK] [registerGenerateKotlindocForReleaseTask]
 * - [BUILD_RELEASE_NOTES_ZIP_TASK] -> Creates a zip file of the contents of
 *   [PREPARE_RELEASE_NOTES_FOR_DROP][registerPrepareReleaseNotesForDropTask]
 * - [FIREBASE_PUBLISH_TASK] -> Runs all the tasks above
 *
 * The following are additional tasks provided- that are either for convenience sake, or are used
 * outside of the standard [FIREBASE_PUBLISH_TASK] workflow (possibly at a later time in the release
 * cycle):
 * - [BUILD_BOM_ZIP_TASK] -> Creates a zip file of the contents of [GENERATE_BOM_TASK]
 *   [registerGenerateBomTask]
 * - [BUILD_BOM_BUNDLE_ZIP_TASK] -> Creates a zip file of the contents of [BUILD_BOM_ZIP_TASK]
 *   [registerGenerateBomTask],
 *   [GENERATE_BOM_RELEASE_NOTES_TASK][registerGenerateBomReleaseNotesTask] and
 *   [GENERATE_TUTORIAL_BUNDLE_TASK][registerGenerateTutorialBundleTask]
 * - [RELEASE_GENEATOR_TASK][registerGenerateReleaseConfigFilesTask]
 * - [RELEASE_REPORT_GENERATOR_TASK][registerGenerateReleaseReportFilesTask]
 * - [PUBLISH_RELEASING_LIBS_TO_LOCAL_TASK][registerPublishReleasingLibrariesToMavenLocalTask]
 * - [SEMVER_CHECK_TASK][registerSemverCheckForReleaseTask]
 * - [PUBLISH_ALL_TO_BUILD_TASK][registerPublishAllToBuildDir]
 * - [LIBRARY_GROUPS_TASK][registerLibraryGroupsTask]
 *
 * Additionally, this plugin registers the [PostReleasePlugin] via [registerPostReleasePlugin] for
 * each releasing library.
 */
abstract class PublishingPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.gradle.projectsEvaluated {
      val allFirebaseLibraries = project.subprojects.mapNotNull { it.firebaseLibraryOrNull }
      val libraryGroups = computeLibraryGroups(project)
      fixLibraryGroupVersions(libraryGroups)
      val releaseMetadata = computeReleaseMetadata(project, allFirebaseLibraries, libraryGroups)

      val releasingFirebaseLibraries = releaseMetadata?.releasingLibraries.orEmpty()
      val releasingProjects = releasingFirebaseLibraries.map { it.project }

      val generateBom = registerGenerateBomTask(project)
      val generateBomReleaseNotes = registerGenerateBomReleaseNotesTask(project, generateBom)
      val generateTutorialBundle = registerGenerateTutorialBundleTask(project)
      registerValidatePomForReleaseTask(project, releasingProjects)
      val checkHeadDependencies =
        registerCheckHeadDependenciesTask(project, releasingFirebaseLibraries)
      val validateProjectsToPublish =
        registerValidateProjectsToPublishTask(project, releasingFirebaseLibraries)
      val validateLibraryGroupsToPublish =
        registerValidateLibraryGroupsToPublishTask(
          project,
          releasingFirebaseLibraries,
          libraryGroups,
        )
      val publishReleasingLibrariesToBuildDir =
        registerPublishReleasingLibrariesToBuildDirTask(project, releasingProjects)
      val generateKotlindocsForRelease =
        registerGenerateKotlindocForReleaseTask(project, releasingFirebaseLibraries)
      val prepareReleaseNotesForDrop =
        registerPrepareReleaseNotesForDropTask(
          project,
          releasingProjects,
          releaseMetadata?.name.orEmpty(),
        )
      val releaseReportTask = registerGenerateReleaseReportFilesTask(project)

      registerGenerateReleaseConfigFilesTask(project, libraryGroups, releaseReportTask)
      registerPublishReleasingLibrariesToMavenLocalTask(project, releasingProjects)
      registerSemverCheckForReleaseTask(project, releasingProjects)
      registerPublishAllToBuildDir(project, allFirebaseLibraries)
      registerPostReleasePlugin(releasingProjects)
      registerLibraryGroupsTask(project, libraryGroups)

      val buildMavenZip =
        project.tasks.register<Zip>(BUILD_MAVEN_ZIP_TASK) {
          from(publishReleasingLibrariesToBuildDir)
          archiveFileName.set("m2repository.zip")
          destinationDirectory.set(project.layout.buildDirectory)
        }

      val buildKotlindocZip =
        project.tasks.register<Zip>(BUILD_KOTLINDOC_ZIP_TASK) {
          from(generateKotlindocsForRelease)
          archiveFileName.set("kotlindoc.zip")
          destinationDirectory.set(project.layout.buildDirectory)
        }

      val buildReleaseNotesZip =
        project.tasks.register<Zip>(BUILD_RELEASE_NOTES_ZIP_TASK) {
          from(prepareReleaseNotesForDrop.map { it.destinationDir })
          archiveFileName.set("release-notes.zip")
          destinationDirectory.set(project.layout.buildDirectory)
        }

      val buildBomZip =
        project.tasks.register<Zip>(BUILD_BOM_ZIP_TASK) {
          from(generateBom)
          archiveFileName.set("bom.zip")
          destinationDirectory.set(project.layout.buildDirectory)
        }

      project.tasks.register<Zip>(BUILD_BOM_BUNDLE_ZIP_TASK) {
        from(buildBomZip, generateBomReleaseNotes, generateTutorialBundle)
        archiveFileName.set("bomBundle.zip")
        destinationDirectory.set(project.layout.projectDirectory)
      }

      project.tasks.register(FIREBASE_PUBLISH_TASK) {
        dependsOn(
          validateProjectsToPublish,
          validateLibraryGroupsToPublish,
          checkHeadDependencies,
          // validatePomForRelease, TODO(b/279466888) - Make GmavenHelper testable
          buildMavenZip,
          buildKotlindocZip,
          buildReleaseNotesZip,
        )

        doLast {
          logger.lifecycle(
            "Publishing the following libraries:\n{}",
            releasingProjects.map { it.path }.joinToString("\n"),
          )
        }
      }
    }
  }

  private fun registerLibraryGroupsTask(
    project: Project,
    libraryGroups: Map<String, List<FirebaseLibraryExtension>>,
  ) {
    project.tasks.register(LIBRARY_GROUPS_TASK) {
      for (libraryGroup in libraryGroups) {
        println(
          "${libraryGroup.key} -> ${libraryGroup.value.joinToString(" | ") { it.artifactName }}"
        )
      }
    }
  }

  /**
   * Figures out the [ReleaseMetadata] for this release.
   *
   * Metadata can be provided either via the project properties or a [ReleaseConfig] file.
   *
   * The expected project properties can be defined as such:
   * - `projectsToPublish` -> A comma separated list of the publishing project(s) `artifactId`.
   * - `releaseName` -> The name of the release (such as `m123`)
   *
   * When using project properties, this method will take into account the sibling libraries per
   * [libraryGroup][FirebaseLibraryExtension.libraryGroup] -> so there's no need to specify multiple
   * of the same co-releasing libs.
   *
   * The [ReleaseConfig] is a pre-defined set of data for a release. It expects a valid
   * `release.json` [file][ReleaseConfig.fromFile], which provides a list of libraries via their
   * respective project [paths][FirebaseLibraryExtension.path]. Additionally, it does __NOT__ take
   * into account co-releasing libraries-> meaning libraries that should be releasing alongside one
   * another will need to be individually specified in the [ReleaseConfig], otherwise it will likely
   * cause an error during the release process.
   *
   * **Project properties take priority over a [ReleaseConfig].**
   *
   * Example usage of project properties:
   * ```
   * ./gradlew firebasePublish -PprojectsToPublish="firebase-firestore,firebase-common" -PreleaseName="m131"
   * ```
   *
   * See [ReleaseConfig.toFile] for example usage of [ReleaseConfig].
   *
   * If either project property is not provided, and a [ReleaseConfig] is either not found or is
   * empty- this method will return null.
   */
  private fun computeReleaseMetadata(
    project: Project,
    allFirebaseLibraries: List<FirebaseLibraryExtension>,
    libraryGroups: Map<String, List<FirebaseLibraryExtension>>,
  ): ReleaseMetadata? =
    releaseMetadataFromProperties(project, allFirebaseLibraries, libraryGroups)
      ?: releaseMetadataFromReleaseConfig(project, allFirebaseLibraries, libraryGroups)

  private fun releaseMetadataFromProperties(
    project: Project,
    allFirebaseLibraries: List<FirebaseLibraryExtension>,
    libraryGroups: Map<String, List<FirebaseLibraryExtension>>,
  ): ReleaseMetadata? {
    val projectsToPublish = project.provideProperty<String>("projectsToPublish").orNull
    val releaseName = project.provideProperty<String>("releaseName").orNull ?: "NO_NAME"

    if (projectsToPublish == null) return null

    val publishingProjects =
      allFirebaseLibraries.filter { it.artifactId.get() in projectsToPublish.split(",") }

    val librariesToRelease = getDeepTransitiveReleases(publishingProjects, libraryGroups)

    return ReleaseMetadata(librariesToRelease, releaseName)
  }

  /**
   * Finds all transitive libraries that need to be released so that the input list of project names
   * can be released. This includes all project level dependencies, and anything in the same library
   * group.
   */
  private tailrec fun getDeepTransitiveReleases(
    inputProjects: List<FirebaseLibraryExtension>,
    libraryGroups: Map<String, List<FirebaseLibraryExtension>>,
  ): List<FirebaseLibraryExtension> {
    val libraryGroupsToRelease =
      inputProjects
        .flatMap { it.resolveProjectLevelDependencies() + it }
        .map { it.libraryGroup.get() }
    val projectsToRelease =
      libraryGroups
        .filterKeys { it in libraryGroupsToRelease }
        .flatMap { it.value }
        .distinctBy { it.artifactId.get() }
    return if (inputProjects == projectsToRelease) inputProjects
    else getDeepTransitiveReleases(projectsToRelease, libraryGroups)
  }

  private fun releaseMetadataFromReleaseConfig(
    project: Project,
    allFirebaseLibraries: List<FirebaseLibraryExtension>,
    libraryGroups: Map<String, List<FirebaseLibraryExtension>>,
  ): ReleaseMetadata? {
    val releaseConfigFile =
      project.layout.projectDirectory.file(RELEASE_CONFIG_FILE).asFile.takeIf { it.exists() }

    return releaseConfigFile?.let {
      val releaseConfig = ReleaseConfig.fromFile(it)
      val librariesToRelease = allFirebaseLibraries.filter { it.path in releaseConfig.libraries }

      val missingLibrariesToRelease =
        computeMissingLibrariesToRelease(librariesToRelease, libraryGroups)
      if (missingLibrariesToRelease.isNotEmpty()) {
        throw GradleException(
          multiLine(
            "Invalid release configuration.",
            "It should include the following libraries due to library groups:",
            missingLibrariesToRelease,
          )
        )
      }

      ReleaseMetadata(librariesToRelease, releaseConfig.name)
    }
  }

  /**
   * Registers the [GENERATE_BOM_TASK] task.
   *
   * Generates a BOM for a release, although it relies on gmaven to be updated- so it should be
   * invoked manually later on in the release process.
   *
   * @see GenerateBomTask
   */
  private fun registerGenerateBomTask(project: Project) =
    project.tasks.register<GenerateBomTask>("generateBom") {
      bomArtifacts.set(BOM_ARTIFACTS)
      ignoredArtifacts.set(
        listOf(
          "com.google.firebase:crash-plugin",
          "com.google.firebase:firebase-ml-vision",
          "com.google.firebase:firebase-ads",
          "com.google.firebase:firebase-ads-lite",
          "com.google.firebase:firebase-abt",
          "com.google.firebase:firebase-analytics-impl",
          "com.google.firebase:firebase-analytics-impl-license",
          "com.google.firebase:firebase-analytics-license",
          "com.google.firebase:firebase-annotations",
          "com.google.firebase:firebase-appcheck-interop",
          "com.google.firebase:firebase-appcheck-safetynet",
          "com.google.firebase:firebase-appdistribution-gradle",
          "com.google.firebase:firebase-appindexing-license",
          "com.google.firebase:firebase-appindexing",
          "com.google.firebase:firebase-iid",
          "com.google.firebase:firebase-core",
          "com.google.firebase:firebase-auth-common",
          "com.google.firebase:firebase-auth-impl",
          "com.google.firebase:firebase-auth-interop",
          "com.google.firebase:firebase-auth-license",
          "com.google.firebase:firebase-encoders-json",
          "com.google.firebase:firebase-encoders-proto",
          "com.google.firebase:firebase-auth-module",
          "com.google.firebase:firebase-bom",
          "com.google.firebase:firebase-common-license",
          "com.google.firebase:firebase-components",
          "com.google.firebase:firebase-config-license",
          "com.google.firebase:firebase-config-interop",
          "com.google.firebase:firebase-crash",
          "com.google.firebase:firebase-crash-license",
          "com.google.firebase:firebase-crashlytics-buildtools",
          "com.google.firebase:firebase-crashlytics-gradle",
          "com.google.firebase:firebase-database-collection",
          "com.google.firebase:firebase-database-connection",
          "com.google.firebase:firebase-database-connection-license",
          "com.google.firebase:firebase-database-license",
          "com.google.firebase:firebase-datatransport",
          "com.google.firebase:firebase-appdistribution",
          "com.google.firebase:firebase-appdistribution-api",
          "com.google.firebase:firebase-dynamic-module-support",
          "com.google.firebase:firebase-functions-license",
          "com.google.firebase:firebase-iid-interop",
          "com.google.firebase:firebase-iid-license",
          "com.google.firebase:firebase-invites",
          "com.google.firebase:firebase-measurement-connector",
          "com.google.firebase:firebase-measurement-connector-impl",
          "com.google.firebase:firebase-messaging-license",
          "com.google.firebase:firebase-ml-common",
          "com.google.firebase:firebase-ml-vision-internal-vkp",
          "com.google.firebase:firebase-ml-model-interpreter",
          "com.google.firebase:firebase-perf-license",
          "com.google.firebase:firebase-plugins",
          "com.google.firebase:firebase-sessions",
          "com.google.firebase:firebase-storage-common",
          "com.google.firebase:firebase-storage-common-license",
          "com.google.firebase:firebase-storage-license",
          "com.google.firebase:perf-plugin",
          "com.google.firebase:protolite-well-known-types",
          "com.google.firebase:testlab-instr-lib",
          "com.google.firebase:firebase-installations-interop",
          "com.google.firebase:firebase-ml-vision-automl",
          "com.google.firebase:firebase-ml-vision-barcode-model",
          "com.google.firebase:firebase-ml-vision-face-model",
          "com.google.firebase:firebase-ml-vision-image-label-model",
          "com.google.firebase:firebase-ml-vision-object-detection-model",
          "com.google.firebase:firebase-ml-natural-language",
          "com.google.firebase:firebase-ml-natural-language-language-id-model",
          "com.google.firebase:firebase-ml-natural-language-smart-reply",
          "com.google.firebase:firebase-ml-natural-language-smart-reply-model",
          "com.google.firebase:firebase-ml-natural-language-translate",
          "com.google.firebase:firebase-ml-natural-language-translate-model",
          "com.crashlytics.sdk.android:crashlytics:crashlytics",
          "com.google.android.gms:play-services-ads",
          "com.google.gms:google-services",
          "com.android.tools.build:gradle",
          "com.google.firebase:firebase-vertexai",
          "com.google.firebase:firebase-analytics-ktx",
          "com.google.firebase:firebase-appcheck-ktx",
          "com.google.firebase:firebase-appdistribution-api-ktx",
          "com.google.firebase:firebase-appdistribution-ktx",
          "com.google.firebase:firebase-auth-ktx",
          "com.google.firebase:firebase-common-ktx",
          "com.google.firebase:firebase-config-ktx",
          "com.google.firebase:firebase-crashlytics-ktx",
          "com.google.firebase:firebase-database-ktx",
          "com.google.firebase:firebase-dynamic-links",
          "com.google.firebase:firebase-dynamic-links-ktx",
          "com.google.firebase:firebase-dynamic-links-license",
          "com.google.firebase:firebase-firestore-ktx",
          "com.google.firebase:firebase-functions-ktx",
          "com.google.firebase:firebase-inappmessaging-display-ktx",
          "com.google.firebase:firebase-inappmessaging-ktx",
          "com.google.firebase:firebase-installations-ktx",
          "com.google.firebase:firebase-messaging-ktx",
          "com.google.firebase:firebase-ml-modeldownloader-ktx",
          "com.google.firebase:firebase-perf-ktx",
          "com.google.firebase:firebase-storage-ktx",
        )
      )

      outputDirectory.set(project.layout.buildDirectory.dir(BOM_DIR_NAME))
    }

  /**
   * Registers the [GENERATE_TUTORIAL_BUNDLE_TASK] task.
   *
   * Generates a tutorial bundle for a release.
   *
   * @see GenerateTutorialBundleTask
   */
  private fun registerGenerateTutorialBundleTask(project: Project) =
    project.tasks.register<GenerateTutorialBundleTask>(GENERATE_TUTORIAL_BUNDLE_TASK) {
      commonArtifacts.set(listOf("com.google.gms:google-services"))
      gradlePlugins.set(
        listOf(
          "com.google.firebase:firebase-appdistribution-gradle",
          "com.google.firebase:firebase-crashlytics-gradle",
        )
      )
      perfArtifacts.set(listOf("com.google.firebase:perf-plugin"))
      requiredArtifacts.set(
        listOf(
          "com.google.gms:google-services",
          "com.google.firebase:firebase-analytics",
          "com.google.firebase:firebase-crashlytics",
          "com.google.firebase:firebase-perf",
          "com.google.firebase:firebase-ai",
          "com.google.firebase:firebase-messaging",
          "com.google.firebase:firebase-auth",
          "com.google.firebase:firebase-database",
          "com.google.firebase:firebase-storage",
          "com.google.firebase:firebase-config",
          "com.google.android.gms:play-services-ads",
          "com.google.firebase:firebase-firestore",
          "com.google.firebase:firebase-functions",
          "com.google.firebase:firebase-inappmessaging-display",
          "com.google.firebase:firebase-ml-vision",
          "com.google.firebase:firebase-appdistribution-gradle",
          "com.google.firebase:firebase-crashlytics-gradle",
          "com.google.firebase:perf-plugin",
        )
      )
      firebaseArtifacts.set(BOM_ARTIFACTS + EXTRA_TUTORIAL_ARTIFACTS)

      tutorialFile.set(project.layout.buildDirectory.file("recipeVersionUpdate.txt"))
    }

  /**
   * Registers the [GENERATE_BOM_RELEASE_NOTES_TASK] task.
   *
   * Generates the release notes for a bom during a release.
   *
   * @see GenerateBomReleaseNotesTask
   */
  private fun registerGenerateBomReleaseNotesTask(
    project: Project,
    generateBomTask: TaskProvider<GenerateBomTask>,
  ) =
    project.tasks.register<GenerateBomReleaseNotesTask>(GENERATE_BOM_RELEASE_NOTES_TASK) {
      currentBom.set(project.layout.file(generateBomTask.flatMap { it.outputDirectory.nestedFile }))
      previousBom.set(
        project.gmavenService.map { it.latestPom("com.google.firebase", "firebase-bom") }
      )

      releaseNotesFile.set(project.layout.buildDirectory.file("bomReleaseNotes.md"))
    }

  /**
   * Registers the [VALIDATE_POM_TASK] task.
   *
   * A collection of [PomValidator] for each releasing project.
   *
   * Ensures that pom dependencies are not accidentally downgraded.
   */
  private fun registerValidatePomForReleaseTask(
    project: Project,
    releasingProjects: List<Project>,
  ) =
    project.tasks.register(VALIDATE_POM_TASK) {
      for (releasingProject in releasingProjects) {
        val pomValidatorTask = releasingProject.tasks.named("isPomDependencyValid")

        dependsOn(pomValidatorTask)
      }
    }

  /**
   * Registers the [CHECK_HEAD_DEPS_TASK] task.
   *
   * Ensures that project level dependencies are included in the release.
   *
   * @see [CheckHeadDependencies]
   */
  private fun registerCheckHeadDependenciesTask(
    project: Project,
    releasingLibraries: List<FirebaseLibraryExtension>,
  ) =
    project.tasks.register<CheckHeadDependencies>(CHECK_HEAD_DEPS_TASK) {
      projectsToPublish.set(releasingLibraries)
    }

  /**
   * Registers the [VALIDATE_PROJECTS_TO_PUBLISH_TASK] task.
   *
   * Ensures that there are releasing projects provided, to avoid potential false positive
   * edge-cases.
   *
   * @throws GradleException if no releasing projects are found.
   */
  // TODO(b/280320915): Remove doLast when Gradle + IDEA fix task configuration avoidance bug
  private fun registerValidateProjectsToPublishTask(
    project: Project,
    releasinglibraries: List<FirebaseLibraryExtension>,
  ) =
    project.tasks.register(VALIDATE_PROJECTS_TO_PUBLISH_TASK) {
      doLast {
        if (releasinglibraries.isEmpty()) {
          throw GradleException(
            "No projects to release. " +
              "Ensure you've specified the projectsToPublish parameter, " +
              "or have a valid $RELEASE_CONFIG_FILE file at the root directory."
          )
        }
        for (releasingLibrary in releasinglibraries) {
          if (!releasingLibrary.version.contains(releasingLibrary.previewMode.get())) {
            throw GradleException(
              "You are releasing a ${releasingLibrary.previewMode.get()} SDK (${releasingLibrary.artifactId.get()}) as ${releasingLibrary.version}!"
            )
          }
        }
      }
    }

  /**
   * Registers the [VALIDATE_LIBRARY_GROUPS_TO_PUBLISH_TASK] task.
   *
   * Validates that all library groups of all publishing projects are included in the release config
   *
   * @throws GradleException if a library is releasing without it's library group.
   */
  private fun registerValidateLibraryGroupsToPublishTask(
    project: Project,
    librariesToRelease: List<FirebaseLibraryExtension>,
    libraryGroups: Map<String, List<FirebaseLibraryExtension>>,
  ) =
    project.tasks.register(VALIDATE_LIBRARY_GROUPS_TO_PUBLISH_TASK) {
      doLast {
        val missingLibrariesToRelease =
          computeMissingLibrariesToRelease(librariesToRelease, libraryGroups)
        if (missingLibrariesToRelease.isNotEmpty()) {
          throw GradleException(
            multiLine(
              "Invalid release configuration.",
              "It should include the following libraries due to library groups:",
              missingLibrariesToRelease,
            )
          )
        }
      }
    }

  /**
   * Registers the [PUBLISH_RELEASING_LIBS_TO_BUILD_TASK] task.
   *
   * A collection of [publishMavenAarPublicationToBuildDirRepository][PublishToMavenRepository] for
   * each releasing project.
   *
   * The artifacts are provided in a repository under the [buildDir][Project.getBuildDir] of the
   * provided [project].
   *
   * This task outputs the final repository directory.
   */
  private fun registerPublishReleasingLibrariesToBuildDirTask(
    project: Project,
    releasingProjects: List<Project>,
  ) =
    project.tasks.register(PUBLISH_RELEASING_LIBS_TO_BUILD_TASK) {
      for (releasingProject in releasingProjects) {
        val publishTask =
          releasingProject.tasks.named<PublishToMavenRepository>(
            "publishMavenAarPublicationToBuildDirRepository"
          )

        dependsOn(publishTask)
        outputs.file(publishTask.map { it.repository.url })
      }
    }

  /**
   * Registers the [GENERATE_KOTLINDOC_FOR_RELEASE_TASK] task.
   *
   * A collection of [kotlindoc][DackkaPlugin] for each releasing library.
   *
   * Outputs a directory containing all the documentation generated for releasing projects.
   */
  private fun registerGenerateKotlindocForReleaseTask(
    project: Project,
    releasingLibraries: List<FirebaseLibraryExtension>,
  ) =
    project.tasks.register(GENERATE_KOTLINDOC_FOR_RELEASE_TASK) {
      for (releasingLibrary in releasingLibraries) {
        val kotlindocTask = releasingLibrary.project.tasks.named("kotlindoc")

        dependsOn(kotlindocTask)

        outputs.dirs(kotlindocTask).optional()
      }
    }

  /**
   * Registers the [PREPARE_RELEASE_NOTES_FOR_DROP] task.
   *
   * A collection of [makeReleaseNotes][MakeReleaseNotesTask] for each releasing project, that are
   * arranged in a common directly such that they can be properly dropped in G3.
   *
   * The artifacts are provided in a folder under the [buildDir][Project.getBuildDir] of the
   * provided [project].
   */
  private fun registerPrepareReleaseNotesForDropTask(
    project: Project,
    releasingProjects: List<Project>,
    releaseName: String,
  ) =
    project.tasks.register<Copy>(PREPARE_RELEASE_NOTES_FOR_DROP) {
      for (releasingProject in releasingProjects) {
        with(releasingProject) {
          val directory = if (name == "firebase-storage") "$name-api" else name
          val releaseNotesTask = tasks.named("makeReleaseNotes")

          from(releaseNotesTask) { into("android/client/$directory/_releases") }
          rename { "$releaseName.md" }
        }
      }

      destinationDir = project.fileFromBuildDir("release-notes")
    }

  /**
   * Registers the [RELEASE_GENEATOR_TASK] task.
   *
   * Creates a config file that keeps track of what libraries are releasing. To learn more about the
   * file's format see [ReleaseConfig].
   *
   * @see [ReleaseGenerator]
   */
  private fun registerGenerateReleaseConfigFilesTask(
    project: Project,
    libraryGroups: Map<String, List<FirebaseLibraryExtension>>,
    releaseReportTask: TaskProvider<ReleaseReportGenerator>,
  ) =
    project.tasks.register<ReleaseGenerator>(RELEASE_GENEATOR_TASK) {
      currentRelease.convention(project.provideProperty("currentRelease"))
      pastRelease.convention(project.provideProperty("pastRelease"))
      printReleaseConfig.convention(project.provideProperty("printOutput"))
      commitsToIgnoreFile.convention(project.layout.projectDirectory.file("ignoreCommits.txt"))

      releaseConfigFile.convention(project.layout.projectDirectory.file(RELEASE_CONFIG_FILE))
      releaseReportJsonFile.convention(
        project.layout.projectDirectory.file(RELEASE_REPORT_JSON_FILE)
      )
      this.libraryGroups = libraryGroups
      finalizedBy(releaseReportTask)
    }

  /**
   * Registers the [RELEASE_REPORT_GENERATOR_TASK] task.
   *
   * Creates a markdown file that keeps track of what libraries are releasing. To learn more about
   * the file's format see [ReleaseConfig].
   *
   * @see [ReleaseReportGenerator]
   */
  private fun registerGenerateReleaseReportFilesTask(project: Project) =
    project.tasks.register<ReleaseReportGenerator>(RELEASE_REPORT_GENERATOR_TASK) {
      releaseReportMdFile.convention(project.layout.projectDirectory.file(RELEASE_REPORT_MD_FILE))
      releaseReportJsonFile.convention(
        project.layout.projectDirectory.file(RELEASE_REPORT_JSON_FILE)
      )
    }

  /**
   * Registers the [PUBLISH_RELEASING_LIBS_TO_LOCAL_TASK] task.
   *
   * A collection of [publishMavenAarPublicationToMavenLocal][PublishToMavenRepository] for each
   * releasing project.
   *
   * Allows users to do local testing of releasing libraries, pulling the artifacts via the maven
   * local repository.
   */
  private fun registerPublishReleasingLibrariesToMavenLocalTask(
    project: Project,
    releasingProjects: List<Project>,
  ) =
    project.tasks.register(PUBLISH_RELEASING_LIBS_TO_LOCAL_TASK) {
      for (releasingProject in releasingProjects) {
        val publishTask = releasingProject.tasks.named("publishMavenAarPublicationToMavenLocal")

        dependsOn(publishTask)
      }
    }

  /**
   * Registers the [SEMVER_CHECK_TASK] task.
   *
   * A collection of [ApiDiffer] for each releasing project.
   *
   * Ensures that the version of a releasing project aligns with the API. That is, if the API shows
   * signs of a major or minor bump- the version is bumped as expected.
   */
  private fun registerSemverCheckForReleaseTask(
    project: Project,
    releasingProjects: List<Project>,
  ) =
    project.tasks.register(SEMVER_CHECK_TASK) {
      for (releasingProject in releasingProjects) {
        val semverCheckTask = releasingProject.tasks.named("semverCheck")
        dependsOn(semverCheckTask)
      }
    }

  /**
   * Registers the [PUBLISH_ALL_TO_BUILD_TASK] task.
   *
   * A collection of [publishMavenAarPublicationToBuildDirRepository][PublishToMavenRepository] for
   * __ALL__ firebase library projects.
   *
   * The artifacts are provided in a repository under the [buildDir][Project.getBuildDir] of the
   * provided [project].
   */
  private fun registerPublishAllToBuildDir(
    project: Project,
    allFirebaseLibraries: List<FirebaseLibraryExtension>,
  ) =
    project.tasks.register(PUBLISH_ALL_TO_BUILD_TASK) {
      for (firebaseLibrary in allFirebaseLibraries) {
        val publishTask =
          firebaseLibrary.project.tasks.named("publishMavenAarPublicationToBuildDirRepository")

        dependsOn(publishTask)
      }
    }

  /** Registers the [PostReleasePlugin] to each releaing project. */
  private fun registerPostReleasePlugin(releasingProjects: List<Project>) {
    for (releasingProject in releasingProjects) {
      releasingProject.apply<PostReleasePlugin>()
    }
  }

  companion object {
    const val RELEASE_CONFIG_FILE = "release.json"
    const val RELEASE_REPORT_MD_FILE = "release_report.md"
    const val RELEASE_REPORT_JSON_FILE = "release_report.json"
    const val BOM_DIR_NAME = "bom"

    const val GENERATE_BOM_TASK = "generateBom"
    const val GENERATE_BOM_RELEASE_NOTES_TASK = "generateBomReleaseNotes"
    const val GENERATE_TUTORIAL_BUNDLE_TASK = "generateTutorialBundle"
    const val VALIDATE_PROJECTS_TO_PUBLISH_TASK = "validateProjectsToPublish"
    const val VALIDATE_LIBRARY_GROUPS_TO_PUBLISH_TASK = "validateLibraryGroupsToPublish"
    const val SEMVER_CHECK_TASK = "semverCheckForRelease"
    const val RELEASE_GENEATOR_TASK = "generateReleaseConfig"
    const val RELEASE_REPORT_GENERATOR_TASK = "generateReleaseReport"
    const val VALIDATE_POM_TASK = "validatePomForRelease"
    const val LIBRARY_GROUPS_TASK = "libraryGroups"
    const val PUBLISH_RELEASING_LIBS_TO_BUILD_TASK = "publishReleasingLibrariesToBuildDir"
    const val PUBLISH_RELEASING_LIBS_TO_LOCAL_TASK = "publishReleasingLibrariesToMavenLocal"
    const val GENERATE_KOTLINDOC_FOR_RELEASE_TASK = "generateKotlindocForRelease"
    const val PREPARE_RELEASE_NOTES_FOR_DROP = "prepareReleaseNotesForDrop"
    const val CHECK_HEAD_DEPS_TASK = "checkHeadDependencies"
    const val BUILD_MAVEN_ZIP_TASK = "buildMavenZip"
    const val BUILD_KOTLINDOC_ZIP_TASK = "buildKotlindocZip"
    const val BUILD_RELEASE_NOTES_ZIP_TASK = "buildReleaseNotesZip"
    const val BUILD_BOM_ZIP_TASK = "buildBomZip"
    const val BUILD_BOM_BUNDLE_ZIP_TASK = "buildBomBundleZip"
    const val FIREBASE_PUBLISH_TASK = "firebasePublish"
    const val PUBLISH_ALL_TO_BUILD_TASK = "publishAllToBuildDir"

    const val BUILD_DIR_REPOSITORY_DIR = "m2repository"

    /** Artifacts included in the bom. */
    val BOM_ARTIFACTS =
      listOf(
        "com.google.firebase:firebase-analytics",
        "com.google.firebase:firebase-appcheck-debug",
        "com.google.firebase:firebase-appcheck-debug-testing",
        "com.google.firebase:firebase-appcheck-playintegrity",
        "com.google.firebase:firebase-appcheck",
        "com.google.firebase:firebase-auth",
        "com.google.firebase:firebase-common",
        "com.google.firebase:firebase-config",
        "com.google.firebase:firebase-crashlytics",
        "com.google.firebase:firebase-crashlytics-ndk",
        "com.google.firebase:firebase-database",
        "com.google.firebase:firebase-dataconnect",
        "com.google.firebase:firebase-encoders",
        "com.google.firebase:firebase-firestore",
        "com.google.firebase:firebase-functions",
        "com.google.firebase:firebase-inappmessaging",
        "com.google.firebase:firebase-inappmessaging-display",
        "com.google.firebase:firebase-installations",
        "com.google.firebase:firebase-messaging",
        "com.google.firebase:firebase-messaging-directboot",
        "com.google.firebase:firebase-ml-modeldownloader",
        "com.google.firebase:firebase-perf",
        "com.google.firebase:firebase-storage",
        "com.google.firebase:firebase-ai",
      )

    /** Artifacts that we use in the tutorial bundle, but _not_ in the bom. */
    val EXTRA_TUTORIAL_ARTIFACTS =
      listOf(
        "com.google.android.gms:play-services-ads",
        "com.google.firebase:firebase-ml-vision",
        "androidx.credentials:credentials",
        "androidx.credentials:credentials-play-services-auth",
        "com.google.android.libraries.identity.googleid:googleid",
      )
  }
}

/**
 * Data needed to understand a Release.
 *
 * @property releasingLibraries A list of libraries that should be released
 * @property name The name of the release (such as `m123`)
 * @see PublishingPlugin.computeReleaseMetadata
 */
data class ReleaseMetadata(
  val releasingLibraries: List<FirebaseLibraryExtension>,
  val name: String,
)
