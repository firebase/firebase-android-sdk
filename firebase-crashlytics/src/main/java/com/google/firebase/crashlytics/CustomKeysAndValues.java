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

package com.google.firebase.crashlytics;

import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class which handles the storage and conversion to strings of key/value pairs with
 * heterogeneous value types.
 */
public class CustomKeysAndValues {

  final Map<String, String> keysAndValues;

  public static class Builder {

    // Holds the converted pairs of custom keys and values.
    private Map<String, String> keysAndValues = new HashMap<String, String>();

    // Methods to accept keys and values and convert values to strings.

    @NonNull
    public Builder putString(@NonNull String key, @NonNull String value) {
      keysAndValues.put(key, value);
      return this;
    }

    @NonNull
    public Builder putBoolean(@NonNull String key, boolean value) {
      keysAndValues.put(key, Boolean.toString(value));
      return this;
    }

    @NonNull
    public Builder putDouble(@NonNull String key, double value) {
      keysAndValues.put(key, Double.toString(value));
      return this;
    }

    @NonNull
    public Builder putFloat(@NonNull String key, float value) {
      keysAndValues.put(key, Float.toString(value));
      return this;
    }

    @NonNull
    public Builder putLong(@NonNull String key, long value) {
      keysAndValues.put(key, Long.toString(value));
      return this;
    }

    @NonNull
    public Builder putInt(@NonNull String key, int value) {
      keysAndValues.put(key, Integer.toString(value));
      return this;
    }

    @NonNull
    public CustomKeysAndValues build() {
      return new CustomKeysAndValues(this);
    }
  }

  CustomKeysAndValues(@NonNull Builder builder) {
    this.keysAndValues = builder.keysAndValues;
  }
}
