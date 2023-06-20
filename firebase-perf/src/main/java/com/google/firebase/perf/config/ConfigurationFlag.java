// Copyright 2020 Google LLC
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

package com.google.firebase.perf.config;

import androidx.annotation.Nullable;

/**
 * Parent class to be extended for each configuration flag. Provides basic constant fields and
 * common methods for all flags.
 */
abstract class ConfigurationFlag<T> {

  ConfigurationFlag() {}

  /* Default value for this flag. */
  protected abstract T getDefault();

  /* Default value for this flag in the case that RC fetch explicitly failed. */
  protected T getDefaultOnRcFetchFail() {
    // Same as typical default unless overridden.
    return getDefault();
  }

  /* Configuration flag ID for Firebase Remote Config. */
  @Nullable
  String getRemoteConfigFlag() {
    return null;
  }

  /* Configuration flag ID for Device cache. */
  @Nullable
  String getDeviceCacheFlag() {
    return null;
  }

  /* Configuration flag ID for Android Manifest. */
  @Nullable
  String getMetadataFlag() {
    return null;
  }
}
