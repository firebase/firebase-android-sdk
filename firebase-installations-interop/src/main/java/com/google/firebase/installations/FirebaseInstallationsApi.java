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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import com.google.android.gms.tasks.Task;
import java.lang.annotation.Retention;

/**
 * This is an interface of {@code FirebaseInstallations} that is only exposed to 2p via component
 * injection.
 *
 * @hide
 */
public interface FirebaseInstallationsApi {

  /** Specifies the options to get a FIS AuthToken. */
  @IntDef({DO_NOT_FORCE_REFRESH, FORCE_REFRESH})
  @Retention(SOURCE)
  @interface AuthTokenOption {}
  /**
   * AuthToken is not refreshed until requested by the developer or if one doesn't exist, is expired
   * or about to expire.
   */
  int DO_NOT_FORCE_REFRESH = 0;
  /**
   * AuthToken is forcefully refreshed on calling the {@link
   * FirebaseInstallationsApi#getAuthToken(int)}.
   */
  int FORCE_REFRESH = 1;

  /**
   * Async function that returns a globally unique identifier of this Firebase app installation.
   * This is a url-safe base64 string of a 128-bit integer.
   */
  Task<String> getId();

  /** Async function that returns a auth token(public key) of this Firebase app installation. */
  Task<InstallationTokenResult> getAuthToken(@AuthTokenOption int authTokenOption);

  /**
   * Async function that deletes this Firebase app installation from Firebase backend. This call
   * would possibly lead Firebase Notification, Firebase RemoteConfig, Firebase Predictions or
   * Firebase In-App Messaging not function properly.
   */
  Task<Void> delete();
}
