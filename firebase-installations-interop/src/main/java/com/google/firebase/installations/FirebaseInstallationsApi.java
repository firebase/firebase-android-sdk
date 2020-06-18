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
import com.google.android.gms.tasks.Task;

/**
 * This is an interface of {@code FirebaseInstallations} that is only exposed to 2p via component
 * injection.
 *
 * @hide
 */
public interface FirebaseInstallationsApi {

  /**
   * Async function that returns a globally unique identifier of this Firebase app installation.
   * This is a url-safe base64 string of a 128-bit integer.
   */
  @NonNull
  Task<String> getId();

  /**
   * Async function that returns a auth token(public key) of this Firebase app installation.
   *
   * @param forceRefresh If set to TRUE this method will trigger a server request to generate new
   *     AuthToken every time when called. Otherwise it will return a locally cached token if
   *     available and valid. It is strongly recommended to set this field to FALSE unless a
   *     previously returned token turned out to be expired (which in itself is an indicator for
   *     time synchronization issues).
   */
  @NonNull
  Task<InstallationTokenResult> getToken(boolean forceRefresh);

  /**
   * Async function that deletes this Firebase app installation from Firebase backend. This call
   * would possibly lead Firebase Notification, Firebase RemoteConfig, Firebase Predictions or
   * Firebase In-App Messaging not function properly.
   */
  @NonNull
  Task<Void> delete();
}
