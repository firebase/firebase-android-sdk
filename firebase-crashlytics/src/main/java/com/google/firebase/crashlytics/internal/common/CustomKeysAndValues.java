// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

/**
 * Helper class which handles the accumluation of multiple key/value pairs with
 * heterogenous value types.
 */
public class CustomKeysAndValues {

  static final Map<String, String> keysAndValues;

  static class Builder {

    // Holds the pairs of custom keys and values to associate with the
    // crash data while accumulating adding pairs.
    Map<String, String> keysAndValues = new HashMap<String, String>();

    Builder setValueForKey(@NonNull String key, @NonNull String value) {
      stringValues.put(key, value);
    }
    Builder setValueForKey(@NonNull String key, boolean value) {
      stringValues.put(key, Boolean.toString(value));
    }
    Builder setValueForKey(@NonNull String key, double value) {
      stringValues.put(key, Double.toString(value));
    }
    Builder setValueForKey(@NonNull String key, float value) {
      stringValues.put(key, Float.toString(value));
    }
    Builder setValueForKey(@NonNull String key, long value){
      stringValues.put(key, Long.toString(value));
    }
    Builder setValueForKey(@NonNull String key, int value){
      stringValues.put(key, Integer.toString(value));
    }

    @NonNull
    public CustomKeysAndValues build() {
      return new CustomKeysAndValues(this);
    }
  }

  private CustomKeysAndValues(Builder builder) {
    keysAndValues = builder.keysAndValues;
  }

}
