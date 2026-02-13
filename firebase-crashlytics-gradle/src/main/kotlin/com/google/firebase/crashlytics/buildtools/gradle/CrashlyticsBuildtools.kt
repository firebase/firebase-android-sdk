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

package com.google.firebase.crashlytics.buildtools.gradle

import com.google.firebase.crashlytics.buildtools.AppBuildInfo
import com.google.firebase.crashlytics.buildtools.Buildtools
import com.google.firebase.crashlytics.buildtools.Obfuscator
import com.google.firebase.crashlytics.buildtools.api.SymbolFileService
import com.google.firebase.crashlytics.buildtools.log.CrashlyticsLogger
import com.google.firebase.crashlytics.buildtools.ndk.NativeSymbolGenerator
import java.io.File
import org.gradle.api.Project
import org.gradle.api.logging.Logger

/**
 * Wrapper for the Crashlytics build tools.
 *
 * This centralizes access to the various Crashlytics build tools from within the plugin, as well as
 * allowing faking the build tools for dry runs or testing purposes.
 */
internal object CrashlyticsBuildtools {
  const val BLANK_MAPPING_FILE_ID = Buildtools.DUMMY_MAPPING_ID

  private const val BUILDTOOLS_PROPERTY = "com.google.firebase.crashlytics.buildtools"
  private const val DEBUG_LOG_REDIRECT_PROPERTY = "com.google.firebase.crashlytics.logDebug"
  private const val FAKE_OUTPUT_PROPERTY = "com.google.firebase.crashlytics.fakeOutput"
  private const val FAKE_ID_PROPERTY = "com.google.firebase.crashlytics.fakeId"

  private val buildtools: Buildtools by lazy(::configureBuildtools)
  private var isFake: Boolean = false
  private var generateFakeOutput: Boolean = false
  private var fakeId: String? = null
  private var logger: CrashlyticsGradleLogger? = null

  /** Configure the build tools wrapper. This must be called before any other function. */
  fun configure(project: Project) {
    isFake = project.providers.gradleProperty(BUILDTOOLS_PROPERTY).orNull == "pretend"
    generateFakeOutput = project.providers.gradleProperty(FAKE_OUTPUT_PROPERTY).orNull == "generate"
    fakeId = project.providers.gradleProperty(FAKE_ID_PROPERTY).orNull
    logger =
      CrashlyticsGradleLogger(
        projectLogger = project.logger,
        debugRedirect =
          CrashlyticsLogger.Level.valueOf(
            project.providers.gradleProperty(DEBUG_LOG_REDIRECT_PROPERTY).orElse("DEBUG").get()
          ),
      )
  }

  /** Generates native symbol files for the given path, using the given symbol generator. */
  fun generateNativeSymbolFiles(
    path: File,
    symbolFileOutputDir: File,
    symbolGenerator: NativeSymbolGenerator,
  ) =
    if (isFake) {
      if (generateFakeOutput) {
        symbolFileOutputDir.mkdirs()
        File(symbolFileOutputDir, "fake.so").writeText("Matt says hi!")
      }
      logPretendCall(
        "generateNativeSymbolFiles",
        path.name,
        symbolFileOutputDir.name,
        symbolGenerator::class.simpleName,
      )
    } else {
      buildtools.generateNativeSymbolFiles(path, symbolFileOutputDir, symbolGenerator)
    }

  /** Adds the build ids as a String array resource to the given Android resource (XML) file. */
  fun injectBuildIdsIntoResource(mergedNativeLibsPath: File, resourceFile: File): Boolean =
    if (isFake) {
      logPretendCall("injectBuildIdsIntoResource", mergedNativeLibsPath.name, resourceFile.name)
      true
    } else {
      buildtools.injectBuildIdsIntoResource(mergedNativeLibsPath, resourceFile)
    }

  /** Adds the mappingFileId as a String resource to the given Android resource (XML) file. */
  fun injectMappingFileIdIntoResource(resourceFile: File, mappingFileId: String): Boolean =
    if (isFake) {
      logPretendCall("injectMappingFileIdIntoResource", resourceFile.name, mappingFileId)
      true
    } else {
      buildtools.injectMappingFileIdIntoResource(resourceFile, mappingFileId)
    }

