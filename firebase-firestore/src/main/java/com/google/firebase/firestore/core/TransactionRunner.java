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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.common.base.Function;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.remote.Datastore;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.firebase.firestore.util.ExponentialBackoff;

/**
 * TransactionRunner encapsulates the logic needed to run and retry transactions so that the caller
 * does not have to manage the backoff and retry count through recursive calls.
 */
public class TransactionRunner<TResult> {
  private AsyncQueue asyncQueue;
  private RemoteStore remoteStore;
  private Function<Transaction, Task<TResult>> updateFunction;
  private int retries;

  private ExponentialBackoff backoff;
  private TaskCompletionSource<TResult> taskSource = new TaskCompletionSource<>();

  public TransactionRunner(
      AsyncQueue asyncQueue,
      RemoteStore remoteStore,
      Function<Transaction, Task<TResult>> updateFunction,
      int retries) {
    hardAssert(retries >= 0, "Got negative number of retries for transaction.");

    this.asyncQueue = asyncQueue;
    this.remoteStore = remoteStore;
    this.updateFunction = updateFunction;
    this.retries = retries;

    backoff = new ExponentialBackoff(asyncQueue, TimerId.RETRY_TRANSACTION);
  }

  /** Runs the transaction and sets the result in taskSource. */
  public void runTransaction() {
    backoff.backoffAndRun(
        () -> {
          final Transaction transaction = remoteStore.createTransaction();
          updateFunction
              .apply(transaction)
              .addOnCompleteListener(
                  asyncQueue.getExecutor(),
                  new OnCompleteListener<TResult>() {
                    @Override
                    public void onComplete(@NonNull Task<TResult> userTask) {
                      if (!userTask.isSuccessful()) {
                        if (retries > 0 && isRetryableTransactionError(userTask.getException())) {
                          retries -= 1;
                          runTransaction();
                        } else {
                          taskSource.setException(userTask.getException());
                        }
                      } else {
                        transaction
                            .commit()
                            .addOnCompleteListener(
                                asyncQueue.getExecutor(),
                                new OnCompleteListener<Void>() {
                                  @Override
                                  public void onComplete(@NonNull Task<Void> commitTask) {
                                    if (commitTask.isSuccessful()) {
                                      taskSource.setResult(userTask.getResult());
                                    } else if (retries > 0
                                        && isRetryableTransactionError(commitTask.getException())) {
                                      retries -= 1;
                                      runTransaction();
                                    } else {
                                      taskSource.setException(commitTask.getException());
                                    }
                                  }
                                });
                      }
                    }
                  });
        });
  }

  /** Returns the result of the transaction after it has been run. */
  public Task<TResult> getTask() {
    return taskSource.getTask();
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
