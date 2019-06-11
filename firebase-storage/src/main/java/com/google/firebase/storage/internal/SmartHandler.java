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

package com.google.firebase.storage.internal;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.storage.StorageTaskScheduler;
import java.util.concurrent.Executor;

/**
 * A slightly better version of Handler that uses the threadpool if there is no looper.
 *
 * @hide
 */
@SuppressWarnings("JavaDoc")
public class SmartHandler {
  private final Handler handler;
  private final Executor executor;
  /**
   * This works around a deadlock in robolectric (see https://github
   * .com/robolectric/robolectric/issues/2115) The root cause is that even if you do a handler.post
   * with the intention of asynchronously invoking back to the ui thread, robolectric does a
   * synchronous invoke to getcurrentime. This has the effect of causing deadlocks if you happen to
   * be in a situation where you are holding a lock while trying to async/invoke back to the main
   * thread and the main thread is trying to obtain that lock. This flag is for test only and
   * instead of doing a handler.post, it instead uses the threadpool for those callbacks.
   */
  /*package*/ static boolean testMode = false;

  /** Constructs a SmartHandler */
  public SmartHandler(@Nullable Executor executor) {
    this.executor = executor;
    if (this.executor == null) {
      if (!testMode) {
        handler = new Handler(Looper.getMainLooper());
      } else {
        handler = null; // we will call back on the thread pool.
      }
    } else {
      handler = null;
    }
  }

  /**
   * Calls back the runnable on the thread loop when this handler was created. If there was no
   * installed Looper, then the threadpool is used.
   *
   * @param runnable the object to call.
   */
  public void callBack(@NonNull final Runnable runnable) {
    Preconditions.checkNotNull(runnable);
    if (handler == null) {
      if (executor != null) {
        // manually specified executor
        executor.execute(runnable);
      } else {
        StorageTaskScheduler.getInstance().scheduleCallback(runnable);
      }
    } else {
      handler.post(runnable);
    }
  }
}
