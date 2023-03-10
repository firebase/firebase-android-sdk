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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LimitedConcurrencyExecutorTests {
  private final TestExecutor testExecutor = new TestExecutor();

  private final LimitedConcurrencyExecutor executor =
      new LimitedConcurrencyExecutor(testExecutor, 2);

  @Test
  public void execute_doesNotExecuteTasks_withConcurrencyMoreThanSpecified() {
    Runnable runnable1 = mock(Runnable.class);
    Runnable runnable2 = mock(Runnable.class);
    Runnable runnable3 = mock(Runnable.class);

    executor.execute(runnable1);
    executor.execute(runnable2);
    executor.execute(runnable3);

    assertThat(testExecutor.queue).hasSize(2);
    testExecutor.step();
    verify(runnable1, times(1)).run();
    verify(runnable2, never()).run();
    verify(runnable3, never()).run();

    assertThat(testExecutor.queue).hasSize(2);

    testExecutor.step();
    testExecutor.step();
    verify(runnable2, times(1)).run();
    verify(runnable3, times(1)).run();
  }
}
