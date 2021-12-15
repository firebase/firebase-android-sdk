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

package com.google.firebase.app.distribution;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.app.distribution.internal.LogWrapper;

class TaskUtils {
  private static final String TAG = "TaskUtils:";

  static <TResult> Task<TResult> handleTaskFailure(
      Task<TResult> task,
      String defaultErrorMessage,
      FirebaseAppDistributionException.Status defaultErrorStatus) {
    if (task.isComplete() && !task.isSuccessful()) {
      Exception e = task.getException();
      LogWrapper.getInstance().e(TAG + "Task failed to complete due to " + e.getMessage(), e);
      if (e instanceof FirebaseAppDistributionException) {
        return task;
      }
      return Tasks.forException(
          new FirebaseAppDistributionException(defaultErrorMessage, defaultErrorStatus, e));
    }
    return task;
  }

  static void safeSetTaskException(TaskCompletionSource taskCompletionSource, Exception e) {
    if (taskCompletionSource != null && !taskCompletionSource.getTask().isComplete()) {
      taskCompletionSource.setException(e);
    }
  }

  static void safeSetTaskException(UpdateTaskImpl task, Exception e) {
    if (task != null && !task.isComplete()) {
      task.setException(e);
    }
  }

  static <TResult> void safeSetTaskResult(
      TaskCompletionSource taskCompletionSource, TResult result) {
    if (taskCompletionSource != null && !taskCompletionSource.getTask().isComplete()) {
      taskCompletionSource.setResult(result);
    }
  }

  static void safeSetTaskResult(UpdateTaskImpl task) {
    if (task != null && !task.isComplete()) {
      task.setResult();
    }
  }
}
