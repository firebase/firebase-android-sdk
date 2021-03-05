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

package com.google.firebase.database.core;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.database.core.utilities.ExponentialBackoff;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.utilities.Function;

public class TransactionRunner<TResult> {

  private static final int RETRY_COUNT = 5;
  private LogWrapper logger;
  private Repo repo;

  private Function<Transaction, Task<TResult>> updateFunction;
  private TaskCompletionSource<TResult> taskCompletionSource = new TaskCompletionSource<>();

  private ExponentialBackoff backoff;

  TransactionRunner(
      Repo repo, LogWrapper logger, Function<Transaction, Task<TResult>> updateFunction) {
    this.repo = repo;
    this.updateFunction = updateFunction;
    this.logger = logger;
    this.backoff = new ExponentialBackoff(repo.getDefaultRunLoop(), logger);
  }

  public Task<TResult> run() {
    runWithBackoff();
    return taskCompletionSource.getTask();
  }

  public void runWithBackoff() {
    backoff.backoffAndRun(
        () -> {
          // Recall tjhat this `updateFunction` runs on the transaction's default executor.
          Transaction transaction = repo.createTransaction();
          updateFunction
              .apply(transaction)
              .addOnCompleteListener(
                  repo.getDefaultExecutor(),
                  (@NonNull Task<TResult> userTask) -> {
                    if (!userTask.isSuccessful()) {
                      handleTransactionError(userTask);
                    } else {
                      transaction
                          .commit()
                          .addOnCompleteListener(
                              repo.getDefaultExecutor(),
                              (@NonNull Task<Void> commitTask) -> {
                                if (commitTask.isSuccessful()) {
                                  taskCompletionSource.setResult(userTask.getResult());
                                } else {
                                  handleTransactionError(commitTask);
                                }
                              });
                    }
                  });
        });
  }

  private void handleTransactionError(Task task) {
    // TODO(wyszynski): Handle this. Differentiate between `userTask` and `commitTask` error?
  }

  private static boolean isRetryableTransactionError(Exception e) {
    return false;
  }
}
