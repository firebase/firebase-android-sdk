// Copyright 2019 Google LLC
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

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.remote.Datastore;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.firebase.firestore.util.ExponentialBackoff;
import com.google.firebase.firestore.util.Function;

/** TransactionRunner encapsulates the logic needed to run and retry transactions with backoff. */
public class TransactionRunner<TResult> {
  private static final int RETRY_COUNT = 5;
  private AsyncQueue asyncQueue;
  private RemoteStore remoteStore;
  private Function<Transaction, Task<TResult>> updateFunction;
  private int retriesLeft;

  private ExponentialBackoff backoff;
  private TaskCompletionSource<TResult> taskSource = new TaskCompletionSource<>();

  public TransactionRunner(
      AsyncQueue asyncQueue,
      RemoteStore remoteStore,
      Function<Transaction, Task<TResult>> updateFunction) {

    this.asyncQueue = asyncQueue;
    this.remoteStore = remoteStore;
    this.updateFunction = updateFunction;
    this.retriesLeft = RETRY_COUNT;

    backoff = new ExponentialBackoff(asyncQueue, TimerId.RETRY_TRANSACTION);
  }

  /** Runs the transaction and returns a Task containing the result. */
  public Task<TResult> run() {
    runWithBackoff();
    return taskSource.getTask();
  }

  private void runWithBackoff() {
    backoff.backoffAndRun(
        () -> {
          final Transaction transaction = remoteStore.createTransaction();
          updateFunction
              .apply(transaction)
              .addOnCompleteListener(
                  asyncQueue.getExecutor(),
                  (@NonNull Task<TResult> userTask) -> {
                    if (!userTask.isSuccessful()) {
                      handleTransactionError(userTask);
                    } else {
                      transaction
                          .commit()
                          .addOnCompleteListener(
                              asyncQueue.getExecutor(),
                              (@NonNull Task<Void> commitTask) -> {
                                if (commitTask.isSuccessful()) {
                                  taskSource.setResult(userTask.getResult());
                                } else {
                                  handleTransactionError(commitTask);
                                }
                              });
                    }
                  });
        });
  }

  private void handleTransactionError(Task task) {
    if (retriesLeft > 0 && isRetryableTransactionError(task.getException())) {
      retriesLeft -= 1;
      runWithBackoff();
    } else {
      taskSource.setException(task.getException());
    }
  }

  private static boolean isRetryableTransactionError(Exception e) {
    if (e instanceof FirebaseFirestoreException) {
      // In transactions, the backend will fail outdated reads with FAILED_PRECONDITION and
      // non-matching document versions with ABORTED. These errors should be retried.
      FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
      return code == FirebaseFirestoreException.Code.ABORTED
          || code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
          || !Datastore.isPermanentError(((FirebaseFirestoreException) e).getCode());
    }
    return false;
  }
}
