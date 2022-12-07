package com.google.firebase.appdistribution.impl;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.concurrent.Executor;

class SequentialReference<T> {

  interface SequentialReferenceProducer<T> {
    T produce();
  }

  interface SequentialReferenceConsumer<T> {
    void consume(T value);
  }

  interface SequentialReferenceTransformer<T, U> {
    U transform(T value);
  }

  interface SequentialReferenceUpdateTaskTransformer<T> {
    UpdateTask transform(T value);
  }

  private final Executor sequentialExecutor;

  private T value;

  SequentialReference(Executor baseExecutor) {
    this(baseExecutor, null);
  }

  SequentialReference(Executor baseExecutor, T initialValue) {
    sequentialExecutor = FirebaseExecutors.newSequentialExecutor(baseExecutor);
    value = initialValue;
  }

  Task<T> set(SequentialReferenceProducer<T> producer) {
    TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () -> {
          value = producer.produce();
          taskCompletionSource.setResult(value);
        });
    return taskCompletionSource.getTask();
  }

  <U> Task<U> setAndTransform(
      SequentialReferenceProducer<T> producer, SequentialReferenceTransformer<T, U> transformer) {
    TaskCompletionSource<U> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () -> {
          value = producer.produce();
          taskCompletionSource.setResult(transformer.transform(value));
        });
    return taskCompletionSource.getTask();
  }

  void get(SequentialReferenceConsumer<T> consumer) {
    sequentialExecutor.execute(() -> consumer.consume(value));
  }

  UpdateTask getAndTransform(SequentialReferenceUpdateTaskTransformer<T> transformer) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    sequentialExecutor.execute(() -> updateTask.shadow(transformer.transform(value)));
    return updateTask;
  }
}
