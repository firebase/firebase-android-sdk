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

import com.android.build.api.variant.DslExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Locale
import kotlin.io.path.relativeTo
import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import javax.inject.Inject

plugins {
  id("com.android.library")
  id("kotlin-android")
  alias(libs.plugins.kotlinx.serialization)
  id("com.google.firebase.dataconnect.gradle.plugin")
}

android {
  val compileSdkVersion : Int by rootProject
  val targetSdkVersion : Int by rootProject
  val minSdkVersion : Int by rootProject

  namespace = "com.google.firebase.dataconnect.connectors"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }

  @Suppress("UnstableApiUsage")
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  packaging {
    resources {
      excludes.add("META-INF/LICENSE.md")
      excludes.add("META-INF/LICENSE-notice.md")
    }
  }
}

dependencies {
  api(project(":firebase-dataconnect"))
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.serialization.core)

  testImplementation(project(":firebase-dataconnect:testutil"))
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.mockk)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)

  androidTestImplementation(project(":firebase-dataconnect:androidTestutil"))
  androidTestImplementation(project(":firebase-dataconnect:testutil"))
  //TODO: change to androidTestImplementation(libs.kotlin.reflect) when it added to the catalog
  androidTestImplementation(kotlin("reflect"))
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotlin.coroutines.test)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.truth.liteproto.extension)
  androidTestImplementation(libs.turbine)
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions {
    freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
  }
}

// Enable Kotlin "Explicit API Mode". This causes the Kotlin compiler to fail if any
// classes, methods, or properties have implicit `public` visibility. This check helps
// avoid  accidentally leaking elements into the public API, requiring that any public
// element be explicitly declared as `public`.
// https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
// https://chao2zhang.medium.com/explicit-api-mode-for-kotlin-on-android-b8264fdd76d1
tasks.withType<KotlinCompile>().all {
  if (!name.contains("test", ignoreCase = true)) {
    if (!kotlinOptions.freeCompilerArgs.contains("-Xexplicit-api=strict")) {
      kotlinOptions.freeCompilerArgs += "-Xexplicit-api=strict"
    }
  }
}

interface DataConnectProjectDslExtension {
  var connectors: List<String>
}

abstract class DataConnectVariantDslExtension @Inject constructor(
    extensionConfig: VariantExtensionConfig<*>
): VariantExtension, java.io.Serializable {
  abstract val connectors: ListProperty<String>

  init {
    // Commenting since the call to extensionConfig.projectExtension() fails with
    // "No global extension DSL element implements ExtensionAware"
    //val projectExtension = extensionConfig.projectExtension(DataConnectProjectDslExtension::class.java)
    //connectors.set(projectExtension.connectors)
    connectors.convention(emptyList())
  }
}

androidComponents.registerExtension(DslExtension.Builder("dataconnect").extendProjectWith(DataConnectProjectDslExtension::class.java).build()) { config ->
  project.objects.newInstance(DataConnectVariantDslExtension::class.java, config)
}

androidComponents.onVariants { variant ->
  val variantNameTitleCase = variant.name.replaceFirstChar { it.titlecase(Locale.US) }

  val inputDirectoriesProvider = variant.sources.assets!!.all.map { directoryCollections ->
    directoryCollections.map { directories ->
      directories.map { directory ->
        directory.dir("../dataconnect")
      }
    }
  }

  val generateCodeTask = project.tasks.register<DataConnectCodegenTask>("generate${variantNameTitleCase}DataConnectSources") {
    inputDirectories.set(inputDirectoriesProvider)
    mergedInputsDirectory.set(project.layout.buildDirectory.dir("intermediates/mergedDataConnectSources/${variant.name}"))
    dataConnectCli.set(File("/google/src/cloud/dconeybe/codegen/google3/blaze-bin/third_party/firebase/dataconnect/emulator/cli/cli"))
  }

  variant.sources.java!!.addGeneratedSourceDirectory(generateCodeTask, DataConnectCodegenTask::outputDirectory)
}

abstract class DataConnectCodegenTask : DefaultTask() {
  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:OutputDirectory
  abstract val mergedInputsDirectory: DirectoryProperty

  @get:InputFiles
  abstract val inputDirectories: ListProperty<Collection<Directory>>

  @get:InputFile
  abstract val dataConnectCli: RegularFileProperty

  @TaskAction
  fun generateCode() {
    val outputDirectory = outputDirectory.asFile.get()
    val mergedInputsDirectory = mergedInputsDirectory.asFile.get()
    val inputDirectories = inputDirectories.get().map { it.map { it.asFile } }

    deleteDirectory(outputDirectory)
    deleteDirectory(mergedInputsDirectory)
    mergeInputDirectories(inputDirectories, mergedInputsDirectory)
    runCodgen(mergedInputsDirectory, outputDirectory)
  }

