// Copyright 2024 Google LLC
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

package com.google.firebase.remoteconfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class which handles the storage and conversion to strings of key/value pairs with
 * heterogeneous value types for custom signals.
 */
public class CustomSignals {

  final Map<String, String> customSignals;

  public static class Builder {
    // Holds the converted pairs of custom keys and values.
    private Map<String, String> customSignals = new HashMap<String, String>();

    // Methods to accept keys and values and convert values to strings.
    @NonNull
    public Builder put(@NonNull String key, @Nullable String value) {
      customSignals.put(key, value);
      return this;
    }

    @NonNull
    public Builder put(@NonNull String key, long value) {
      customSignals.put(key, Long.toString(value));
      return this;
    }

    @NonNull
    public Builder put(@NonNull String key, double value) {
      customSignals.put(key, Double.toString(value));
      return this;
    }

    @NonNull
    public CustomSignals build() {
      return new CustomSignals(this);
    }
  }

  CustomSignals(@NonNull Builder builder) {
    this.customSignals = builder.customSignals;
  }
}
