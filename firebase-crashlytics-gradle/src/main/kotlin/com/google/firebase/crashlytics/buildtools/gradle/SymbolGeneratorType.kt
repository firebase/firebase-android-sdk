/*
 * Copyright 2024 Google LLC
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
import org.gradle.api.GradleException

/** Symbol generator type. */
enum class SymbolGeneratorType {
  /** Breakpad symbol generator type. */
  BREAKPAD,
  /** CSym symbol generator type. */
  CSYM;

  /** Returns the symbol generator type string that could be passed back into [fromString]. */
  override fun toString(): String =
    when (this) {
      BREAKPAD -> SYMBOL_GENERATOR_BREAKPAD
      CSYM -> SYMBOL_GENERATOR_CSYM
    }

  internal companion object {
    /** Parses a symbol generator type string that would be passed as a property or extension. */
    fun fromString(symbolGeneratorType: String): SymbolGeneratorType =
      when (symbolGeneratorType) {
        SYMBOL_GENERATOR_BREAKPAD -> BREAKPAD
        SYMBOL_GENERATOR_CSYM -> CSYM
        else ->
          throw GradleException(
            "The given symbol generator \"$symbolGeneratorType\" is not supported. Use a valid " +
              "value for the symbolGenerator option, such as $SYMBOL_GENERATOR_BREAKPAD."
          )
      }
  }
}