  private fun deleteDirectory(dir: File) {
    if (dir.exists()) {
      logger.info("Deleting directory: {}", dir)
      val deleteSucceeded = dir.deleteRecursively()
      if (! deleteSucceeded) {
        throw DeleteDirectoryFailedException(dir)
      }
    }
  }

  private fun mergeInputDirectories(inputDirectories: List<List<File>>, outputDirectory: File) {
    data class SrcFileInfo(val file: File, val relativePath: String)
    val srcFiles = mutableListOf<SrcFileInfo>()

    for (curInputDirectories in inputDirectories.reversed()) {
      val destFileBySrcFiles = mutableMapOf<String, MutableSet<String>>()
      for (curInputDirectory in curInputDirectories) {
        logger.info("Enumerating input files in directory: {}", curInputDirectory)
        val dirWalker = curInputDirectory.walkTopDown().onFail { file, exception ->
          throw ReadInputDirectoryFailedException(file, exception)
        }
        for (srcFile in dirWalker) {
          if (!srcFile.isFile) {
            continue
          }
          logger.debug("Found input file: {}", srcFile)
          val relativePath = srcFile.toPath().relativeTo(curInputDirectory.toPath()).toString()
          destFileBySrcFiles.getOrPut(relativePath) { mutableSetOf()}.add(srcFile.path)
          srcFiles.add(SrcFileInfo(file=srcFile, relativePath = relativePath))
        }
      }

      val srcFileConflicts = destFileBySrcFiles.filter { it.value.size > 1 }.map { SrcFileConflict(destPath=it.key, srcPaths = it.value.toList()) }
      if (srcFileConflicts.isNotEmpty()) {
        throw SourceFileConflictsException(srcFileConflicts)
      }
    }

    for (srcFile in srcFiles) {
      val destFile = outputDirectory.toPath().resolve(srcFile.relativePath).toFile()
      logger.debug("Copying {} to {}", srcFile.file, destFile)
      srcFile.file.copyTo(destFile, overwrite=true)
    }
  }

  private fun codegenArgs() = sequence<String> {
      yield(dataConnectCli.get().asFile.path)
      if (logger.isInfoEnabled) {
        yield("-logtostderr")
      }
      if (logger.isDebugEnabled) {
        yield("-v")
        yield("2")
      }
      yield("gradle")
      yield("generate")
      yield("-config_dir=$intermediatesDirectory")
      yield("-output_dir=${outputDirectory.path}")
  }

  private fun runCodgen(intermediatesDirectory: File, outputDirectory: File) {
    val codegenArgs = buildList {
      add(dataConnectCli.get().asFile.path)
      if (logger.isInfoEnabled) {
        add("-logtostderr")
      }
      if (logger.isDebugEnabled) {
        add("-v")
        add("2")
      }
      add("gradle")
      add("generate")
      add("-config_dir=$intermediatesDirectory")
      add("-output_dir=${outputDirectory.path}")
    }
    val codegenArgsStr = codegenArgs.joinToString(" ")
    logger.info("Running command: {}", codegenArgsStr)
    val process = ProcessBuilder().apply {
      command(codegenArgs)
      directory(intermediatesDirectory)
      inheritIO()
      redirectErrorStream(true)
    }.start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
      throw CodegenFailedException("Data Connect code generation failed:"
          + " command completed with non-zero exit code $exitCode: $codegenArgsStr"
      )
    }
  }

  private class CodegenFailedException(message: String) : GradleException(message)

  private class DeleteDirectoryFailedException(dir: File) : GradleException("deleting directory failed: $dir")

  private data class SrcFileConflict(val destPath: String, val srcPaths: List<String>)

  private class SourceFileConflictsException(conflicts: List<SrcFileConflict>) : GradleException(toMessage(conflicts)) {
    private companion object {
      fun toMessage(conflict: SrcFileConflict): String {
        return "${conflict.srcPaths.sorted().joinToString(" and ")} " +
            "map to the same output file: ${conflict.destPath}"
      }

      fun toMessage(conflicts: List<SrcFileConflict>): String {
        if (conflicts.size == 1) {
          return "Input file conflict: ${toMessage(conflicts.single())}"
        }

        val sb = StringBuilder("${conflicts.size} input file conflicts:")
        conflicts.sortedBy { it.destPath }.forEachIndexed { index, conflict ->
          sb.append(" [${index+1}] ${toMessage(conflict)}")
        }
        return sb.toString()
      }
    }
  }

  private class ReadInputDirectoryFailedException(file: File, cause: Throwable) :
    GradleException("reading input file/directory failed: $file", cause)
}