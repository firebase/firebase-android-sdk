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

package com.google.firebase.segmentation;

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseException;

/** The class for all Exceptions thrown by {@link FirebaseSegmentation}. */
public class SetCustomInstallationIdException extends FirebaseException {

  public enum Status {
    UNKNOWN(0),

    /** Error in Firebase SDK. */
    CLIENT_ERROR(1),

    /** Error when calling Firebase segmentation backend. */
    BACKEND_ERROR(2),

    /** The given custom installation is already tied to another Firebase installation. */
    DUPLICATED_CUSTOM_INSTALLATION_ID(3);

    private final int value;

    Status(int value) {
      this.value = value;
    }
  }

  @NonNull private final Status status;

  SetCustomInstallationIdException(@NonNull Status status) {
    this.status = status;
  }

  SetCustomInstallationIdException(@NonNull Status status, @NonNull String message) {
    super(message);
    this.status = status;
  }

  SetCustomInstallationIdException(
      @NonNull Status status, @NonNull String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  /**
   * Gets the status code for the operation that failed.
   *
   * @return the code for the SetCustomInstallationIdException
   */
  @NonNull
  public Status getStatus() {
    return status;
  }
}
