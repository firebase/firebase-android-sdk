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

package com.google.firebase.app.distribution;

/** Enum for possible states during Update, used in UpdateProgress. */
enum UpdateStatus {
  /** Update queued but not started */
  PENDING,

  /** Download in progress */
  DOWNLOADING,

  /** Download completed */
  DOWNLOADED,

  /** Download failed */
  DOWNLOAD_FAILED,

  /** Installation canceled */
  INSTALL_CANCELED,

  /** Installation failed */
  INSTALL_FAILED,

  /** AAB flow (directed to Play) */
  REDIRECTED_TO_PLAY,

  /** Currently on the latest release */
  NEW_RELEASE_NOT_AVAILABLE,

  /** Release check failed before download started */
  NEW_RELEASE_CHECK_FAILED,

  /** Customer canceled the update */
  UPDATE_CANCELED,
}
