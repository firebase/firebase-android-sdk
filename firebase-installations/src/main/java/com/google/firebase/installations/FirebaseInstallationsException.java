// Copyright 2019 Google LLC
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

package com.google.firebase.installations;

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseException;

/**
 * The class for all Exceptions thrown by {@link FirebaseInstallations}.
 *
 * @hide
 */
public class FirebaseInstallationsException extends FirebaseException {
  public enum Status {
    /**
     * Indicates that the caller is misconfigured, usually with a bad or misconfigured API Key or
     * Project.
     */
    BAD_CONFIG,

    /**
     * The service is currently unavailable. This is a most likely due to a transient condition and
     * may be corrected by retrying. We recommend exponential backoff when retrying requests.
     */
    UNAVAILABLE,
    /**
     * Firebase servers have received too many requests in a short period of time from the client.
     */
    TOO_MANY_REQUESTS,
  }

  @NonNull private final Status status;

  public FirebaseInstallationsException(@NonNull Status status) {
    this.status = status;
  }

  public FirebaseInstallationsException(@NonNull String message, @NonNull Status status) {
    super(message);
    this.status = status;
  }

  public FirebaseInstallationsException(
      @NonNull String message, @NonNull Status status, @NonNull Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  /**
   * Gets the status for the operation that failed.
   *
   * @return the status for the FirebaseInstallationsException
   */
  @NonNull
  public Status getStatus() {
    return status;
  }
}
