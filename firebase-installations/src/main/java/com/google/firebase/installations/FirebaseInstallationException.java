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

/** The class for all Exceptions thrown by {@link FirebaseInstallations}. */
public class FirebaseInstallationException extends FirebaseException {

  public enum Code {
    SERVER_ERROR,

    NETWORK_ERROR,

    UNAUTHORIZED,

    CLIENT_ERROR
  }

  @NonNull private final Code code;

  public FirebaseInstallationException(@NonNull Code code) {
    this.code = code;
  }

  public FirebaseInstallationException(@NonNull String message, @NonNull Code code) {
    super(message);
    this.code = code;
  }

  public FirebaseInstallationException(
      @NonNull String message, @NonNull Code code, @NonNull Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  /**
   * Gets the status code for the operation that failed.
   *
   * @return the code for the FirebaseInstallationsException
   */
  @NonNull
  public Code getCode() {
    return code;
  }
}
