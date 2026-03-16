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

package com.google.firebase.crashlytics.buildtools.gradle.tasks

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.Variant
import com.google.firebase.crashlytics.buildtools.CrashlyticsOptions.SYMBOL_GENERATOR_BREAKPAD
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsBuildtools
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin.Companion.CRASHLYTICS_TASK_GROUP
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin.Companion.buildDir
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsVariantExtension
import com.google.firebase.crashlytics.buildtools.gradle.SymbolGeneratorType
import com.google.firebase.crashlytics.buildtools.gradle.extensions.capitalized
import com.google.firebase.crashlytics.buildtools.ndk.NativeSymbolGenerator
import com.google.firebase.crashlytics.buildtools.ndk.internal.breakpad.BreakpadSymbolGenerator
import com.google.firebase.crashlytics.buildtools.ndk.internal.csym.NdkCSymGenerator
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

/** Generate symbol files for Crashlytics projects. */
@Suppress("UnstableApiUsage") // SingleArtifact.MERGED_NATIVE_LIBS
@CacheableTask
abstract class GenerateSymbolFileTask : DefaultTask() {
  @get:[InputFiles PathSensitive(PathSensitivity.RELATIVE) SkipWhenEmpty]
  abstract val unstrippedNativeLibsDirs: ConfigurableFileCollection
  @get:[InputFile PathSensitive(PathSensitivity.NONE) Optional]
  abstract val breakpadBinary: RegularFileProperty
  @get:Internal abstract val symbolGeneratorType: Property<SymbolGeneratorType>
  @get:Internal abstract val breakpadExtractionDir: DirectoryProperty
  @get:OutputDirectory abstract val symbolFileOutputDir: DirectoryProperty

  init {
    group = CRASHLYTICS_TASK_GROUP
    description =
      "Generate native symbol file used by Crashlytics to symbolicate native (NDK) stack traces"
  }

  @TaskAction
  fun generateSymbolFiles() {
    val generator: NativeSymbolGenerator =
      when (symbolGeneratorType.get() as SymbolGeneratorType) {
        SymbolGeneratorType.BREAKPAD -> BreakpadSymbolGenerator(resolveBreakpadBinary())
        SymbolGeneratorType.CSYM -> NdkCSymGenerator()
      }

    for (unstrippedNativeLibsDir in unstrippedNativeLibsDirs.files) {
      CrashlyticsBuildtools.generateNativeSymbolFiles(
        path = unstrippedNativeLibsDir,
        symbolFileOutputDir.get().asFile,
        generator,
      )
    }
  }

  /**
   * Sets and validates the unstripped native libs directories.
   *
   * Validate the [unstrippedNativeLibsOverride] does not contain a directory manually set to the
   * output of the [SingleArtifact.MERGED_NATIVE_LIBS] without include the dependency.
   *
   * This happens because for a while the plugin did not properly handle product flavors, so
   * customers would manually configure this to the output of the merged native libs task in a way
   * that didn't include the task dependencies.
   */
  private fun validateUnstrippedNativeLibsDirs(
    project: Project,
    variant: Variant,
    unstrippedNativeLibsOverride: ConfigurableFileCollection,
  ) {
    if (unstrippedNativeLibsOverride.isEmpty) {
      unstrippedNativeLibsDirs.setFrom(variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS))
      return
    }

    val mergedNativeLibsOutput = "build/intermediates/merged_native_libs/${variant.name}/out"
    if (unstrippedNativeLibsOverride.any { it.path.contains(mergedNativeLibsOutput) }) {
      val mergedNativeLibsTask = "merge${variant.name.capitalized()}NativeLibs"

      val dependencies =
        unstrippedNativeLibsOverride.buildDependencies
          .getDependencies(null)
          .mapNotNull { it?.name }
          .toSet()

      if (!dependencies.contains(mergedNativeLibsTask)) {
        try {
          dependsOn(project.tasks.getByPath(mergedNativeLibsTask))
        } catch (ex: UnknownTaskException) {
          logger.warn(
            "The unstrippedNativeLibsDir manually overridden to output of $mergedNativeLibsTask " +
              "task. This is not necessary, it is safe to remove $mergedNativeLibsOutput from " +
              "the unstrippedNativeLibsDir override."
          )
        }
      }
    }
    unstrippedNativeLibsDirs.setFrom(unstrippedNativeLibsOverride)
  }

  /** Sets and validates the symbol generator type. */
  private fun validateSymbolGeneratorType(
    project: Project,
    symbolGeneratorTypeOverride: Property<String>,
  ) {
    val symbolGeneratorTypeString =
      if (project.hasProperty(SYMBOL_GENERATOR_PROPERTY)) {
        project.findProperty(SYMBOL_GENERATOR_PROPERTY) as String
      } else if (symbolGeneratorTypeOverride.isPresent) {
        symbolGeneratorTypeOverride.get()
      } else {
        SYMBOL_GENERATOR_BREAKPAD
      }

    // This will also validate the symbol generator type string.
    symbolGeneratorType.set(SymbolGeneratorType.fromString(symbolGeneratorTypeString))
  }

  /**
   * Resolves the Breakpad binary file.
   *
   * This may extract the Breakpad binary, so this must be run at action time.
   */
  private fun resolveBreakpadBinary(): File {
    if (breakpadBinary.isPresent) {
      return breakpadBinary.get().asFile
    }

    val extractionDir = breakpadExtractionDir.get().asFile
    extractionDir.mkdirs()
    return BreakpadSymbolGenerator.extractDefaultDumpSymsBinary(extractionDir)
  }

  internal companion object {
    private const val SYMBOL_GENERATOR_PROPERTY = "com.google.firebase.crashlytics.symbolGenerator"
    private const val BREAKPAD_BINARY_PROPERTY = "com.google.firebase.crashlytics.breakpadBinary"

    fun register(
      project: Project,
      variant: Variant,
      crashlyticsExtension: CrashlyticsVariantExtension,
    ): TaskProvider<GenerateSymbolFileTask> =
      project.tasks.register<GenerateSymbolFileTask>(
        "generateCrashlyticsSymbolFile${variant.name.capitalized()}"
      ) {
        if (project.hasProperty(BREAKPAD_BINARY_PROPERTY)) {
          breakpadBinary.set(File(project.findProperty(BREAKPAD_BINARY_PROPERTY) as String))
        } else {
          breakpadBinary.set(crashlyticsExtension.breakpadBinary)
        }
        this.breakpadExtractionDir.set(buildDir(project, variant, "dump_syms"))
        this.symbolFileOutputDir.set(buildDir(project, variant, "nativeSymbols"))

        validateUnstrippedNativeLibsDirs(
          project,
          variant,
          crashlyticsExtension.unstrippedNativeLibsOverride,
        )

        validateSymbolGeneratorType(project, crashlyticsExtension.symbolGeneratorType)

        printDebugProperties()
      }
  }

  private fun printDebugProperties() {
    logger.debug("GenerateSymbolFileTask:")
    logger.debug("  unstrippedNativeLibsDirs: ${unstrippedNativeLibsDirs.asPath}")
    logger.debug("  breakpadBinary: ${breakpadBinary.orNull?.asFile?.path}")
    logger.debug("  symbolGeneratorType: ${symbolGeneratorType.orNull?.toString()}")
    logger.debug("  breakpadExtractionDir: ${breakpadExtractionDir.orNull?.asFile?.path}")
    logger.debug("  symbolFileOutputDir: ${symbolFileOutputDir.orNull?.asFile?.path}")
  }
}
