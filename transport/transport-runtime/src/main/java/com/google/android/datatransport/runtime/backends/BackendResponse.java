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

package com.google.android.datatransport.runtime.backends;

import com.google.auto.value.AutoValue;

/**
 * Represents a response from a {@link TransportBackend}.
 *
 * <p>It contains the response status and time to wait before trying to send another request to it.
 */
@AutoValue
public abstract class BackendResponse {
  public enum Status {
    OK,
    TRANSIENT_ERROR,
    FATAL_ERROR,
  }

  /** Status result of the backend call */
  public abstract Status getStatus();

  /** Time in millis to wait before attempting another request. */
  public abstract long getNextRequestWaitMillis();

  public static BackendResponse transientError() {
    return new AutoValue_BackendResponse(Status.TRANSIENT_ERROR, -1);
  }

  public static BackendResponse fatalError() {
    return new AutoValue_BackendResponse(Status.FATAL_ERROR, -1);
  }

  public static BackendResponse ok(long nextRequestWaitMillis) {
    return new AutoValue_BackendResponse(Status.OK, nextRequestWaitMillis);
  }
}
