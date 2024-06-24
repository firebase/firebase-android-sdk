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

/**
 * The various Data Connect backends against which integration tests can run.
 *
 * The integration tests generally determine which backend to use by calling
 * [fromInstrumentationArguments], which returns [Emulator] by default. This default, however, can
 * be overridden by specifying the `DATA_CONNECT_BACKEND` instrumentation argument. This argument
 * can take on the following values:
 * - `prod` ([Production]) - the Firebase Data Connect _production_ server.
 * - `staging` ([Staging]) - the Firebase Data Connect _staging_ server.
 * - `autopush` ([Autopush]) - the Firebase Data Connect _autopush_ server.
 * - `emulator` ([Emulator]) - the Firebase Data Connect _emulator_, running on the default port.
 * - `emulator://[host][:port]` ([Emulator]) - the Firebase Data Connect _emulator_, running on the
 * given host and/or port (uses the default host and/or port, if not specified).
 * - `http://host[:port]` ([Custom]) - the Firebase Data Connect server running on the given host,
 * optionally on the given port (default: 80) with sslEnabled=false.
 * - `https://host[:port]` ([Custom]) - the Firebase Data Connect server running on the given host,
 * optionally on the given port (default: 443) with sslEnabled=true.
 *
 * The instrumentation test argument can be set on the Gradle command-line by specifying
 * ```
 * -Pandroid.testInstrumentationRunnerArguments.DATA_CONNECT_BACKEND=[backend]
 * ```
 * where `[backend]` is one of the values specified above. For example, to run against production,
 * the tests could be run as follows:
 * ```
 * ./gradlew :firebase-dataconnect:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.DATA_CONNECT_BACKEND=prod
 * ```
 *
 * The instrumentation test argument can also be set in Android Studio's run configuration. Simply
 * open the run configuration for the integration tests whose backend you want to customize and add
 * the `DATA_CONNECT_BACKEND` key/value pair to the "instrumentation arguments". See the following
 * screenshots for a walkthrough:
 *
 * -
 * https://github.com/firebase/firebase-android-sdk/assets/61283819/2bcb272b-16cc-4715-ad69-a4654e08b02e
 * -
 * https://github.com/firebase/firebase-android-sdk/assets/61283819/a8766c6d-b289-4d16-a96e-d012f4acd872
 * -
 * https://github.com/firebase/firebase-android-sdk/assets/61283819/bdf1b721-a600-49ab-9e52-bf50ae05ac3e
 *
 * Googlers can see these screenshots, if the screenshots above ever get garbage collected:
 *
 * - https://screenshot.googleplex.com/9nTdBTgiojbgisu
 * - https://screenshot.googleplex.com/AmNdgDkWmR4gQXr
 * - https://screenshot.googleplex.com/8Aq5YKUXCLUAjKr
 *
 * When using "autopush" or "staging", the `firebase-tools` cli must be told about the URL of the
 * Data Connect server to which to deploy using the `FIREBASE_DATACONNECT_URL` environment variable.
 * This only matters if running a command line `firebase deploy --only dataconnect` or other
 * `firebase` commands that talk to a Data Connect backend. See the documentation of [Staging] and
 * [Autopush] for details.
 */
sealed interface DataConnectBackend {

  val dataConnectSettings: DataConnectSettings
  val authBackend: FirebaseAuthBackend

  fun getDataConnect(app: FirebaseApp, config: ConnectorConfig): FirebaseDataConnect =
    FirebaseDataConnect.getInstance(app, config, dataConnectSettings)

  /** The "production" Data Connect server, which is used by customers. */
  object Production : DataConnectBackend {
    override val dataConnectSettings
      get() = DataConnectSettings()
    override val authBackend: FirebaseAuthBackend
      get() = FirebaseAuthBackend.Production
    override fun toString() = "DataConnectBackend.Production"
  }

  sealed class PredefinedDataConnectBackend(val host: String) : DataConnectBackend {
    override val dataConnectSettings
      get() = DataConnectSettings().copy(host = host, sslEnabled = true)
    override val authBackend: FirebaseAuthBackend
      get() = FirebaseAuthBackend.Production
  }

  /**
   * The "staging" Data Connect server, which is updated roughly weekly with the latest code.
   *
   * In order to instruct firebase-tools to run against the staging backend, set the environment
   * variable `FIREBASE_DATACONNECT_URL=https://staging-firebasedataconnect.sandbox.googleapis.com`
   */
  object Staging :
    PredefinedDataConnectBackend("staging-firebasedataconnect.sandbox.googleapis.com") {
    override fun toString() = "DataConnectBackend.Staging($host)"
  }

  /**
   * The "autopush" Data Connect server, which is updated every 2 hours with the latest code
   *
   * In order to instruct firebase-tools to run autopush the staging backend, set the environment
   * variable `FIREBASE_DATACONNECT_URL=https://autopush-firebasedataconnect.sandbox.googleapis.com`
   */
  object Autopush :
    PredefinedDataConnectBackend("autopush-firebasedataconnect.sandbox.googleapis.com") {
    override fun toString() = "DataConnectBackend.Autopush($host)"
  }

  /** A custom Data Connect server. */
  data class Custom(val host: String, val sslEnabled: Boolean) : DataConnectBackend {
    override val dataConnectSettings
      get() = DataConnectSettings().copy(host = host, sslEnabled = sslEnabled)
    override val authBackend: FirebaseAuthBackend
      get() = FirebaseAuthBackend.Production
    override fun toString() = "DataConnectBackend.Custom(host=$host, sslEnabled=$sslEnabled)"
  }

  /** The Data Connect emulator. */
  data class Emulator(val host: String? = null, val port: Int? = null) : DataConnectBackend {
    override val dataConnectSettings
      get() = DataConnectSettings()
    override val authBackend: FirebaseAuthBackend
      get() = FirebaseAuthBackend.Emulator()
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

    /**
     * The name of the instrumentation argument that can be set to override the Data Connect backend
     * to use.
     */
    private const val INSTRUMENTATION_ARGUMENT = "DATA_CONNECT_BACKEND"

    /**
     * Returns the Data Connect backend to use, according to the [INSTRUMENTATION_ARGUMENT]
     * instrumentation argument, or [Emulator] if the instrumentation argument is not set.
     *
     * This method should generally be called by integration tests to determine which Data Connect
     * backend to use.
     */
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
