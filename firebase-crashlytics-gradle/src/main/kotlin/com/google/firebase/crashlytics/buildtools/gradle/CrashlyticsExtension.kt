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

import com.google.firebase.crashlytics.buildtools.CrashlyticsOptions.SYMBOL_GENERATOR_BREAKPAD
import com.google.firebase.crashlytics.buildtools.CrashlyticsOptions.SYMBOL_GENERATOR_CSYM
import groovy.lang.Closure
import java.io.File

/** Firebase Crashlytics extension. */
interface CrashlyticsExtension {
  /** Mapping file upload enabled. */
  var mappingFileUploadEnabled: Boolean?

  /** Native symbol upload enabled. */
  var nativeSymbolUploadEnabled: Boolean?

  /**
   * Override the unstripped native libs dir.
   *
   * Accepts any valid argument to [org.gradle.api.Project.files], including String, File,
   * FileCollection, etc.
   *
   * See https://docs.gradle.org/8.1/javadoc/org/gradle/api/Project.html#files-java.lang.Object...-
   */
  var unstrippedNativeLibsDir: Any?

  /**
   * Override the default symbol generator type, [SYMBOL_GENERATOR_BREAKPAD].
   *
   * Set to [SYMBOL_GENERATOR_CSYM] for builds that cannot use breakpad:
   * ```kotlin
   * symbolGeneratorType = "csym"
   * ```
   */
  var symbolGeneratorType: String?

  /** Override the breakpad binary. Only used for [SYMBOL_GENERATOR_BREAKPAD] generator. */
  var breakpadBinary: File?

  /**
   * This field is deprecated, setting it will cause a build error. Will be removed after July 2024.
   *
   * Use fields [symbolGeneratorType] and/or [breakpadBinary] instead.
   */
  @set:Deprecated(
    level = DeprecationLevel.ERROR,
    message = "Use fields symbolGeneratorType and/or breakpadBinary instead.",
  )
  var symbolGenerator: Closure<*>?
}
