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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DelegatingScheduledExecutorServiceTest {
  private final DelegatingScheduledExecutorService service =
      new DelegatingScheduledExecutorService(
          Executors.newCachedThreadPool(), Executors.newSingleThreadScheduledExecutor());

  @Test
  public void schedule_whenCancelled_shouldCancelUnderlyingFuture() {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean ran = new AtomicBoolean();
    ScheduledFuture<Object> future =
        service.schedule(
            () -> {
              latch.await();
              ran.set(true);
              return null;
            },
            10,
            TimeUnit.SECONDS);
    future.cancel(true);
    latch.countDown();
    assertThat(ran.get()).isFalse();
  }

  @Test
  public void scheduleAtFixedRate_whenRunnableThrows_shouldCancelSchedule()
      throws InterruptedException {
    Semaphore semaphore = new Semaphore(0);
    AtomicLong ran = new AtomicLong();

    ScheduledFuture<?> future =
        service.scheduleAtFixedRate(
            () -> {
              ran.incrementAndGet();
              throw new RuntimeException("fail");
            },
            1,
            1,
            TimeUnit.SECONDS);

    semaphore.release();
    try {
      future.get();
      fail("Expected exception not thrown");
    } catch (ExecutionException ex) {
      assertThat(ex.getCause()).isInstanceOf(RuntimeException.class);
      assertThat(ex.getCause().getMessage()).isEqualTo("fail");
    }
    assertThat(ran.get()).isEqualTo(1);
  }
}
