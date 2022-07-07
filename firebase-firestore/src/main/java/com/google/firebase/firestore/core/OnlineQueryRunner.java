package com.google.firebase.firestore.core;

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
    backoff = new ExponentialBackoff(asyncQueue, AsyncQueue.TimerId.RETRY_TRANSACTION);
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
    if (attemptsRemaining > 0) {
      runWithBackoff(query);
    } else {
      taskSource.setException(result.getException());
    }
  }
}
