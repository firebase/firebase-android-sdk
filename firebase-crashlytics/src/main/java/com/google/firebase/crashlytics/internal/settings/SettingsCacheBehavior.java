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

package com.google.firebase.crashlytics.internal.settings;

/** Enum defining possible behaviors when looking up settings from cache */
public enum SettingsCacheBehavior {
  /**
   * The normal behavior - tries to return settings from the cache, respecting their expiration
   * date/time.
   */
  USE_CACHE,

  /** Skips the entire initial cache lookup, going straight to fetching settings from the network */
  SKIP_CACHE_LOOKUP,

  /** States that any cached value should be used, regardless of whether it has expired. */
  IGNORE_CACHE_EXPIRATION
}
