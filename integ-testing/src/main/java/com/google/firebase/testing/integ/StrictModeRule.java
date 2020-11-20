// Copyright 2020 Google LLC
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

package com.google.firebase.testing.integ;

import android.os.Build;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import androidx.test.internal.runner.junit4.statement.UiThreadStatement;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

/**
 * This rule enables {@link StrictMode} on the <a
 * href="https://developer.android.com/guide/components/processes-and-threads#Threads">Main
 * thread</a>.
 *
 * <p>Just adding it as a {@link @Rule} to your test is enough to enable it.
 *
 * <p>Note however that the tests don't run on the Main thread by default, so if you expect the code
 * under test to run in the Main thread in production, please use the provided {@link
 * #runOnMainThread(MaybeThrowingRunnable)} to execute it on the Main thread.
 *
 * <p>Example use:
 *
 * <pre>{@code
 * @Test
 * public class MyTests {
 *   @Rule public StrictModeRule strictMode = new StrictModeRule();
 *
 *  @Test public void myTest() {
 *    // runs on the instrumentation thread
 *    runMyCode();
 *
 *    // runs on Main thread.
 *    strictMode.runOnMainThread(() -> {
 *      runCodeOnMainThread();
 *    });
 *  }
 * }
 * }</pre>
 */
public class StrictModeRule implements TestRule {

  private static final Executor penaltyListenerExecutor = Runnable::run;

  /** Runs {@code runnable} on Main thread. */
  public <E extends Throwable> void runOnMainThread(MaybeThrowingRunnable<E> runnable) throws E {
    try {
      new UiThreadStatement(
              new Statement() {
                @Override
                public void evaluate() throws E {
                  runnable.run();
                }
              },
              true)
          .evaluate();
    } catch (Throwable throwable) {
      @SuppressWarnings("unchecked")
      E e = (E) throwable;
      throw e;
    }
  }

  /** Runs {@code callable} on Main thread and returns it result. */
  public <T, E extends Throwable> T runOnMainThread(MaybeThrowingCallable<T, E> callable) throws E {
    try {
      AtomicReference<T> result = new AtomicReference<>();
      new UiThreadStatement(
              new Statement() {
                @Override
                public void evaluate() throws E {
                  result.set(callable.call());
                }
              },
              true)
          .evaluate();
      return result.get();
    } catch (Throwable throwable) {
      @SuppressWarnings("unchecked")
      E e = (E) throwable;
      throw e;
    }
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        AtomicReference<ThreadPolicy> originalThreadPolicy = new AtomicReference<>();
        VmPolicy originalVmPolicy = StrictMode.getVmPolicy();

        ConcurrentLinkedQueue<Throwable> violations = new ConcurrentLinkedQueue<>();

        InstrumentationRegistry.getInstrumentation()
            .runOnMainSync(
                () -> {
                  originalThreadPolicy.set(StrictMode.getThreadPolicy());

                  StrictMode.setThreadPolicy(createThreadPolicy(violations));
                  StrictMode.setVmPolicy(createVmPolicy(violations));
                });
        try {
          base.evaluate();
        } catch (Throwable e) {
          violations.add(e);
        } finally {
          InstrumentationRegistry.getInstrumentation()
              .runOnMainSync(() -> StrictMode.setThreadPolicy(originalThreadPolicy.get()));
          // Make sure GC happens, so that the VM policy can detect unclosed resources.
          runGc();
          StrictMode.setVmPolicy(originalVmPolicy);
        }
        MultipleFailureException.assertEmpty(new ArrayList<>(violations));
      }
    };
  }

  private static ThreadPolicy createThreadPolicy(Collection<Throwable> violations) {
    ThreadPolicy.Builder builder = new ThreadPolicy.Builder().detectAll();
    if (Build.VERSION.SDK_INT >= 28) {
      builder.penaltyListener(penaltyListenerExecutor, violations::add);
    } else {
      builder.penaltyDeath();
    }
    return builder.build();
  }

  private static VmPolicy createVmPolicy(Collection<Throwable> violations) {
    VmPolicy.Builder builder = new VmPolicy.Builder().detectAll();
    if (Build.VERSION.SDK_INT >= 28) {
      builder.penaltyListener(penaltyListenerExecutor, violations::add);
    } else {
      builder.penaltyDeath();
    }
    return builder.build();
  }

  private static void runGc() {
    Runtime.getRuntime().gc();
    Runtime.getRuntime().runFinalization();
    Runtime.getRuntime().gc();
    Runtime.getRuntime().runFinalization();
  }
}
