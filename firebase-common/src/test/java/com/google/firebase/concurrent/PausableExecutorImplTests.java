package com.google.firebase.concurrent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PausableExecutorImplTests {
  private final TestExecutor testExecutor = new TestExecutor();

  private final PausableExecutorImpl executor = new PausableExecutorImpl(false, testExecutor);

  @Test
  public void foo() {
    Runnable runnable1 = mock(Runnable.class);
    Runnable runnable2 = mock(Runnable.class);
    Runnable runnable3 = mock(Runnable.class);

    executor.execute(runnable1);
    executor.execute(runnable2);
    executor.pause();
    executor.execute(runnable3);
    testExecutor.stepAll();

    // assertThat(executor.queue).containsExactly(runnable3);
    verify(runnable1, times(1)).run();
    verify(runnable2, times(1)).run();
    verify(runnable3, never()).run();

    executor.resume();
    testExecutor.stepAll();
    verify(runnable3, times(1)).run();
  }
}
