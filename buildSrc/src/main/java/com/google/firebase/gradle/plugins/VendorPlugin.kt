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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.Directory
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
import org.gradle.process.ExecOperations

class VendorPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.plugins.all {
      when (this) {
        is LibraryPlugin -> configureAndroid(project)
      }
    }
  }

  fun configureAndroid(project: Project) {
    project.apply(plugin = "LicenseResolverPlugin")

    val vendor = project.configurations.create("vendor")
    project.configurations.all {
      when (name) {
        "releaseCompileOnly",
        "debugImplementation",
        "testImplementation",
        "androidTestImplementation" -> extendsFrom(vendor)
      }
    }

    val jarJar = project.configurations.create("firebaseJarJarArtifact")
    project.dependencies.add("firebaseJarJarArtifact", "org.pantsbuild:jarjar:1.7.2")

    val androidComponents = project.extensions.getByType<LibraryAndroidComponentsExtension>()

    androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
      val vendorTask =
        project.tasks.register("${variant.name}VendorTransform", VendorTask::class.java) {
          vendorDependencies.set(vendor)
          packageName.set(variant.namespace)
          this.jarJar.set(jarJar)
        }
      variant.artifacts
        .forScope(ScopedArtifacts.Scope.PROJECT)
        .use(vendorTask)
        .toTransform(
          ScopedArtifact.CLASSES,
          VendorTask::inputJars,
          VendorTask::inputDirs,
          VendorTask::outputJar
        )
    }
  }
}

abstract class VendorTask @Inject constructor(private val execOperations: ExecOperations) :
  DefaultTask() {
  @get:[InputFiles Classpath]
  abstract val vendorDependencies: Property<Configuration>

  @get:[InputFiles Classpath]
  abstract val jarJar: Property<Configuration>

  @get:Input abstract val packageName: Property<String>

  @get:InputFiles abstract val inputJars: ListProperty<RegularFile>

  @get:InputFiles abstract val inputDirs: ListProperty<Directory>

  @get:OutputFile abstract val outputJar: RegularFileProperty

  @TaskAction
  fun taskAction() {
    val workDir = File.createTempFile("vendorTmp", null)
    workDir.mkdirs()
    workDir.deleteRecursively()

    val unzippedDir = File(workDir, "unzipped")
    val externalCodeDir = unzippedDir

    for (directory in inputDirs.get()) {
      directory.asFile.copyRecursively(unzippedDir)
    }
    for (jar in inputJars.get()) {
      unzipJar(jar.asFile, unzippedDir)
    }

    val ownPackageNames = inferPackages(unzippedDir)

    for (jar in vendorDependencies.get()) {
      unzipJar(jar, externalCodeDir)
    }
    val externalPackageNames = inferPackages(externalCodeDir) subtract ownPackageNames
    val java = File(externalCodeDir, "java")
    val javax = File(externalCodeDir, "javax")
    if (java.exists() || javax.exists()) {
      // JarJar unconditionally skips any classes whose package name starts with "java" or "javax".
      throw GradleException(
        "Vendoring java or javax packages is not supported. " +
          "Please exclude one of the direct or transitive dependencies: \n" +
          vendorDependencies
            .get()
            .resolvedConfiguration
            .resolvedArtifacts
            .joinToString(separator = "\n")
      )
    }

    val jar = File(workDir, "intermediate.jar")
    zipAll(unzippedDir, jar)
    transform(jar, ownPackageNames, externalPackageNames)
  }

  fun transform(inputJar: File, ownPackages: Set<String>, packagesToVendor: Set<String>) {
    val parentPackage = packageName.get()
    val rulesFile = File.createTempFile(parentPackage, ".jarjar")
    rulesFile.printWriter().use {
      for (packageName in ownPackages) {
        it.println("keep $packageName.**")
      }
      for (externalPackageName in packagesToVendor) {
        it.println("rule $externalPackageName.** $parentPackage.@0")
      }
    }
    logger.info("The following JarJar configuration will be used:\n ${rulesFile.readText()}")

    execOperations
      .javaexec {
        mainClass.set("org.pantsbuild.jarjar.Main")
        classpath = project.files(jarJar.get())
        args =
          listOf(
            "process",
            rulesFile.absolutePath,
            inputJar.absolutePath,
            outputJar.asFile.get().absolutePath
          )
        systemProperties = mapOf("verbose" to "true", "misplacedClassStrategy" to "FATAL")
      }
      .assertNormalExitValue()
  }
}

fun inferPackages(dir: File): Set<String> {
  return dir
    .walk()
    .filter { it.name.endsWith(".class") }
    .map { it.parentFile.toRelativeString(dir).replace('/', '.') }
    .toSet()
}

fun unzipJar(jar: File, directory: File) {
  ZipFile(jar).use { zip ->
    zip
      .entries()
      .asSequence()
      .filter { !it.isDirectory && !it.name.startsWith("META-INF") }
      .forEach { entry ->
        zip.getInputStream(entry).use { input ->
          val entryFile = File(directory, entry.name)
          entryFile.parentFile.mkdirs()
          entryFile.outputStream().use { output -> input.copyTo(output) }
        }
      }
  }
}

fun zipAll(directory: File, zipFile: File) {

  ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use {
    zipFiles(it, directory, "")
  }
}

private fun zipFiles(zipOut: ZipOutputStream, sourceFile: File, parentDirPath: String) {
  val data = ByteArray(2048)
  sourceFile.listFiles()?.forEach { f ->
    if (f.isDirectory) {
      val path =
        if (parentDirPath == "") {
          f.name
        } else {
          parentDirPath + File.separator + f.name
        }
      // Call recursively to add files within this directory
      zipFiles(zipOut, f, path)
    } else {
      FileInputStream(f).use { fi ->
        BufferedInputStream(fi).use { origin ->
          val path = parentDirPath + File.separator + f.name
          val entry = ZipEntry(path)
          entry.time = f.lastModified()
          entry.isDirectory
          entry.size = f.length()
          zipOut.putNextEntry(entry)
          while (true) {
            val readBytes = origin.read(data)
            if (readBytes == -1) {
              break
            }
            zipOut.write(data, 0, readBytes)
          }
        }
      }
    }
  }
}
