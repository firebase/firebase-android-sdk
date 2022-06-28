package com.google.firebase.firestore.core;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.AggregateQuery;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.ExponentialBackoff;

public class OnlineQueryRunner<TResult> {
  private AsyncQueue asyncQueue;
  private RemoteStore remoteStore;

  private int attemptsRemaining = 1;

  private ExponentialBackoff backoff;
  private TaskCompletionSource<TResult> taskSource = new TaskCompletionSource<>();

  public OnlineQueryRunner(AsyncQueue asyncQueue, RemoteStore remoteStore) {
    backoff = new ExponentialBackoff(asyncQueue, AsyncQueue.TimerId.RETRY_TRANSACTION);
    this.asyncQueue = asyncQueue;
    this.remoteStore = remoteStore;
  }

  public Task<TResult> run(AggregateQuery query) {
    runWithBackoff(query);
    return taskSource.getTask();
  }

  private void runWithBackoff(AggregateQuery query) {
    attemptsRemaining -= 1;
    backoff.backoffAndRun(
      () -> {
        remoteStore.getDatastore().run(query);
      });
  }

}
