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

package com.google.firebase.platforminfo;

import androidx.annotation.Nullable;

/**
 * Detects presence of Kotlin stdlib on the classpath.
 *
 * <p>If it is present, it is inferred that the application or its subset is written in Kotlin.
 */
public final class KotlinDetector {
  private KotlinDetector() {}

  /** Returns the version of Kotlin stdlib if found, {@code null} otherwise. */
  @Nullable
  public static String detectVersion() {
    try {
      return kotlin.KotlinVersion.CURRENT.toString();
    } catch (NoClassDefFoundError ex) {
      return null;
    }
  }
}
