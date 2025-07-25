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
import com.google.firebase.gradle.plugins.ci.Coverage
import com.google.firebase.gradle.plugins.services.GMavenService
import java.io.File
import java.nio.file.Paths
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.w3c.dom.Element

/**
 * Plugin for Firebase Libraries.
 *
 * Implements shared functionality between Java and Android libraries.
 *
 * @see [FirebaseAndroidLibraryPlugin]
 * @see [FirebaseJavaLibraryPlugin]
 * @see [FirebaseLibraryExtension]
 */
abstract class BaseFirebaseLibraryPlugin : Plugin<Project> {
  protected fun setupDefaults(project: Project, library: FirebaseLibraryExtension) {
    with(library) {
      project.gradle.sharedServices.registerIfAbsent<GMavenService, _>("gmaven")
      previewMode.convention("")
      publishJavadoc.convention(true)
      artifactId.convention(project.name)
      groupId.convention(project.provider { project.group.toString() })
      libraryGroup.convention(artifactId)

      customizePom {
        licenses {
          license {
            name = "The Apache Software License, Version 2.0"
            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
          }
        }
        scm {
          connection = "scm:git:https://github.com/firebase/firebase-android-sdk.git"
          url = "https://github.com/firebase/firebase-android-sdk"
        }
      }

      releaseNotes {
        enabled.convention(true)
        artifactId.convention(project.name)
        artifactName.convention(project.name)
      }

      val parent = project.parent
      // TODO(): remove when we get rid of ktx modules
      if (project.name == "ktx" && parent !== null) {
        artifactId.convention(parent.provider { "${parent.name}-ktx" })
        groupId.convention(parent.provider { "${parent.group}" })
      }

      androidLintCheckProjects.convention(
        project
          .provideProperty<String>("firebase.checks.lintProjects")
          .map { it.split(",") }
          .orElse(emptyList())
      )
    }
  }

  protected fun registerMakeReleaseNotesTask(project: Project) =
    project.tasks.register<MakeReleaseNotesTask>("makeReleaseNotes") {
      val changelog = project.file("CHANGELOG.md")
      val releaseNotes by tempFile("release_notes.md")

      onlyIf("Changelog file not found.") { changelog.exists() }

      changelogFile.set(changelog)
      releaseNotesFile.set(releaseNotes)
      skipMissingEntries.set(project.provideProperty("skipEmptyChangelog"))
    }

  protected fun kotlinModuleName(project: Project): String {
    val fullyQualifiedProjectPath = project.path.replace(":".toRegex(), "-")
    return project.rootProject.name + fullyQualifiedProjectPath
  }

  protected fun setupStaticAnalysis(project: Project, library: FirebaseLibraryExtension) {
    project.afterEvaluate {
      configurations.all {
        if ("lintChecks" == name) {
          for (checkProject in library.androidLintCheckProjects.get()) {
            project.dependencies.add("lintChecks", project.project(checkProject))
          }
        }
      }
    }
    project.tasks.register("firebaseLint") { dependsOn("lint") }
    Coverage.apply(library)
  }

  protected fun setupMetalavaSemver(project: Project, library: FirebaseLibraryExtension) {
    project.tasks.register<CopyApiTask>("copyApiTxtFile") {
      apiTxtFile.set(project.file("api.txt"))
      output.set(project.file("existing_api.txt"))
    }

    project.tasks.register<SemVerTask>("metalavaSemver") {
      apiTxtFile.set(project.file("api.txt"))
      existingApiFile.set(project.file("existing_api.txt"))
      currentVersionString.value(library.version)
      previousVersionString.value(library.previousVersion)
    }
  }

  protected fun getApiInfo(
    project: Project,
    srcDirs: ConfigurableFileCollection,
  ): TaskProvider<ApiInformationTask> {
    val outputFile =
      project.rootProject.file(
        Paths.get(
          project.rootProject.buildDir.path,
          "apiinfo",
          project.path.substring(1).replace(":", "_"),
        )
      )
    val outputApiFile = File(outputFile.absolutePath + "_api.txt")
    val apiTxt =
      project.file("api.txt").takeIf { it.exists() } ?: project.rootProject.file("empty-api.txt")
    val apiInfo =
      project.tasks.register<ApiInformationTask>("apiInformation") {
        sources.from(project.provider { srcDirs })
        apiTxtFile.set(apiTxt)
        baselineFile.set(project.file("baseline.txt"))
        this.outputFile.set(outputFile)
        this.outputApiFile.set(outputApiFile)
        updateBaseline.set(project.hasProperty("updateBaseline"))
      }
    return apiInfo
  }

