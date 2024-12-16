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
 * A container type to represent key/value pairs of heterogeneous types to be set as custom signals
 * in {@link FirebaseRemoteConfig#setCustomSignals}.
 */
public class CustomSignals {
  final Map<String, String> customSignals;

  /** Builder for constructing {@link CustomSignals} instances. */
  public static class Builder {
    private Map<String, String> customSignals = new HashMap<String, String>();

    /**
     * Adds a custom signal with a value that can be a string or null to the builder.
     *
     * @param key The key for the custom signal.
     * @param value The string value associated with the key. Can be null.
     * @return This Builder instance to allow chaining of method calls.
     */
    @NonNull
    public Builder put(@NonNull String key, @Nullable String value) {
      customSignals.put(key, value);
      return this;
    }

    /**
     * Adds a custom signal with a long value to the builder.
     *
     * @param key The key for the custom signal.
     * @param value The long value for the custom signal.
     * @return This Builder instance to allow chaining of method calls.
     */
    @NonNull
    public Builder put(@NonNull String key, long value) {
      customSignals.put(key, Long.toString(value));
      return this;
    }

    /**
     * Adds a custom signal with a double value to the builder.
     *
     * @param key The key for the custom signal.
     * @param value The double value for the custom signal.
     * @return This Builder instance to allow chaining of method calls.
     */
    @NonNull
    public Builder put(@NonNull String key, double value) {
      customSignals.put(key, Double.toString(value));
      return this;
    }

    /**
     * Creates a {@link CustomSignals} instance with the added custom signals.
     *
     * @return The constructed {@link CustomSignals} instance.
     */
    @NonNull
    public CustomSignals build() {
      return new CustomSignals(this);
    }
  }

  CustomSignals(@NonNull Builder builder) {
    this.customSignals = builder.customSignals;
  }
}
