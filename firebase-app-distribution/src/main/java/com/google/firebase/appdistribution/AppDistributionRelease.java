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

import androidx.annotation.NonNull;

public interface AppDistributionRelease {
  // The short bundle version of this build (example 1.0.0)
  @NonNull
  public String getDisplayVersion();

  // The bundle version of this build (example: 123)
  @NonNull
  public String getBuildVersion();

  // The release notes for this build
  @NonNull
  public String getReleaseNotes();

  // The binary type for this build
  @NonNull
  public BinaryType getBinaryType();
}