  protected fun getIsPomValidTask(project: Project, firebaseLibrary: FirebaseLibraryExtension) {
    project.tasks.register<PomValidator>("isPomDependencyValid") {
      pomFile.set(project.layout.buildDirectory.file("publications/mavenAar/pom-default.xml"))
      groupId.set(firebaseLibrary.groupId.get())
      artifactId.set(firebaseLibrary.artifactId.get())
      dependsOn("generatePomFileForMavenAarPublication")
    }
  }

  protected fun getGenerateApiTxt(project: Project, srcDirs: ConfigurableFileCollection) =
    project.tasks.register<GenerateApiTxtTask>("generateApiTxtFile") {
      sources.from(project.provider { srcDirs })
      apiTxtFile.set(project.file("api.txt"))
      baselineFile.set(project.file("baseline.txt"))
      updateBaseline.set(project.hasProperty("updateBaseline"))
    }

  protected fun getDocStubs(project: Project, srcDirs: ConfigurableFileCollection) =
    project.tasks.register<GenerateStubsTask>("docStubs") {
      sources.from(project.provider { srcDirs })
    }

  /**
   * Adds + configures the [MavenPublishPlugin] for the given [project].
   *
   * This provides the repository we publish to (a folder in the root build directory), and
   * configures maven pom generation.
   *
   * @see [applyPomTransformations]
   * @see [FirebaseLibraryExtension.customizePomAction]
   */
  protected fun configurePublishing(project: Project, firebaseLibrary: FirebaseLibraryExtension) {
    with(project) {
      apply<MavenPublishPlugin>()

      // TODO(b/371625225): sources jar is missing from java libs
      extensions.configure<PublishingExtension> {
        repositories.maven {
          url = rootProject.fileFromBuildDir("m2repository").toURI()
          name = "BuildDir"
        }
        publications.create<MavenPublication>("mavenAar") {
          // TODO(https://github.com/gradle/gradle/issues/18619): Remove afterEvaluate when
          // properties are supported
          afterEvaluate {
            artifactId = firebaseLibrary.artifactId.get()
            groupId = firebaseLibrary.groupId.get()

            pom(firebaseLibrary.customizePomAction)
            firebaseLibrary.applyPomTransformations(pom)
            from(components.findByName(firebaseLibrary.type.componentName))
          }
        }
      }
    }
  }

  /**
   * Performs various transformations needed to ensure the given [pom] is ready for a release.
   *
   * The transformations are done lazily via the [withXml][MavenPom.withXml] provider.
   *
   * @param pom the [MavenPom] to prepare
   * @see [addTypeWithAARSupport]
   */
  private fun FirebaseLibraryExtension.applyPomTransformations(pom: MavenPom) {
    pom.withXml {
      val dependencies = asElement().findElementsByTag("dependency")
      val androidDependencies = resolveAndroidDependencies()
      for (dependency in dependencies) {
        addTypeWithAARSupport(dependency, androidDependencies)
      }
    }
  }

  /**
   * Adds + configures the `type` element as a direct descendant of the provided [Element].
   *
   * The `type` element specifies what the given [dependency] is published as. This could be another
   * `pom`, a `jar`, an `aar`, etc., Usually, the [MavenPublishPlugin] can infer these types; this
   * is not the case however with `aar` artifacts.
   *
   * This method will check if the provided [dependency] is in the provided list of artifact strings
   * ([androidLibraries]), and map it to an `aar` or `jar` as needed.
   *
   * The following is an example of a `type` element:
   * ```
   * <dependency>
   *   <type>aar</type>
   * </dependency>
   * ```
   *
   * @param dependency the element to append the `type` to
   * @param androidLibraries a list of dependencies for this given SDK that publish `aar` artifacts
   * @see applyPomTransformations
   */
  // TODO(b/277607560): Remove when Gradle's MavenPublishPlugin adds functionality for aar types
  private fun addTypeWithAARSupport(dependency: Element, androidLibraries: List<String>) {
    dependency.findOrCreate("type").apply {
      textContent = if (androidLibraries.contains(dependency.toMavenName())) "aar" else "jar"
    }
  }
}

/**
 * A list of _all_ dependencies that publish `aar` artifacts.
 *
 * This is collected via the [runtimeClasspath][FirebaseLibraryExtension.runtimeClasspath], and
 * includes project level dependencies as well as external dependencies.
 *
 * The dependencies are mapped to their [mavenName][toMavenName].
 *
 * @see resolveProjectLevelDependencies
 * @see resolveExternalAndroidLibraries
 */
