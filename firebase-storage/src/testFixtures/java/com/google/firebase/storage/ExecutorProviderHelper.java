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

package com.google.firebase.storage;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Looper;
import com.google.android.gms.tasks.TaskExecutors;
import java.util.concurrent.Executor;

/** ExecutorProviderHelper */
public class ExecutorProviderHelper {
  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public static Executor getInstance() {
    if (Looper.myLooper() == Looper.getMainLooper() && Looper.getMainLooper() != null) {
      return TaskExecutors.MAIN_THREAD;
    }

    return AsyncTask.THREAD_POOL_EXECUTOR;
  }
}
