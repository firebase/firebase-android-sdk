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

package com.google.firebase.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class DelegatingScheduledExecutorService implements ScheduledExecutorService {
  private final ExecutorService delegate;
  private final ScheduledExecutorService scheduler;

  DelegatingScheduledExecutorService(ExecutorService delegate, ScheduledExecutorService scheduler) {
    this.delegate = delegate;
    this.scheduler = scheduler;
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
    return delegate.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return delegate.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return delegate.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return delegate.submit(task);
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return delegate.submit(task, result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return delegate.submit(task);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return delegate.invokeAll(tasks);
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return delegate.invokeAll(tasks, timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws ExecutionException, InterruptedException {
    return delegate.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws ExecutionException, InterruptedException, TimeoutException {
    return delegate.invokeAny(tasks, timeout, unit);
  }

  @Override
  public void execute(Runnable command) {
    delegate.execute(command);
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return new DelegatingScheduledFuture<Void>(
        completer ->
            scheduler.schedule(
                () ->
                    delegate.execute(
                        () -> {
                          try {
                            command.run();
                            completer.set(null);
                          } catch (Exception ex) {
                            completer.setException(ex);
                          }
                        }),
                delay,
                unit));
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return new DelegatingScheduledFuture<>(
        completer ->
            scheduler.schedule(
                () ->
                    delegate.submit(
                        () -> {
                          try {
                            V result = callable.call();
                            completer.set(result);
                          } catch (Exception ex) {
                            completer.setException(ex);
                          }
                        }),
                delay,
                unit));
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    return new DelegatingScheduledFuture<>(
        completer ->
            scheduler.scheduleAtFixedRate(
                () ->
                    delegate.execute(
                        () -> {
                          try {
                            command.run();
                          } catch (Exception ex) {
                            completer.setException(ex);
                            throw ex;
                          }
                        }),
                initialDelay,
                period,
                unit));
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return new DelegatingScheduledFuture<>(
        completer ->
            scheduler.scheduleWithFixedDelay(
                () ->
                    delegate.execute(
                        () -> {
                          try {
                            command.run();
                          } catch (Exception ex) {
                            completer.setException(ex);
                          }
                        }),
                initialDelay,
                delay,
                unit));
  }
}
