// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A Firebase Remote Config internal issue caused by an interaction with the Firebase Remote Config
 * server.
 *
 * @author Miraziz Yusupov
 */
public class FirebaseRemoteConfigServerException extends FirebaseRemoteConfigException {
  private final int httpStatusCode;

  /**
   * Creates a Firebase Remote Config server exception with the given message and HTTP status code.
   */
  public FirebaseRemoteConfigServerException(int httpStatusCode, @NonNull String detailMessage) {
    super(detailMessage);
    this.httpStatusCode = httpStatusCode;
  }

  /**
   * Creates a Firebase Remote Config server exception with the given message, HTTP status code and
   */
  public FirebaseRemoteConfigServerException(
      int httpStatusCode, @NonNull String detailMessage, @Nullable Throwable cause) {
    super(detailMessage, cause);
    this.httpStatusCode = httpStatusCode;
  }

  /** Gets the HTTP status code of the failed Firebase Remote Config server operation. */
  public int getHttpStatusCode() {
    return httpStatusCode;
  }
}
