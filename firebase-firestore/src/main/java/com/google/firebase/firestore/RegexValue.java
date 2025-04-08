// Copyright 2025 Google LLC
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

package com.google.firebase.firestore;

import androidx.annotation.NonNull;

/** Represents a regular expression type in Firestore documents. */
public final class RegexValue {
  public final String pattern;
  public final String options;

  public RegexValue(@NonNull String pattern, @NonNull String options) {
    this.pattern = pattern;
    this.options = options;
  }

  /**
   * Returns true if this RegexValue is equal to the provided object.
   *
   * @param obj The object to compare against.
   * @return Whether this RegexValue is equal to the provided object.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof RegexValue)) {
      return false;
    }
    RegexValue other = (RegexValue) obj;
    return pattern.equals(other.pattern) && options.equals(other.options);
  }

  @Override
  public int hashCode() {
    return 31 * pattern.hashCode() + options.hashCode();
  }

  @Override
  public String toString() {
    return "RegexValue{pattern='" + pattern + "', options='" + options + "'}";
  }
}
