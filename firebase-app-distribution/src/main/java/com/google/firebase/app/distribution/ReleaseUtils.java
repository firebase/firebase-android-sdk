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

class ReleaseUtils {
  static AppDistributionRelease convertToAppDistributionRelease(
      AppDistributionReleaseInternal internalRelease) {
    if (internalRelease == null) {
      return null;
    }
    long versionCode;
    try {
      versionCode = Long.parseLong(internalRelease.getBuildVersion());
    } catch (NumberFormatException e) {
      versionCode = 0;
    }
    return AppDistributionRelease.builder()
        .setVersionCode(versionCode)
        .setDisplayVersion(internalRelease.getDisplayVersion())
        .setReleaseNotes(internalRelease.getReleaseNotes())
        .setBinaryType(internalRelease.getBinaryType())
        .build();
  }
}
