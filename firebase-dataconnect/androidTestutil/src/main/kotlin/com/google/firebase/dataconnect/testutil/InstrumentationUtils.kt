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

package com.google.firebase.dataconnect.testutil

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry

fun getInstrumentationArguments(): Bundle? =
  try {
    InstrumentationRegistry.getArguments()
  } catch (_: IllegalStateException) {
    // Treat IllegalStateException the same as no arguments specified, since getArguments()
    // documents that it throws IllegalStateException "if no argument Bundle has been
    // registered."
    null
  }

fun getInstrumentationArgument(key: String): String? = getInstrumentationArguments()?.getString(key)

class InvalidInstrumentationArgumentException(
  key: String,
  value: String,
  details: String,
  cause: Throwable? = null
) :
  Exception(
    "Invalid value for instrumentation argument \"$key\": " +
      "\"$value\" ($details" +
      (if (cause === null) "" else ": ${cause.message}") +
      ")",
    cause
  )
