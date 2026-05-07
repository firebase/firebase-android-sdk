// Copyright 2026 Google LLC
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

package com.google.firebase.firestore.remote;

import androidx.annotation.NonNull;

/** Represents a transient, session-specific target ID used strictly over the watch stream. */
public final class RemoteTargetId implements Comparable<RemoteTargetId> {
  private final int value;

  private RemoteTargetId(int value) {
    this.value = value;
  }

  public static RemoteTargetId from(int value) {
    return new RemoteTargetId(value);
  }

  public int value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return value == ((RemoteTargetId) o).value;
  }

  @Override
  public int hashCode() {
    return value;
  }

  @Override
  public int compareTo(@NonNull RemoteTargetId other) {
    return Integer.compare(value, other.value);
  }

  @Override
  public String toString() {
    return "RemoteTargetId(" + value + ")";
  }
}
