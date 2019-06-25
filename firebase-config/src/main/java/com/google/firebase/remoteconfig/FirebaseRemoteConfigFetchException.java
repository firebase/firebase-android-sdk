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

/**
 * Exception thrown when the {@link FirebaseRemoteConfig#fetch()} operation cannot be completed
 * successfully.
 *
 * @deprecated Use {@link FirebaseRemoteConfigServerException} or {@link
 *     FirebaseRemoteConfigClientException} instead.
 */
@Deprecated
public class FirebaseRemoteConfigFetchException extends FirebaseRemoteConfigException {
  /** Creates a Firebase Remote Config fetch exception with the given message. */
  public FirebaseRemoteConfigFetchException(String detailMessage) {
    super(detailMessage);
  }

  /** Creates a Firebase Remote Config fetch exception with the given message and cause. */
  public FirebaseRemoteConfigFetchException(String detailMessage, Throwable cause) {
    super(detailMessage, cause);
  }
}
