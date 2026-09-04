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

/** Represents the Firestore "Min Key" data type. */
public final class MinKey {
  private static final MinKey INSTANCE = new MinKey();

  private MinKey() {}

  @NonNull
  public static MinKey instance() {
    return INSTANCE;
  }

  /**
   * Returns true if this MinKey is equal to the provided object.
   *
   * @param obj The object to compare against.
   * @return Whether this MinKey is equal to the provided object.
   */
  @Override
  public boolean equals(Object obj) {
    return obj == INSTANCE;
  }

  @Override
  public int hashCode() {
    return MinKey.class.getName().hashCode();
  }
}
