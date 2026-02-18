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

package com.google.firebase.crashlytics.internal.common;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import org.junit.Test;

public class ExecutorUtilsTest extends CrashlyticsTestCase {

  private static final String THREAD_FACTORY_NAME = "TestThreadFactory";
  private static final String FIRST_THREAD_NAME = THREAD_FACTORY_NAME + "1";

  @Test
  public void testBuildSingleThreadExecutorService() throws Exception {
    final ExecutorService service =
        ExecutorUtils.buildSingleThreadExecutorService(THREAD_FACTORY_NAME);
    final Future<String> future = service.submit(new ThreadNameCallable());
    assertEquals(FIRST_THREAD_NAME, future.get());
  }

  @Test
  public void testBuildSingleThreadScheduledExecutorService() throws Exception {
    final ExecutorService service =
        ExecutorUtils.buildSingleThreadScheduledExecutorService(THREAD_FACTORY_NAME);
    final Future<String> future = service.submit(new ThreadNameCallable());
    assertEquals(FIRST_THREAD_NAME, future.get());
  }

  @Test
  public void testGetNamedThreadFactory() throws Exception {
    verifyGetNamedThreadFactory(THREAD_FACTORY_NAME);
  }

  @Test
  public void testGetNamedThreadFactory_nullName() throws Exception {
    verifyGetNamedThreadFactory(null);
  }

  private void verifyGetNamedThreadFactory(String threadFactorName) {
    final ThreadFactory threadFactory = ExecutorUtils.getNamedThreadFactory(threadFactorName);
    Thread thread;
    for (int i = 0; i < 2; i++) {
      thread = threadFactory.newThread(mock(Runnable.class));
      // Thread identifier starts from 1
      assertEquals(threadFactorName + (i + 1), thread.getName());
    }
  }

  private static class ThreadNameCallable implements Callable<String> {
    @Override
    public String call() throws Exception {
      return Thread.currentThread().getName();
    }
  }
}
