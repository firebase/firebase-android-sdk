// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig;

import androidx.annotation.NonNull;

/** Wrapper for a Remote Config parameter value, with methods to get it as different types. */
public interface FirebaseRemoteConfigValue {
  /**
   * Gets the value as a <code>long</code>.
   *
   * @return <code>long</code> representation of this parameter value.
   * @throws IllegalArgumentException If the value cannot be converted to a <code>long</code>.
   */
  long asLong() throws IllegalArgumentException;
  /**
   * Gets the value as a <code>double</code>.
   *
   * @return <code>double</code> representation of this parameter value.
   * @throws IllegalArgumentException If the value cannot be converted to a <code>double</code>.
   */
  double asDouble() throws IllegalArgumentException;

  /**
   * Gets the value as a <code>String</code>.
   *
   * @return <code>String</code> representation of this parameter value.
   */
  @NonNull
  String asString();

  /**
   * Gets the value as a <code>byte[]</code>.
   *
   * @return <code>byte[]</code> representation of this parameter value.
   */
  @NonNull
  byte[] asByteArray();
  /**
   * Gets the value as a <code>boolean</code>.
   *
   * @return <code>boolean</code> representation of this parameter value.
   * @throws IllegalArgumentException If the value cannot be converted to a <code>boolean</code>.
   */
  boolean asBoolean() throws IllegalArgumentException;

  /**
   * Indicates at which source this value came from.
   *
   * @return {@link FirebaseRemoteConfig#VALUE_SOURCE_REMOTE} if the value was retrieved from the
   *     server, {@link FirebaseRemoteConfig#VALUE_SOURCE_DEFAULT} if the value was set as a
   *     default, or {@link FirebaseRemoteConfig#VALUE_SOURCE_STATIC} if no value was found and a
   *     static default value was returned instead.
   */
  int getSource();
}
