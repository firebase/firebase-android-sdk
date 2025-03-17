/*
 * Copyright 2020 Google LLC
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

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.LibraryPlugin
import com.google.firebase.gradle.plugins.license.LicenseResolverPlugin
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.utils.extendsFrom

/**
 * Gradle plugin for vendoring dependencies in an android library.
 *
 * We vendor dependencies by moving the dependency into the published package, and renaming all
 * imports to reference the vendored package.
 *
 * Registers the `vendor` configuration to be used for specifying vendored dependencies.
 *
 * Note that you should exclude any `java` or `javax` transitive dependencies, as `jarjar` (what we
 * use to do the actual vendoring) unconditionally skips them.
 *
 * ```
 * vendor("com.google.dagger:dagger:2.27") {
 *   exclude(group = "javax.inject", module = "javax.inject")
 * }
 * ```
 *
 * @see VendorTask
 */
class VendorPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.apply<LicenseResolverPlugin>()
    project.plugins.withType<LibraryPlugin>().configureEach { configureAndroid(project) }
  }

  private fun configureAndroid(project: Project) {
    val vendor = project.configurations.register("vendor")
    val configurations =
      listOf(
        "releaseCompileOnly",
        "debugImplementation",
        "testImplementation",
        "androidTestImplementation",
      )

    for (configuration in configurations) {
      project.configurations.named(configuration).extendsFrom(vendor)
    }

    val jarJarArtifact =
      project.configurations.register("firebaseJarJarArtifact") {
        dependencies.add(project.dependencies.create("org.pantsbuild:jarjar:1.7.2"))
      }

    val androidComponents = project.extensions.getByType<LibraryAndroidComponentsExtension>()

    androidComponents.onReleaseVariants {
      val vendorTask =
        project.tasks.register<VendorTask>("${it.name}VendorTransform") {
          vendorDependencies.set(vendor)
          packageName.set(it.namespace)
          jarJar.set(jarJarArtifact)
        }

      it.artifacts
        .forScope(ScopedArtifacts.Scope.PROJECT)
        .use(vendorTask)
        .toTransform(
          ScopedArtifact.CLASSES,
          VendorTask::inputJars,
          VendorTask::inputDirs,
          VendorTask::outputJar,
        )
    }
  }
}

/**
 * Executes the actual vendoring of a library.
 *
 * @see VendorPlugin
 */
abstract class VendorTask
@Inject
constructor(
  private val exec: ExecOperations,
  private val archive: ArchiveOperations,
  private val fs: FileSystemOperations,
  private val layout: ProjectLayout,
) : DefaultTask() {
  /** Dependencies that should be vendored. */
  @get:[InputFiles Classpath]
  abstract val vendorDependencies: Property<Configuration>

  /** Configuration pointing to the `.jar` file for JarJar. */
  @get:[InputFiles Classpath]
  abstract val jarJar: Property<Configuration>

  /**
   * The name of the package (or namespace) that we're vendoring for.
   *
   * We use this to rename the [vendorDependencies].
   */
  @get:Input abstract val packageName: Property<String>

  /** The jars generated for this package during a release. */
  @get:InputFiles abstract val inputJars: ListProperty<RegularFile>

  /** The directories generated for this package during a release. */
  @get:InputFiles abstract val inputDirs: ListProperty<Directory>

  /** The jar file to save the vendored artifact. */
  @get:OutputFile abstract val outputJar: RegularFileProperty

  @TaskAction
  fun taskAction() {
    val unzippedDir = temporaryDir.childFile("unzipped")

    logger.info("Unpacking input directories")
    fs.sync {
      from(inputDirs)
      into(unzippedDir)
    }

    logger.info("Unpacking input jars")
    fs.copy {
      for (jar in inputJars.get()) {
        from(archive.zipTree(jar))
      }
      into(unzippedDir)
      exclude { it.path.contains("META-INF") }
    }

    val ownPackageNames = inferPackageNames(unzippedDir)

    logger.info("Unpacking vendored files")
    fs.copy {
      for (jar in vendorDependencies.get()) {
        from(archive.zipTree(jar))
      }
      into(unzippedDir)
      exclude { it.path.contains("META-INF") }
    }

    val externalPackageNames = inferPackageNames(unzippedDir) subtract ownPackageNames
    val java = unzippedDir.childFile("java")
    val javax = unzippedDir.childFile("javax")
    if (java.exists() || javax.exists()) {
      // JarJar unconditionally skips any classes whose package name starts with "java" or "javax".
      val dependencies = vendorDependencies.get().resolvedConfiguration.resolvedArtifacts
      throw GradleException(
        """
          |Vendoring java or javax packages is not supported.
          |Please exclude one of the direct or transitive dependencies:
          |${dependencies.joinToString("\n")}
        """
          .trimMargin()
      )
    }

    val inputJar = temporaryDir.childFile("intermediate.jar")
    unzippedDir.zipFilesTo(this, inputJar)

    transform(inputJar, ownPackageNames, externalPackageNames)
  }

  private fun transform(inputJar: File, ownPackages: Set<String>, packagesToVendor: Set<String>) {
    val parentPackage = packageName.get()
    val rulesFile = temporaryDir.childFile("$parentPackage.jarjar")

    rulesFile.printWriter().use {
      for (packageName in ownPackages) {
        it.println("keep $packageName.**")
      }
      for (externalPackageName in packagesToVendor) {
        it.println("rule $externalPackageName.** $parentPackage.@0")
      }
    }

    logger.info("The following JarJar configuration will be used:\n ${rulesFile.readText()}")

    exec
      .javaexec {
        mainClass.set("org.pantsbuild.jarjar.Main")
        classpath = layout.files(jarJar)
        args =
          listOf(
            "process",
            rulesFile.absolutePath,
            inputJar.absolutePath,
            outputJar.asFile.get().absolutePath,
          )
        systemProperties = mapOf("verbose" to "true", "misplacedClassStrategy" to "FATAL")
      }
      .assertNormalExitValue()
  }

  /** Given a directory of class files, constructs a list of all the class files. */
  private fun inferPackageNames(dir: File): Set<String> {
    return dir
      .walk()
      .filter { it.name.endsWith(".class") }
      .map { it.parentFile.toRelativeString(dir).replace(File.separator, ".") }
      .toSet()
  }
}
