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

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import java.util.Set;

/** Information about the updated config passed to {@link ConfigUpdateListener#onUpdate}. */
@AutoValue
public abstract class ConfigUpdate {
  @NonNull
  /** @hide */
  public static ConfigUpdate create(@NonNull Set<String> updatedKeys) {
    return new AutoValue_ConfigUpdate(updatedKeys);
  }

  /**
   * Parameter keys whose values have been updated from the currently activated values. Includes
   * keys that are added, deleted, and whose value, value source, or metadata has changed.
   */
  @NonNull
  public abstract Set<String> getUpdatedKeys();
}
