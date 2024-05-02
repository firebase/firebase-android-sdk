// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect.testutil

import androidx.annotation.VisibleForTesting
import androidx.test.platform.app.InstrumentationRegistry
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

// NOTE: To have firebase-tools use a different Data Connect host (e.g. staging), set the
// environment variable `FIREBASE_DATACONNECT_URL` to the URL.

sealed interface DataConnectBackend {

  object Production : DataConnectBackend {
    override fun toString() = "DataConnectBackend.Production"
  }

  object Staging : DataConnectBackend {
    const val host = "https://staging-firebasedataconnect.sandbox.googleapis.com"
    override fun toString() = "DataConnectBackend.Staging($host)"
  }

  object Autopush : DataConnectBackend {
    const val host = "https://autopush-firebasedataconnect.sandbox.googleapis.com"
    override fun toString() = "DataConnectBackend.Autopush($host)"
  }

  data class Custom(val host: String) : DataConnectBackend {
    override fun toString() = "DataConnectBackend.Custom(host=$host)"
  }

  data class Emulator(val host: String? = null, val port: Int? = null) : DataConnectBackend {
    override fun toString() = "DataConnectBackend.Emulator(host=$host, port=$port)"
  }

  class InvalidInstrumentationArgument(argumentValue: String, details: String, cause: Throwable) :
    Exception(
      "Invalid value for instrumentation argument \"$INSTRUMENTATION_ARGUMENT\": " +
        "\"$argumentValue\" ($details: ${cause.message}",
      cause
    )

  companion object {

    private const val INSTRUMENTATION_ARGUMENT = "DATA_CONNECT_BACKEND"

    fun fromInstrumentationArguments(): DataConnectBackend {
      val bundle =
        try {
          InstrumentationRegistry.getArguments()
        } catch (_: IllegalStateException) {
          // Treat IllegalStateException the same as no arguments specified, since getArguments()
          // documents that it throws IllegalStateException "if no argument Bundle has been
          // registered."
          null
        }

      val argument = bundle?.getString(INSTRUMENTATION_ARGUMENT)
      return fromInstrumentationArgument(argument) ?: Emulator()
    }

    @VisibleForTesting
    internal fun fromInstrumentationArgument(arg: String?): DataConnectBackend? {
      if (arg === null) {
        return null
      }

      when (arg) {
        "prod" -> return Production
        "staging" -> return Staging
        "autopush" -> return Autopush
        "emulator" -> return Emulator()
      }

      val uri =
        try {
          URI(arg)
        } catch (e: URISyntaxException) {
          throw InvalidInstrumentationArgument(arg, "cannot be parsed as a URI", e)
        }

      if (uri.scheme == "emulator") {
        val url =
          try {
            URL("https://${uri.schemeSpecificPart}")
          } catch (e: MalformedURLException) {
            throw InvalidInstrumentationArgument(arg, "invalid 'emulator' URI", e)
          }
        val emulatorHost = url.host.ifEmpty { null }
        val emulatorPort = url.port.let { if (it > 0) it else null }
        return Emulator(host = emulatorHost, port = emulatorPort)
      }

      try {
        URL(arg)
      } catch (e: MalformedURLException) {
        throw InvalidInstrumentationArgument(arg, "cannot be parsed as a URL", e)
      }

      return Custom(arg)
    }
  }
}
