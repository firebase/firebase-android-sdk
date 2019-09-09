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

package com.google.firebase.components;

import static com.google.firebase.components.Preconditions.checkNotNull;

import androidx.annotation.NonNull;

/** Holds information about a given {@link ComponentRuntime}. */
public final class ContainerInfo {
  private final String name;

  public ContainerInfo(@NonNull String name) {
    this.name = checkNotNull(name, "Container name cannot be null.");
  }

  /** The name of the runtime. */
  @NonNull
  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ContainerInfo that = (ContainerInfo) o;

    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return "ContainerName{" + "name='" + name + '\'' + '}';
  }
}
