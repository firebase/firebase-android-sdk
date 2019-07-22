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

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/** An internal class for keeping track of options applied to an HttpsCallableReference. */
class HttpsCallOptions {

  // The default timeout to use for all calls.
  private static final long DEFAULT_TIMEOUT = 70;
  private static final TimeUnit DEFAULT_TIMEOUT_UNITS = TimeUnit.SECONDS;

  // The timeout to use for calls from references created by this Functions.
  private long timeout = DEFAULT_TIMEOUT;
  private TimeUnit timeoutUnits = DEFAULT_TIMEOUT_UNITS;

  /**
   * Changes the timeout for calls from this instance of Functions. The default is 60 seconds.
   *
   * @param timeout The length of the timeout, in the given units.
   * @param units The units for the specified timeout.
   */
  void setTimeout(long timeout, TimeUnit units) {
    this.timeout = timeout;
    this.timeoutUnits = units;
  }

  /**
   * Returns the timeout for calls from this instance of Functions.
   *
   * @return The timeout, in milliseconds.
   */
  public long getTimeout() {
    return timeoutUnits.toMillis(timeout);
  }

  /** Creates a new OkHttpClient with these options applied to it. */
  OkHttpClient apply(OkHttpClient client) {
    return client
        .newBuilder()
        .callTimeout(timeout, timeoutUnits)
        .readTimeout(timeout, timeoutUnits)
        .build();
  }
}
