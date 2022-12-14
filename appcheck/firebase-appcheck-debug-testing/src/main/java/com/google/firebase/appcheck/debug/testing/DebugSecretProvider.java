// Copyright 2022 Google LLC
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

package com.google.firebase.appcheck.debug.testing;

import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.appcheck.debug.InternalDebugSecretProvider;

/** @hide */
public class DebugSecretProvider implements InternalDebugSecretProvider {
  private static final String DEBUG_SECRET_KEY = "firebaseAppCheckDebugSecret";

  DebugSecretProvider() {}

  /**
   * Returns a debug secret from {@link InstrumentationRegistry} arguments to be used with the
   * {@link com.google.firebase.appcheck.debug.internal.DebugAppCheckProvider} in continuous
   * integration testing flows.
   */
  @Override
  public String getDebugSecret() {
    return InstrumentationRegistry.getArguments().getString(DEBUG_SECRET_KEY);
  }
}
