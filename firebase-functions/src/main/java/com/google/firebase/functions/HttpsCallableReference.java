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

package com.google.firebase.functions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.tasks.Task;
import java.util.concurrent.TimeUnit;

/** A reference to a particular Callable HTTPS trigger in Cloud Functions. */
public class HttpsCallableReference {

  // The functions client to use for making calls.
  private final FirebaseFunctions functionsClient;

  // The name of the HTTPS endpoint this reference refers to.
  private final String name;

  // Options for how to do the HTTPS call.
  HttpsCallOptions options = new HttpsCallOptions();

  /** Creates a new reference with the given options. */
  HttpsCallableReference(FirebaseFunctions functionsClient, String name) {
    this.functionsClient = functionsClient;
    this.name = name;
  }

  /**
   * Executes this Callable HTTPS trigger asynchronously.
   *
   * <p>The data passed into the trigger can be any of the following types:
   *
   * <ul>
   *   <li>Any primitive type, including null, int, long, float, and boolean.
   *   <li>{@link String}
   *   <li>{@link java.util.List List&lt;?&gt;}, where the contained objects are also one of these
   *       types.
   *   <li>{@link java.util.Map Map&lt;String, ?&gt;>}, where the values are also one of these
   *       types.
   *   <li>{@link org.json.JSONArray}
   *   <li>{@link org.json.JSONObject}
   *   <li>{@link org.json.JSONObject#NULL}
   * </ul>
   *
   * <p>If the returned task fails, the Exception will be one of the following types:
   *
   * <ul>
   *   <li>{@link java.io.IOException} - if the HTTPS request failed to connect.
   *   <li>{@link FirebaseFunctionsException} - if the request connected, but the function returned
   *       an error.
   * </ul>
   *
   * <p>The request to the Cloud Functions backend made by this method automatically includes a
   * Firebase Instance ID token to identify the app instance. If a user is logged in with Firebase
   * Auth, an auth token for the user will also be automatically included.
   *
   * <p>Firebase Instance ID sends data to the Firebase backend periodically to collect information
   * regarding the app instance. To stop this, see {@link
   * com.google.firebase.iid.FirebaseInstanceId#deleteInstanceId}. It will resume with a new
   * Instance ID the next time you call this method.
   *
   * @param data Parameters to pass to the trigger.
   * @return A Task that will be completed when the HTTPS request has completed.
   * @see org.json.JSONArray
   * @see org.json.JSONObject
   * @see java.io.IOException
   * @see FirebaseFunctionsException
   */
  @NonNull
  public Task<HttpsCallableResult> call(@Nullable Object data) {
    return functionsClient.call(name, data, options);
  }

  /**
   * Executes this HTTPS endpoint asynchronously without arguments.
   *
   * <p>The request to the Cloud Functions backend made by this method automatically includes a
   * Firebase Instance ID token to identify the app instance. If a user is logged in with Firebase
   * Auth, an auth token for the user will also be automatically included.
   *
   * <p>Firebase Instance ID sends data to the Firebase backend periodically to collect information
   * regarding the app instance. To stop this, see {@link
   * com.google.firebase.iid.FirebaseInstanceId#deleteInstanceId}. It will resume with a new
   * Instance ID the next time you call this method.
   *
   * @return A Task that will be completed when the HTTPS request has completed.
   */
  @NonNull
  public Task<HttpsCallableResult> call() {
    return functionsClient.call(name, null, options);
  }

  /**
   * Changes the timeout for calls from this instance of Functions. The default is 60 seconds.
   *
   * @param timeout The length of the timeout, in the given units.
   * @param units The units for the specified timeout.
   */
  public void setTimeout(long timeout, @NonNull TimeUnit units) {
    options.setTimeout(timeout, units);
  }

  /**
   * Returns the timeout for calls from this instance of Functions.
   *
   * @return The timeout, in milliseconds.
   */
  public long getTimeout() {
    return options.getTimeout();
  }

  /**
   * Creates a new reference with the given timeout for calls. The default is 60 seconds.
   *
   * @param timeout The length of the timeout, in the given units.
   * @param units The units for the specified timeout.
   */
  @NonNull
  public HttpsCallableReference withTimeout(long timeout, @NonNull TimeUnit units) {
    HttpsCallableReference other = new HttpsCallableReference(functionsClient, name);
    other.setTimeout(timeout, units);
    return other;
  }
}
