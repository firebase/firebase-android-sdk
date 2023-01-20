// Copyright 2023 Google LLC
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

package com.google.firebase.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class PausableExecutorServiceImpl implements PausableExecutorService {

  private final ExecutorService delegateService;
  private final PausableExecutor pausableDelegate;

  PausableExecutorServiceImpl(boolean paused, ExecutorService delegate) {
    delegateService = delegate;
    pausableDelegate = new PausableExecutorImpl(paused, delegate);
  }

  @Override
  public void execute(Runnable command) {
    pausableDelegate.execute(command);
  }

  @Override
  public void pause() {
    pausableDelegate.pause();
  }

  @Override
  public void resume() {
    pausableDelegate.resume();
  }

  @Override
  public boolean isPaused() {
    return pausableDelegate.isPaused();
  }

  @Override
  public void shutdown() {
    throw new UnsupportedOperationException("Shutting down is not allowed.");
  }

  @Override
  public List<Runnable> shutdownNow() {
    throw new UnsupportedOperationException("Shutting down is not allowed.");
  }

  @Override
  public boolean isShutdown() {
    return delegateService.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegateService.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegateService.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    FutureTask<T> ft = new FutureTask<>(task);
    execute(ft);
    return ft;
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return submit(
        () -> {
          task.run();
          return result;
        });
  }

  @Override
  public Future<?> submit(Runnable task) {
    return submit(
        () -> {
          task.run();
          return null;
        });
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegateService.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return delegateService.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws ExecutionException, InterruptedException {
    return delegateService.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    return delegateService.invokeAny(tasks, timeout, unit);
  }
}
