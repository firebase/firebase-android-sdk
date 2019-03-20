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

import android.util.Log;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.idling.CountingIdlingResource;
import com.google.android.gms.tasks.RuntimeExecutionException;
import com.google.android.gms.tasks.Task;

/**
 * Smoke test utilities in the test APK.
 *
 * <p>The purpose of this class is to avoid adding testing libraries to the application APK that
 * contains Firebase code and will be obfuscated. The {@code TestUtil} class in the application APK
 * calls the implementations in this class reflectively.
 */
public final class TestApkUtil {

  private static final String TAG = "TestApkUtil";

  private TestApkUtil() {}

  /**
   * Waits for the task to complete successfully.
   *
   * <p>This method will block the current thread and return the result of the task. It will rethrow
   * any expection thrown by the task without wrapping.
   */
  public static <T> T waitForSuccess(Task<T> task) throws Exception {
    Log.d(TAG, "Begin waiting for task, " + task + ".");
    CountingIdlingResource idler = new CountingIdlingResource("Task");

    idler.increment();
    IdlingRegistry.getInstance().register(idler);
    task.addOnCompleteListener(
        t -> {
          Log.d(TAG, "Task completed. Success: " + t.isSuccessful() + ".");
          idler.decrement();
        });

    Log.d(TAG, "Wait now.");
    Espresso.onIdle();
    Log.d(TAG, "End wait.");

    IdlingRegistry.getInstance().unregister(idler);
    try {
      return task.getResult();
    } catch (RuntimeExecutionException ex) {
      Throwable t = ex.getCause();
      if (t instanceof Exception) {
        throw (Exception) t;
      } else if (t instanceof Error) {
        throw (Error) t;
      }

      throw new IllegalStateException("Task threw unexpected Throwable", t);
    }
  }
}
