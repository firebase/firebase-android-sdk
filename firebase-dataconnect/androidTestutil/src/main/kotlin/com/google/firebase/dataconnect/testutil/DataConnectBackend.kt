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
import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.getInstance
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

// NOTE: To have firebase-tools use a different Data Connect host (e.g. staging), set the
// environment variable `FIREBASE_DATACONNECT_URL` to the URL.

sealed interface DataConnectBackend {

  val dataConnectSettings: DataConnectSettings

  fun getDataConnect(app: FirebaseApp, config: ConnectorConfig): FirebaseDataConnect =
    FirebaseDataConnect.getInstance(app, config, dataConnectSettings)

  object Production : DataConnectBackend {
    override val dataConnectSettings
      get() = DataConnectSettings()
    override fun toString() = "DataConnectBackend.Production"
  }

  sealed class PredefinedDataConnectBackend(val host: String) : DataConnectBackend {
    override val dataConnectSettings
      get() = DataConnectSettings().copy(host = host, sslEnabled = true)
  }

  object Staging :
    PredefinedDataConnectBackend("staging-firebasedataconnect.sandbox.googleapis.com") {
    override fun toString() = "DataConnectBackend.Staging($host)"
  }

  object Autopush :
    PredefinedDataConnectBackend("autopush-firebasedataconnect.sandbox.googleapis.com") {
    override fun toString() = "DataConnectBackend.Autopush($host)"
  }

  data class Custom(val host: String, val sslEnabled: Boolean) : DataConnectBackend {
    override val dataConnectSettings
      get() = DataConnectSettings().copy(host = host, sslEnabled = sslEnabled)
    override fun toString() = "DataConnectBackend.Custom(host=$host, sslEnabled=$sslEnabled)"
  }

  data class Emulator(val host: String? = null, val port: Int? = null) : DataConnectBackend {
    override val dataConnectSettings
      get() = DataConnectSettings()
    override fun toString() = "DataConnectBackend.Emulator(host=$host, port=$port)"

    override fun getDataConnect(app: FirebaseApp, config: ConnectorConfig): FirebaseDataConnect =
      super.getDataConnect(app, config).apply {
        if (host !== null && port !== null) {
          useEmulator(host = host, port = port)
        } else if (host !== null) {
          useEmulator(host = host)
        } else if (port !== null) {
          useEmulator(port = port)
        } else {
          useEmulator()
        }
      }
  }

  class InvalidInstrumentationArgument(
    argumentValue: String,
    details: String,
    cause: Throwable? = null
  ) :
    Exception(
      "Invalid value for instrumentation argument \"$INSTRUMENTATION_ARGUMENT\": " +
        "\"$argumentValue\" ($details" +
        (if (cause === null) "" else ": ${cause.message}") +
        ")",
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

    private fun URL.hostOrNull(): String? = host.ifEmpty { null }
    private fun URL.portOrNull(): Int? = port.let { if (it > 0) it else null }

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
        return Emulator(host = url.hostOrNull(), port = url.portOrNull())
      }

      val url =
        try {
          URL(arg)
        } catch (e: MalformedURLException) {
          throw InvalidInstrumentationArgument(arg, "cannot be parsed as a URL", e)
        }

      val host = url.hostOrNull()
      val port = url.portOrNull()
      val sslEnabled =
        when (url.protocol) {
          "http" -> false
          "https" -> true
          else ->
            throw InvalidInstrumentationArgument(arg, "unsupported protocol: ${url.protocol}", null)
        }

      val customHost =
        if (host !== null && port !== null) {
          "$host:$port"
        } else if (host !== null) {
          host
        } else if (port !== null) {
          ":$port"
        } else {
          throw InvalidInstrumentationArgument(arg, "a host and/or a port must be specified", null)
        }

      return Custom(host = customHost, sslEnabled = sslEnabled)
    }
  }
}
