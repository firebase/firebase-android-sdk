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

package com.google.firebase.appcheck.debug;

import androidx.annotation.NonNull;

/**
 * Helper class used by {@link com.google.firebase.appcheck.debug.testing.DebugAppCheckTestHelper}
 * in order to access the package-private {@link DebugAppCheckProviderFactory} constructor that
 * takes in a debug secret.
 *
 * @hide
 */
public class DebugAppCheckProviderFactoryHelper {
  @NonNull
  public static DebugAppCheckProviderFactory createDebugAppCheckProviderFactory(
      @NonNull String debugSecret) {
    return new DebugAppCheckProviderFactory(debugSecret);
  }
}
