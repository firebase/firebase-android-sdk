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

package com.google.firebase.installations;

import com.google.firebase.installations.local.PersistedInstallationEntry;

interface StateListener {
  /**
   * Returns {@code true} if the defined {@link PersistedInstallationEntry} state is reached, {@code
   * false} otherwise.
   */
  boolean onStateReached(PersistedInstallationEntry persistedInstallationEntry);

  /**
   * Returns {@code true} if an exception is thrown while registering a Firebase Installation,
   * {@code false} otherwise.
   */
  boolean onException(Exception exception);
}
