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

package com.google.firebase.internal;

import androidx.annotation.VisibleForTesting;

/**
 * A simple mechanism to detect if the host app is obfuscated.
 *
 * <p>The approach is to compare the source class name with the runtime class name, if they don't
 * match the app is obfuscated. The reverse is not necessarily true, i.e. if they match, the host
 * app could be configured in such a way that this class is not obfuscated, but it's unlikely to
 * happen.
 */
public final class ObfuscationUtils {
  private static final String NAME = "ObfuscationUtils";
  private static final boolean APP_OBFUSCATED =
      !isEqual(NAME, ObfuscationUtils.class.getSimpleName());

  private ObfuscationUtils() {}

  /**
   * Custom implementation of string equality to reduce the likelihood of r8 optimizing this check
   * away into a constant true or false, defeating the purpose of this check.
   */
  @VisibleForTesting
  static boolean isEqual(String one, String other) {
    if (one.length() != other.length()) {
      return false;
    }
    for (int i = 0; i < one.length(); i++) {
      if (one.charAt(i) != other.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  public static boolean isAppObfuscated() {
    return APP_OBFUSCATED;
  }
}
