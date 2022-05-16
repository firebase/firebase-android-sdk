// Copyright 2022 Google LLC
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The release information returned by {@link FirebaseAppDistribution#checkForNewRelease} when a new
 * version is available for the signed in tester.
 */
interface AppDistributionRelease {

  /** Returns the short bundle version of this build (example: 1.0.0). */
  @NonNull
  String getDisplayVersion();

  /** Returns the version code of this build (example: 123). */
  @NonNull
  long getVersionCode();

  /** Returns the release notes for this build. */
  @Nullable
  String getReleaseNotes();

  /** Returns the binary type for this build. */
  @NonNull
  BinaryType getBinaryType();
}