// TODO(b/277607560): Remove when Gradle's MavenPublishPlugin adds functionality for aar types
fun FirebaseLibraryExtension.resolveAndroidDependencies() =
  resolveExternalAndroidLibraries() +
    resolveProjectLevelDependencies()
      .filter { it.type == LibraryType.ANDROID }
      .map { it.mavenName.get() }

/**
 * A list of project level dependencies.
 *
 * This is collected via the [runtimeClasspath][FirebaseLibraryExtension.runtimeClasspath].
 *
 * @throws RuntimeException if a project level dependency is found that doesn't have
 *   [FirebaseLibraryExtension]
 */
// TODO(b/277607560): Remove when Gradle's MavenPublishPlugin adds functionality for aar types
fun FirebaseLibraryExtension.resolveProjectLevelDependencies() =
  project.configurations
    .getByName(runtimeClasspath)
    .allDependencies
    .mapNotNull { it as? ProjectDependency }
    .map {
      project.project(it.dependencyProject.path).extensions.findByType<FirebaseLibraryExtension>()
        ?: throw RuntimeException(
          "Project level dependencies must have the firebaseLibrary plugin. The following dependency does not: ${it.artifactName}"
        )
    }

/**
 * A list of _external_ dependencies that publish `aar` artifacts.
 *
 * This is collected via the [runtimeClasspath][FirebaseLibraryExtension.getRuntimeClasspath], using
 * an [ArtifactView][org.gradle.api.artifacts.ArtifactView] that filters for `aar` artifactType.
 *
 * Artifacts are mapped to their respective maven name:
 * ```
 * groupId:artifactId
 * ```
 */
// TODO(b/277607560): Remove when Gradle's MavenPublishPlugin adds functionality for aar types
fun FirebaseLibraryExtension.resolveExternalAndroidLibraries() =
  project.configurations
    .getByName(runtimeClasspath)
    .incoming
    .artifactView { attributes { attribute("artifactType", "aar") } }
    .artifacts
    .map { it.variant.displayName.substringBefore(" ").substringBeforeLast(":") }

/**
 * The name provided to this artifact when published.
 *
 * Syntax sugar for:
 * ```
 * "$mavenName:$version"
 * ```
 *
 * For example, the following could be an artifact name:
 * ```
 * "com.google.firebase:firebase-common:16.0.5"
 * ```
 *
 * @see [mavenName]
 */
val FirebaseLibraryExtension.artifactName: String
  get() = "${mavenName.get()}:$version"

/**
 * Fetches the latest version for this SDK from GMaven.
 *
 * Uses [GmavenHelper] to make the request.
 *
 * To get the latest released version per the local `gradle.properties`, use [latestReleasedVersion]
 * .
 *
 * @see [ModuleVersion]
 */
val FirebaseLibraryExtension.latestGMavenVersion: ModuleVersion
  get() {
    val latestVersion = GmavenHelper(groupId.get(), artifactId.get()).getLatestReleasedVersion()

    return ModuleVersion.fromStringOrNull(latestVersion)
      ?: throw RuntimeException(
        "Invalid format for ModuleVersion for module '$artifactName':\n $latestVersion"
      )
  }

/**
 * The latest version released, per the `gradle.properties` file.
 *
 * Checks for the property `latestReleasedVersion`, which should be in the `gradle.properties` files
 * of released SDKs (alongside the current `version`).
 *
 * To fetch the latest released version per GMaven, use [latestGMavenVersion].
 *
 * ```properties
 * version=19.2.1
 * latestReleasedVersion=19.2.0
 * ```
 */
val FirebaseLibraryExtension.latestReleasedVersion: Provider<String>
  get() = project.provideProperty<String>("latestReleasedVersion")

/**
 * Fetches the namespace for this SDK from the [LibraryExtension].
 *
 * eg;
 *
 * ```
 * com.googletest.firebase.appdistribution
 * ```
 *
 * @throws RuntimeException when the project doesn't have a defined namespace
 */
val FirebaseLibraryExtension.namespace: String
  get() =
    project.extensions.getByType<LibraryExtension>().namespace
      ?: throw RuntimeException("Project doesn't have a defined namespace: ${project.path}")

/**
 * The name of this library on maven, without the version.
 *
 * For example, the following could be a maven name:
 * ```
 * "com.google.firebase:firebase-common"
 * ```
 *
 * @see [artifactName]
 */
val FirebaseLibraryExtension.mavenName: Provider<String>
  get() = groupId.zip(artifactId) { groupId, artifactId -> "$groupId:$artifactId" }
