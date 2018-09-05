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

package com.google.firebase.firestore.util;

import com.google.android.gms.tasks.TaskExecutors;
import java.util.concurrent.Executor;

/** Helper class for executors. */
public final class Executors {

  /**
   * The default executor for user visible callbacks. It is an executor scheduling callbacks on
   * Android's main thread.
   */
  public static final Executor DEFAULT_CALLBACK_EXECUTOR = TaskExecutors.MAIN_THREAD;

  /** An executor that executes the provided runnable immediately on the current thread. */
  public static final Executor DIRECT_EXECUTOR = Runnable::run;

  private Executors() {
    // Private constructor to prevent initialization
  }
}
