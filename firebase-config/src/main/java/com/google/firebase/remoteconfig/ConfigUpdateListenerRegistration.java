// Copyright 2022 Google LLC
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

/**
 * Listener registration returned by {@link
 * com.google.firebase.remoteconfig.FirebaseRemoteConfig#addOnConfigUpdateListener}.
 *
 * <p>Calling {@link ConfigUpdateListenerRegistration#remove()} stops the listener from receiving
 * config updates and unregisters itself. If remove is called and no other listener registrations
 * remain, the connection to the Remote Config backend is closed. Subsequently calling {@link
 * com.google.firebase.remoteconfig.FirebaseRemoteConfig#addOnConfigUpdateListener} will re-open the
 * connection.
 */
public interface ConfigUpdateListenerRegistration {

  /**
   * Removes the listener being tracked by this {@code ConfigUpdateListenerRegistration}. After the
   * initial call, subsequent calls to {@code remove()} have no effect.
   */
  public void remove();
}
