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

package com.google.android.gms.shadows.common.internal;

import android.os.Handler;
import com.google.android.gms.common.internal.Preconditions;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow for {@link Preconditions} that disables threading checks. Since Robolectric fakes various
 * threading constructs, these would otherwise cause tests to fail.
 */
@Implements(Preconditions.class)
public class ShadowPreconditions {
  @Implementation
  public static void checkNotMainThread() {
    // Do nothing
  }

  @Implementation
  public static void checkNotMainThread(String errorMessage) {
    // Do nothing
  }

  @Implementation
  public static void checkHandlerThread(Handler handler) {
    // Do nothing
  }

  @Implementation
  public static void checkHandlerThread(Handler handler, String errorMessage) {
    // Do nothing
  }
}
