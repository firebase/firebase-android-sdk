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

import android.content.Context;
import android.content.SharedPreferences;

public class ReleaseIdentifierStorage {

  private static final String RELEASE_IDENTIFIER_PREFERENCES_NAME =
      "FirebaseAppDistributionReleaseIdentifierStorage";

  private final SharedPreferences releaseIdentifierSharedPreferences;

  ReleaseIdentifierStorage(Context applicationContext) {
    this.releaseIdentifierSharedPreferences =
        applicationContext.getSharedPreferences(
            RELEASE_IDENTIFIER_PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  void setCodeHashMap(String internalCodeHash, AppDistributionReleaseInternal newRelease) {
    this.releaseIdentifierSharedPreferences
        .edit()
        .putString(internalCodeHash, newRelease.getCodeHash())
        .apply();
  }

  String getExternalCodeHash(String internalCodeHash) {
    return releaseIdentifierSharedPreferences.getString(internalCodeHash, null);
  }
}
