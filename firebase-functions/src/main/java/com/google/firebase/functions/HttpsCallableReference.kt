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

import com.google.android.gms.tasks.Task
import java.net.URL
import java.util.concurrent.TimeUnit

/** A reference to a particular Callable HTTPS trigger in Cloud Functions.  */
class HttpsCallableReference {
  // The functions client to use for making calls.
  private val functionsClient: FirebaseFunctions

  // The name of the HTTPS endpoint this reference refers to.
  // Is null if url is set.
  private val name: String?

  // The url of the HTTPS endpoint this reference refers to.
  // Is null if name is set.
  private val url: URL?

  // Options for how to do the HTTPS call.
  private val options: HttpsCallOptions

  /** Creates a new reference with the given options.  */
  internal constructor(functionsClient: FirebaseFunctions, name: String?, options: HttpsCallOptions) {
    this.functionsClient = functionsClient
    this.name = name
    url = null
    this.options = options
  }

  /** Creates a new reference with the given options.  */
  internal constructor(functionsClient: FirebaseFunctions, url: URL?, options: HttpsCallOptions) {
    this.functionsClient = functionsClient
    name = null
    this.url = url
    this.options = options
  }

  /**
   * Executes this Callable HTTPS trigger asynchronously.
   *
   *
   * The data passed into the trigger can be any of the following types:
   *
   *
   *  * Any primitive type, including null, int, long, float, and boolean.
   *  * [String]
   *  * [List&amp;lt;?&amp;gt;][java.util.List], where the contained objects are also one of these
   * types.
   *  * [Map&amp;lt;String, ?&amp;gt;&gt;][java.util.Map], where the values are also one of these
   * types.
   *  * [org.json.JSONArray]
   *  * [org.json.JSONObject]
   *  * [org.json.JSONObject.NULL]
   *
   *
   *
   * If the returned task fails, the Exception will be one of the following types:
   *
   *
   *  * [java.io.IOException] - if the HTTPS request failed to connect.
   *  * [FirebaseFunctionsException] - if the request connected, but the function returned
   * an error.
   *
   *
   *
   * The request to the Cloud Functions backend made by this method automatically includes a
   * Firebase Instance ID token to identify the app instance. If a user is logged in with Firebase
   * Auth, an auth token for the user will also be automatically included.
   *
   *
   * Firebase Instance ID sends data to the Firebase backend periodically to collect information
   * regarding the app instance. To stop this, see [ ][com.google.firebase.iid.FirebaseInstanceId.deleteInstanceId]. It will resume with a new
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
  fun call(data: Any?): Task<HttpsCallableResult?> {
    return if (name != null) {
      functionsClient.call(name, data, options)
    } else {
      functionsClient.call(url!!, data, options)
    }
  }

  /**
   * Executes this HTTPS endpoint asynchronously without arguments.
   *
   *
   * The request to the Cloud Functions backend made by this method automatically includes a
   * Firebase Instance ID token to identify the app instance. If a user is logged in with Firebase
   * Auth, an auth token for the user will also be automatically included.
   *
   *
   * Firebase Instance ID sends data to the Firebase backend periodically to collect information
   * regarding the app instance. To stop this, see [ ][com.google.firebase.iid.FirebaseInstanceId.deleteInstanceId]. It will resume with a new
   * Instance ID the next time you call this method.
   *
   * @return A Task that will be completed when the HTTPS request has completed.
   */
  fun call(): Task<HttpsCallableResult?> {
    return if (name != null) {
      functionsClient.call(name, null, options)
    } else {
      functionsClient.call(url!!, null, options)
    }
  }

  /**
   * Changes the timeout for calls from this instance of Functions. The default is 60 seconds.
   *
   * @param timeout The length of the timeout, in the given units.
   * @param units The units for the specified timeout.
   */
  fun setTimeout(timeout: Long, units: TimeUnit) {
    options.setTimeout(timeout, units)
  }

  val timeout: Long
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
  fun withTimeout(timeout: Long, units: TimeUnit): HttpsCallableReference {
    val other = HttpsCallableReference(functionsClient, name, options)
    other.setTimeout(timeout, units)
    return other
  }
}