  /** Uploads the given mapping file. */
  fun uploadMappingFile(
    mappingFile: File,
    mappingFileId: String,
    appBuildInfo: AppBuildInfo,
    obfuscator: Obfuscator,
  ) =
    if (isFake) {
      logPretendCall(
        "uploadMappingFile",
        mappingFile.name,
        mappingFileId,
        "${appBuildInfo.packageName} ${appBuildInfo.googleAppId} ${appBuildInfo.buildDir.name}",
        "${obfuscator.vendor.name} ${obfuscator.version}",
      )
    } else {
      buildtools.uploadMappingFile(mappingFile, mappingFileId, appBuildInfo, obfuscator)
    }

  /** Uploads all the native symbol files in symbolFileDir. */
  fun uploadNativeSymbolFiles(
    symbolFileDir: File,
    appId: String,
    symbolFileService: SymbolFileService,
  ) =
    if (isFake) {
      logPretendCall(
        "uploadNativeSymbolFiles",
        symbolFileDir.name,
        appId,
        symbolFileService::class.simpleName,
      )
    } else {
      buildtools.uploadNativeSymbolFiles(symbolFileDir, appId, symbolFileService)
    }

  /** Generate a random valid mapping file id. */
  fun generateMappingFileId(): String =
    if (isFake) {
      fakeId ?: "test1234"
    } else {
      Buildtools.generateMappingFileId()
    }

  /** Configure the actual build tools. */
  private fun configureBuildtools(): Buildtools {
    val buildtools = Buildtools.getInstance()
    // TODO(mrober): Remove this manual wrapper when buildtools switches to slf4j.
    logger?.let(Buildtools::setLogger)
    buildtools.setBuildtoolsClientInfo(
      javaClass.`package`?.implementationTitle,
      javaClass.`package`?.implementationVersion,
    )
    return buildtools
  }

  /** Log the function calls in a consistent format when in pretend mode. */
  private fun logPretendCall(functionName: String, vararg params: String?) =
    logger?.logQ(
      // Don't change this format because tests rely on exact output lines.
      "$functionName - ${params.filterNotNull().filter(String::isNotBlank).joinToString(" ")}"
    )

  /**
   * Sets up the Crashlytics logger, used by the Crashlytics build tools classes to manage logging
   * output. By default, this logger will write all logging messages at DEBUG and above to the
   * Gradle platform logger. The level of the Gradle platform logger will determine if those
   * messages actually get displayed in the log.
   */
  private class CrashlyticsGradleLogger(
    private val projectLogger: Logger,
    private val debugRedirect: CrashlyticsLogger.Level = CrashlyticsLogger.Level.DEBUG,
  ) : CrashlyticsLogger {
    private var level: CrashlyticsLogger.Level = CrashlyticsLogger.Level.DEBUG

    /**
     * The Gradle platform logger determines the output level. So this method doesn't do anything
     * other than ensure VERBOSE is redirected to DEBUG when necessary, as Gradle doesn't have a
     * VERBOSE level.
     */
    override fun setLevel(level: CrashlyticsLogger.Level) {
      this.level = level
    }

    override fun logV(msg: String) {
      if (level.logsFor(CrashlyticsLogger.Level.VERBOSE)) {
        projectLogger.debug(msg)
      }
    }

    override fun logD(msg: String) =
      when (debugRedirect) {
        CrashlyticsLogger.Level.ERROR -> projectLogger.error(msg)
        CrashlyticsLogger.Level.WARNING -> projectLogger.warn(msg)
        CrashlyticsLogger.Level.INFO -> projectLogger.info(msg)
        else -> projectLogger.debug(msg)
      }

    override fun logI(msg: String) = projectLogger.info(msg)

    override fun logW(msg: String, t: Throwable?) = projectLogger.warn(msg, t)

    /** Log a message at the Gradle specific log level QUIET. */
    fun logQ(msg: String) = projectLogger.quiet(msg)

    override fun logE(msg: String, t: Throwable?) = projectLogger.error(msg, t)
  }
}
