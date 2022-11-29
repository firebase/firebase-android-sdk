// Copyright 2022 Google LLC
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

package com.google.firebase.remoteconfig;

import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Event Listener for Realtime config update callbacks.
 *
 * @author Quan Pham
 */
public interface ConfigUpdateListener {
  /**
   * Callback for when a new config has been automatically fetched from the backend and has changed
   * from the activated config.
   *
   * @param updatedParams The set of parameter keys changed from the currently activated values.
   *     Includes keys that are: added; deleted; and whose value, value source, or metadata has
   *     changed.
   */
  void onUpdate(@Nonnull Set<String> updatedParams);

  /**
   * Callback for when an error occurs while listening for or fetching a config update.
   *
   * @param error
   */
  void onError(@Nonnull FirebaseRemoteConfigException error);
}
