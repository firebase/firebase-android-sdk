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

package com.google.firebase.installations.remote;

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseException;

/** The class for all Exceptions thrown by {@link FirebaseInstallationServiceClient}. */
public class FirebaseInstallationServiceException extends FirebaseException {

  public enum Code {
    SERVER_ERROR,

    NETWORK_ERROR,

    UNAUTHORIZED
  }

  @NonNull private final Code code;

  FirebaseInstallationServiceException(@NonNull Code code) {
    this.code = code;
  }

  FirebaseInstallationServiceException(@NonNull String message, @NonNull Code code) {
    super(message);
    this.code = code;
  }

  FirebaseInstallationServiceException(
      @NonNull String message, @NonNull Code code, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /**
   * Gets the status code for the operation that failed.
   *
   * @return the code for the FirebaseInstallationServiceException
   */
  @NonNull
  public Code getCode() {
    return code;
  }
}
