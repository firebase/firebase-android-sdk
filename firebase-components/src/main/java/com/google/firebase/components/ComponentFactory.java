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

/** A factory interface that must be provided when creating a {@link Component}. */
public interface ComponentFactory<T> {
  /**
   * Provided a {@link ComponentContainer}, creates an instance of {@code T}.
   *
   * <p>Note: It is only allowed to request declared dependencies from the container, otherwise the
   * container will throw {@link IllegalArgumentException}.
   */
  T create(ComponentContainer container);
}
