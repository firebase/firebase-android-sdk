// Copyright 2018 Google LLC
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

package com.google.firebase.components;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.inject.Provider;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LazyTest {

  @SuppressWarnings("unchecked")
  private final Provider<Object> mockProvider = mock(Provider.class);

  @Test
  public void get_whenLazyIsInitializedWithValue_shouldReturnTheValue() {
    Object instance = new Object();
    Lazy<Object> lazy = new Lazy<>(instance);

    assertThat(lazy.get()).isSameInstanceAs(instance);
  }

  @Test
  public void get_shouldDelegateToFactory() {
    Object instance = new Object();
    Lazy<Object> lazy = new Lazy<>(mockProvider);

    when(mockProvider.get()).thenReturn(instance);

    assertThat(lazy.get()).isSameInstanceAs(instance);

    verify(mockProvider, times(1)).get();
  }

  @Test
  public void get_shouldBeThreadSafe() throws Exception {
    int numThreads = 10;
    CountDownLatch latch = new CountDownLatch(numThreads);

    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    LatchedProvider provider = new LatchedProvider(latch);
    Lazy<Object> lazy = new Lazy<>(provider);

    List<Callable<Object>> tasks = new ArrayList<>(numThreads);
    for (int i = 0; i < numThreads; i++) {
      tasks.add(
          () -> {
            latch.countDown();
            return lazy.get();
          });
    }
    List<Future<Object>> futures = executor.invokeAll(tasks);

    assertThat(provider.instantiationCount.get()).isEqualTo(1);

    Set<Object> createdInstances = new HashSet<>();
    for (Future<Object> future : futures) {
      createdInstances.add(future.get());
    }
    assertThat(createdInstances).hasSize(1);
  }

  private static class LatchedProvider implements Provider<Object> {
    private final CountDownLatch latch;
    final AtomicInteger instantiationCount = new AtomicInteger();

    LatchedProvider(CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public Object get() {
      // wait for all threads to start and get as close to calling Lazy#get() as possible.
      uninterruptablyAwait(latch);
      instantiationCount.incrementAndGet();
      return new Object();
    }

    private static void uninterruptablyAwait(CountDownLatch latch) {
      boolean interrupted = false;
      try {
        while (true) {
          try {
            latch.await();
            return;
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }
}
