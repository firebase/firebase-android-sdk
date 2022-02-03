// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution;

/** Enum for possible states during Update, used in {@link UpdateProgress}. */
public enum UpdateStatus {
  /** The update is queued but not started. */
  PENDING,

  /** The new release download is in progress. */
  DOWNLOADING,

  /** The new release was downloaded successfully. */
  DOWNLOADED,

  /** The new release failed to download. */
  DOWNLOAD_FAILED,

  /** The new release installation was canceled. */
  INSTALL_CANCELED,

  /** The new release installation failed. */
  INSTALL_FAILED,

  /** The tester was redirected to Play to download an {@link BinaryType#AAB} file. */
  REDIRECTED_TO_PLAY,

  /** The tester is currently on the latest release they have access to for the current app. */
  NEW_RELEASE_NOT_AVAILABLE,

  /** The call to {@link FirebaseAppDistribution#checkForNewRelease} failed. */
  NEW_RELEASE_CHECK_FAILED,

  /** The tester canceled the update. */
  UPDATE_CANCELED,
}
