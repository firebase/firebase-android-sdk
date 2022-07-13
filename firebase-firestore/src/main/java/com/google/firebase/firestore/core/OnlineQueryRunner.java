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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.util.Util.isRetryableBackendError;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.ExponentialBackoff;

public class OnlineQueryRunner {
  private AsyncQueue asyncQueue;
  private RemoteStore remoteStore;

  private int attemptsRemaining = 1;

  private ExponentialBackoff backoff;
  private TaskCompletionSource<Long> taskSource = new TaskCompletionSource<>();

  public OnlineQueryRunner(AsyncQueue asyncQueue, RemoteStore remoteStore) {
    backoff = new ExponentialBackoff(asyncQueue, AsyncQueue.TimerId.RETRY_ONLINE_QUERY);
    this.asyncQueue = asyncQueue;
    this.remoteStore = remoteStore;
  }

  public Task<Long> runCountQuery(Query query) {
    runWithBackoff(query);
    return taskSource.getTask();
  }

  private void runWithBackoff(Query query) {
    attemptsRemaining -= 1;
    backoff.backoffAndRun(
        () -> {
          remoteStore
              .runCountQuery(query)
              .addOnCompleteListener(
                  asyncQueue.getExecutor(),
                  (OnCompleteListener<Long>)
                      task -> {
                        if (task.isSuccessful()) {
                          taskSource.setResult(task.getResult());
                        } else {
                          handleError(task, query);
                        }
                      });
        });
  }

  private void handleError(Task<Long> result, Query query) {
    if (attemptsRemaining > 0 && isRetryableBackendError(result.getException())) {
      runWithBackoff(query);
    } else {
      taskSource.setException(result.getException());
    }
  }
}
