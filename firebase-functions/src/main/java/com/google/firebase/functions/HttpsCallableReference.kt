// Copyright 2018 Google LLC
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
package com.google.firebase.functions

import androidx.annotation.VisibleForTesting
import com.google.android.gms.tasks.Task
import java.net.URL
import java.util.concurrent.TimeUnit
import org.reactivestreams.Publisher

/** A reference to a particular Callable HTTPS trigger in Cloud Functions. */
public class HttpsCallableReference {
  // The functions client to use for making calls.
  private val functionsClient: FirebaseFunctions

  // The name of the HTTPS endpoint this reference refers to.
  // Is null if url is set.
  private val name: String?

  // The url of the HTTPS endpoint this reference refers to.
  // Is null if name is set.
  private val url: URL?

  // Options for how to do the HTTPS call.
  @VisibleForTesting internal val options: HttpsCallOptions

  /** Creates a new reference with the given options. */
  internal constructor(
    functionsClient: FirebaseFunctions,
    name: String?,
    options: HttpsCallOptions
  ) {
    this.functionsClient = functionsClient
    this.name = name
    url = null
    this.options = options
  }

  /** Creates a new reference with the given options. */
  internal constructor(functionsClient: FirebaseFunctions, url: URL?, options: HttpsCallOptions) {
    this.functionsClient = functionsClient
    name = null
    this.url = url
    this.options = options
  }

  /**
   * Executes this Callable HTTPS trigger asynchronously.
   *
   * The data passed into the trigger can be any of the following types:
   *
   * * Any primitive type, including null, int, long, float, and boolean.
   * * [String]
   * * [List<?>][java.util.List], where the contained objects are also one of these types.
   * * [Map<String, ?>][java.util.Map], where the values are also one of these types.
   * * [org.json.JSONArray]
   * * [org.json.JSONObject]
   * * [org.json.JSONObject.NULL]
   *
   * If the returned task fails, the Exception will be one of the following types:
   *
   * * [java.io.IOException]
   * - if the HTTPS request failed to connect.
   * * [FirebaseFunctionsException]
   * - if the request connected, but the function returned an error.
   *
   * The request to the Cloud Functions backend made by this method automatically includes a
   * Firebase Instance ID token to identify the app instance. If a user is logged in with Firebase
   * Auth, an auth token for the user will also be automatically included.
   *
   * Firebase Instance ID sends data to the Firebase backend periodically to collect information
   * regarding the app instance. To stop this, see
   * [com.google.firebase.iid.FirebaseInstanceId.deleteInstanceId]. It will resume with a new
   * Instance ID the next time you call this method.
   *
   * @param data Parameters to pass to the trigger.
   * @return A Task that will be completed when the HTTPS request has completed.
   * @see org.json.JSONArray
   *
   * @see org.json.JSONObject
   *
   * @see java.io.IOException
   *
   * @see FirebaseFunctionsException
   */
  public fun call(data: Any?): Task<HttpsCallableResult> {
    return if (name != null) {
      functionsClient.call(name, data, options)
    } else {
      functionsClient.call(url!!, data, options)
    }
  }

  /**
   * Executes this HTTPS endpoint asynchronously without arguments.
   *
   * The request to the Cloud Functions backend made by this method automatically includes a
   * Firebase Instance ID token to identify the app instance. If a user is logged in with Firebase
   * Auth, an auth token for the user will also be automatically included.
   *
   * Firebase Instance ID sends data to the Firebase backend periodically to collect information
   * regarding the app instance. To stop this, see
   * [com.google.firebase.iid.FirebaseInstanceId.deleteInstanceId]. It will resume with a new
   * Instance ID the next time you call this method.
   *
   * @return A Task that will be completed when the HTTPS request has completed.
   */
  public fun call(): Task<HttpsCallableResult> {
    return if (name != null) {
      functionsClient.call(name, null, options)
    } else {
      functionsClient.call(url!!, null, options)
    }
  }

  /**
   * Streams data to the specified HTTPS endpoint.
   *
   * The data passed into the trigger can be any of the following types:
   *
   * * Any primitive type, including null, int, long, float, and boolean.
   * * [String]
   * * [List<?>][java.util.List], where the contained objects are also one of these types.
   * * [Map<String, ?>][java.util.Map], where the values are also one of these types.
   * * [org.json.JSONArray]
   * * [org.json.JSONObject]
   * * [org.json.JSONObject.NULL]
   *
   * If the returned streamResponse fails, the exception will be one of the following types:
   *
   * * [java.io.IOException]
   * - if the HTTPS request failed to connect.
   * * [FirebaseFunctionsException]
   * - if the request connected, but the function returned an error.
   *
   * The request to the Cloud Functions backend made by this method automatically includes a
   * Firebase Instance ID token to identify the app instance. If a user is logged in with Firebase
   * Auth, an auth token for the user will also be automatically included.
   *
   * Firebase Instance ID sends data to the Firebase backend periodically to collect information
   * regarding the app instance. To stop this, see
   * [com.google.firebase.iid.FirebaseInstanceId.deleteInstanceId]. It will resume with a new
   * Instance ID the next time you call this method.
   *
   * @param data Parameters to pass to the endpoint. Defaults to `null` if not provided.
   * @return [Publisher] that will emit intermediate data, and the final result, as it is generated
   * by the function.
   * @see org.json.JSONArray
   *
   * @see org.json.JSONObject
   *
   * @see java.io.IOException
   *
   * @see FirebaseFunctionsException
   */
  @JvmOverloads
  public fun stream(data: Any? = null): Publisher<StreamResponse> {
    return if (name != null) {
      functionsClient.stream(name, data, options)
    } else {
      functionsClient.stream(requireNotNull(url), data, options)
    }
  }

  /**
   * Changes the timeout for calls from this instance of Functions. The default is 60 seconds.
   *
   * @param timeout The length of the timeout, in the given units.
   * @param units The units for the specified timeout.
   */
  public fun setTimeout(timeout: Long, units: TimeUnit) {
    options.setTimeout(timeout, units)
  }

  public val timeout: Long
    /**
     * Returns the timeout for calls from this instance of Functions.
     *
     * @return The timeout, in milliseconds.
     */
    get() = options.getTimeout()

  /**
   * Creates a new reference with the given timeout for calls. The default is 60 seconds.
   *
   * @param timeout The length of the timeout, in the given units.
   * @param units The units for the specified timeout.
   */
  public fun withTimeout(timeout: Long, units: TimeUnit): HttpsCallableReference {
    val other = HttpsCallableReference(functionsClient, name, options)
    other.setTimeout(timeout, units)
    return other
  }

  /**
   * Adds an HTTP header for calls from this instance of Functions.
   *
   * Note that an existing header with the same name will be overwritten.
   *
   * @param name Name of HTTP header
   * @param value Value of HTTP header
   */
  public fun addHeader(name: String, value: String): HttpsCallableReference {
    options.addHeader(name, value)
    return this
  }

  /**
   * Adds all HTTP headers of passed map for calls from this instance of Functions.
   *
   * Note that an existing header with the same name will be overwritten.
   *
   * @param headers Map of HTTP headers (name to value)
   */
  public fun addHeaders(headers: Map<String, String>): HttpsCallableReference {
    options.addHeaders(headers)
    return this
  }
}
