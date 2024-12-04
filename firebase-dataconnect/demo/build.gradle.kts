/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import java.nio.charset.StandardCharsets

plugins {
  // Use whichever versions of these dependencies suit your application.
  // The versions shown here were the latest versions as of December 03, 2024.
  // Note, however, that the version of kotlin("plugin.serialization") _must_,
  // in general, match the version of kotlin("android").
  id("com.android.application") version "8.7.3"
  id("com.google.gms.google-services") version "4.4.2"
  val kotlinVersion = "2.1.0"
  kotlin("android") version kotlinVersion
  kotlin("plugin.serialization") version kotlinVersion

  // The following code in this "plugins" block can be omitted from customer
  // facing documentation as it is an implementation detail of this application.
  id("com.diffplug.spotless") version "7.0.0.BETA4"
}

dependencies {
  // Use whichever versions of these dependencies suit your application.
  // The versions shown here were the latest versions as of December 03, 2024.
  implementation("com.google.firebase:firebase-dataconnect:16.0.0-beta03")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.activity:activity-ktx:1.9.3")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
  implementation("com.google.android.material:material:1.12.0")

  // The following code in this "dependencies" block can be omitted from customer
  // facing documentation as it is an implementation detail of this application.
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
  implementation("io.kotest:kotest-property:5.9.1")
  implementation("io.kotest.extensions:kotest-property-arbs:2.1.2")
}

// The remaining code in this file can be omitted from customer facing
// documentation. It's here just to make things compile and/or configure
// optional components of the build (e.g. spotless code formatting).

