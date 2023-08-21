/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.remoteconfig.interop;

import androidx.annotation.NonNull;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsStateSubscriber;

public interface FirebaseRemoteConfigInterop {
  /**
   * Register a {@link RolloutsStateSubscriber} {@code subscriber} whose callback {@code
   * onRolloutsStateChanged} is invoked when the set of assigned rollouts changes for the given
   * Remote Config {@code namespace}.
   *
   * @param namespace The Remote Config namespace for which {@code subscriber} should be registered.
   *     A Firebase project can have multiple Remote Configs, each belonging to a unique namespace,
   *     ex. "firebase" for the default configuration.
   * @param subscriber An implementation of {@link RolloutsStateSubscriber}.
   */
  void registerRolloutsStateSubscriber(
      @NonNull String namespace, @NonNull RolloutsStateSubscriber subscriber);
}
