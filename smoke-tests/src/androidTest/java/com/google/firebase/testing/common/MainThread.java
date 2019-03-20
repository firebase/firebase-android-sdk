// Copyright 2018 Google LLC
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

package com.google.firebase.testing.common;

import android.os.Handler;
import android.os.Looper;

/**
 * Convenience class for interacting with Android.
 *
 * <p>For now, this only consists of the {@link #run} method.
 */
public final class MainThread {

  private static Handler handler = null;

  private MainThread() {}

  /** Runs the {@link Runnable} on the main thread. */
  public static void run(Runnable r) throws InterruptedException {
    if (handler == null) {
      handler = new Handler(Looper.getMainLooper());
    }

    handler.post(r);
  }
}