android {
  namespace = "com.google.firebase.dataconnect.minimaldemo"
  compileSdk = 35
  defaultConfig {
    minSdk = 21
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures.viewBinding = true
  kotlinOptions.jvmTarget = "1.8"
}

spotless {
  val ktfmtVersion = "0.53"
  kotlin {
    target("**/*.kt")
    targetExclude("build/")
    ktfmt(ktfmtVersion).googleStyle()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("build/")
    ktfmt(ktfmtVersion).googleStyle()
  }
  json {
    target("**/*.json")
    targetExclude("build/")
    simple().indentWithSpaces(2)
  }
  yaml {
    target("**/*.yaml")
    targetExclude("build/")
    jackson()
      .yamlFeature("INDENT_ARRAYS", true)
      .yamlFeature("MINIMIZE_QUOTES", true)
      .yamlFeature("WRITE_DOC_START_MARKER", false)
  }
  format("xml") {
    target("**/*.xml")
    targetExclude("build/")
    trimTrailingWhitespace()
    indentWithSpaces(2)
    endWithNewline()
  }
}

abstract class DataConnectGenerateSourcesTask : DefaultTask() {

  @get:InputDirectory abstract val inputDirectory: DirectoryProperty

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @get:Internal abstract val nodeExecutableDirectory: DirectoryProperty

  @get:Internal abstract val firebaseCommand: Property<String>

  @get:Internal abstract val workDirectory: DirectoryProperty

  @get:Inject protected abstract val execOperations: ExecOperations

  @get:Inject protected abstract val providerFactory: ProviderFactory

  @get:Inject protected abstract val fileSystemOperations: FileSystemOperations

  @TaskAction
  fun run(inputChanges: InputChanges) {
    if (inputChanges.isIncremental) {
      val onlyLogFilesChanged =
        inputChanges.getFileChanges(inputDirectory).all { it.file.name.endsWith(".log") }
      if (onlyLogFilesChanged) {
        didWork = false
        return
      }
    }

    val inputDirectory: File = inputDirectory.get().asFile
    val outputDirectory: File = outputDirectory.get().asFile
    val nodeExecutableDirectory: File? = nodeExecutableDirectory.orNull?.asFile
    val firebaseCommand: String? = firebaseCommand.orNull
    val workDirectory: File = workDirectory.get().asFile

    outputDirectory.deleteRecursively()
    outputDirectory.mkdirs()
    workDirectory.deleteRecursively()
    workDirectory.mkdirs()

    val newPath: String? =
      if (nodeExecutableDirectory === null) {
        null
      } else {
        val nodeExecutableDirectoryAbsolutePath = nodeExecutableDirectory.absolutePath
        val oldPath = providerFactory.environmentVariable("PATH").orNull
        if (oldPath === null) {
          nodeExecutableDirectoryAbsolutePath
        } else {
          nodeExecutableDirectoryAbsolutePath + File.pathSeparator + oldPath
        }
      }

    val logFile =
      if (logger.isInfoEnabled) {
        null
      } else {
        File(workDirectory, "dataconnect.sdk.generate.log.txt")
      }

    val execResult =
      logFile?.outputStream().use { logStream ->
        execOperations.runCatching {
          exec {
            commandLine(firebaseCommand ?: "firebase", "--debug", "dataconnect:sdk:generate")
            workingDir(inputDirectory)
            isIgnoreExitValue = false
            if (newPath !== null) {
              environment("PATH", newPath)
            }
            if (logStream !== null) {
              standardOutput = logStream
              errorOutput = logStream
            }
          }
        }
      }

    execResult.onFailure { exception ->
      logFile?.readText(StandardCharsets.UTF_8)?.lines()?.forEach { line ->
        logger.warn("{}", line.trimEnd())
      }
      throw exception
    }
  }
}

abstract class CopyDirectoryTask : DefaultTask() {

  @get:InputDirectory abstract val srcDirectory: DirectoryProperty

  @get:OutputDirectory abstract val destDirectory: DirectoryProperty

  @get:Inject protected abstract val fileSystemOperations: FileSystemOperations

  @TaskAction
  fun run() {
    val srcDirectory: File = srcDirectory.get().asFile
    val destDirectory: File = destDirectory.get().asFile

    logger.info("srcDirectory: {}", srcDirectory.absolutePath)
    logger.info("destDirectory: {}", destDirectory.absolutePath)

    destDirectory.deleteRecursively()
    destDirectory.mkdirs()

    fileSystemOperations.copy {
      from(srcDirectory)
      into(destDirectory)
    }
  }
}

run {
  val dataConnectTaskGroupName = "Firebase Data Connect Minimal App"
  val projectDirectory = layout.projectDirectory

  val generateSourcesTask =
    tasks.register<DataConnectGenerateSourcesTask>("dataConnectGenerateSources") {
      group = dataConnectTaskGroupName
      description =
        "Run firebase dataconnect:sdk:generate to generate the Data Connect Kotlin SDK sources"

      inputDirectory = projectDirectory.dir("firebase")
      outputDirectory = projectDirectory.dir("dataConnectGeneratedSources")

      nodeExecutableDirectory =
        project.providers.gradleProperty("dataConnect.minimalApp.nodeExecutableDirectory").map {
          projectDirectory.dir(it)
        }
      firebaseCommand = project.providers.gradleProperty("dataConnect.minimalApp.firebaseCommand")

      workDirectory = layout.buildDirectory.dir(name)
    }

  val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()
  androidComponents.onVariants { variant ->
    val variantNameTitleCase = variant.name[0].uppercase() + variant.name.substring(1)
    val copyTaskName = "dataConnectCopy${variantNameTitleCase}GeneratedSources"
    val copyTask =
      tasks.register<CopyDirectoryTask>(copyTaskName) {
        group = dataConnectTaskGroupName
        description =
          "Copy the generated Data Connect Kotlin SDK sources into the " +
            "generated code directory for the \"${variant.name}\" variant."
        srcDirectory = generateSourcesTask.flatMap { it.outputDirectory }
      }

    variant.sources.java!!.addGeneratedSourceDirectory(copyTask, CopyDirectoryTask::destDirectory)
  }
}
