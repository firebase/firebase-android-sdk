// Copyright 2024 Google LLC
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

package com.google.firebase.firestore.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.firestore.core.DatabaseInfo;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Supplier;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.ManagedChannelBuilder;
import io.grpc.okhttp.OkHttpChannelBuilder;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class GrpcCallProviderTest {

  private OkHttpChannelBuilder realBuilder;

  @Before
  public void setUp() throws Exception {
    realBuilder = OkHttpChannelBuilder.forTarget("host");

    // Inject overrideChannelBuilderSupplier via reflection
    Field field = GrpcCallProvider.class.getDeclaredField("overrideChannelBuilderSupplier");
    field.setAccessible(true);
    field.set(null, (Supplier<ManagedChannelBuilder<?>>) () -> realBuilder);
  }

  @After
  public void tearDown() throws Exception {
    Field field = GrpcCallProvider.class.getDeclaredField("overrideChannelBuilderSupplier");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Test
  public void configuresChannelFlowControlWindow() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AsyncQueue asyncQueue = new AsyncQueue();
    DatabaseId databaseId = DatabaseId.forProject("project");
    // DatabaseInfo with 512KB flow control window
    DatabaseInfo databaseInfo = new DatabaseInfo(databaseId, "key", "host", true, 512 * 1024);

    GrpcCallProvider grpcCallProvider =
        new GrpcCallProvider(
            asyncQueue, context, databaseInfo, Mockito.mock(CallCredentials.class));

    // Await initialization of the channel task on a background thread
    Field taskField = GrpcCallProvider.class.getDeclaredField("channelTask");
    taskField.setAccessible(true);
    com.google.android.gms.tasks.Task<?> task =
        (com.google.android.gms.tasks.Task<?>) taskField.get(grpcCallProvider);

    Thread thread =
        new Thread(
            () -> {
              try {
                com.google.android.gms.tasks.Tasks.await(task);
              } catch (Exception e) {
                // ignore
              }
            });
    thread.start();
    thread.join();

    // Verify via reflection that the realBuilder's flowControlWindow field is configured with 512KB
    Field flowField = OkHttpChannelBuilder.class.getDeclaredField("flowControlWindow");
    flowField.setAccessible(true);
    int flowControlWindow = (int) flowField.get(realBuilder);

    assertEquals(512 * 1024, flowControlWindow);
  }

  @Test
  public void grpcExecutor_whenCallCancelledDuringDrain_doesNotPanicAsyncQueue() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AsyncQueue asyncQueue = new AsyncQueue();
    DatabaseId databaseId = DatabaseId.forProject("project");
    DatabaseInfo databaseInfo = new DatabaseInfo(databaseId, "key", "host", true, 512 * 1024);

    GrpcCallProvider grpcCallProvider =
        new GrpcCallProvider(
            asyncQueue, context, databaseInfo, Mockito.mock(CallCredentials.class));

    // Await initialization of the channel task on a background thread
    Field taskField = GrpcCallProvider.class.getDeclaredField("channelTask");
    taskField.setAccessible(true);
    com.google.android.gms.tasks.Task<?> task =
        (com.google.android.gms.tasks.Task<?>) taskField.get(grpcCallProvider);

    Thread thread =
        new Thread(
            () -> {
              try {
                com.google.android.gms.tasks.Tasks.await(task);
              } catch (Exception e) {
                // ignore
              }
            });
    thread.start();
    thread.join();

    Field optionsField = GrpcCallProvider.class.getDeclaredField("callOptions");
    optionsField.setAccessible(true);
    CallOptions callOptions = (CallOptions) optionsField.get(grpcCallProvider);
    Executor grpcExecutor = callOptions.getExecutor();

    CountDownLatch drainExecuted = new CountDownLatch(1);
    // Simulate gRPC DelayedClientCall throwing during drain
    Runnable delayedDrainRunnable =
        () -> {
          try {
            throw new IllegalStateException("call was cancelled");
          } finally {
            drainExecuted.countDown();
          }
        };

    grpcExecutor.execute(delayedDrainRunnable);
    assertTrue(drainExecuted.await(5, TimeUnit.SECONDS));

    // Synchronously flush the AsyncQueue to ensure afterExecute() has completed.
    asyncQueue.runSync(() -> {});

    // GuardedGrpcExecutor catches the "call was cancelled" IllegalStateException, logs it,
    // and prevents it from escaping into afterExecute() and tripping AsyncQueue.panic().
    org.robolectric.shadows.ShadowLooper.idleMainLooper();
  }

  @Test
  public void grpcExecutor_whenOtherExceptionDuringDrain_panicsAsyncQueue() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    AsyncQueue asyncQueue = new AsyncQueue();
    DatabaseId databaseId = DatabaseId.forProject("project");
    DatabaseInfo databaseInfo = new DatabaseInfo(databaseId, "key", "host", true, 512 * 1024);

    GrpcCallProvider grpcCallProvider =
        new GrpcCallProvider(
            asyncQueue, context, databaseInfo, Mockito.mock(CallCredentials.class));

    Field taskField = GrpcCallProvider.class.getDeclaredField("channelTask");
    taskField.setAccessible(true);
    com.google.android.gms.tasks.Task<?> task =
        (com.google.android.gms.tasks.Task<?>) taskField.get(grpcCallProvider);

    Thread thread =
        new Thread(
            () -> {
              try {
                com.google.android.gms.tasks.Tasks.await(task);
              } catch (Exception ignored) {
              }
            });
    thread.start();
    thread.join();

    Field optionsField = GrpcCallProvider.class.getDeclaredField("callOptions");
    optionsField.setAccessible(true);
    CallOptions callOptions = (CallOptions) optionsField.get(grpcCallProvider);
    Executor grpcExecutor = callOptions.getExecutor();

    CountDownLatch drainExecuted = new CountDownLatch(1);
    Runnable failingRunnable =
        () -> {
          try {
            throw new IllegalStateException("unrelated internal state error");
          } finally {
            drainExecuted.countDown();
          }
        };

    grpcExecutor.execute(failingRunnable);
    assertTrue(drainExecuted.await(5, TimeUnit.SECONDS));

    org.robolectric.shadows.ShadowLooper shadowLooper =
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper());
    // Await the panic runnable posted to the Main Looper
    long deadline = System.currentTimeMillis() + 5000;
    while (shadowLooper.isIdle() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }

    // Non-cancellation exceptions must rethrow, reaching afterExecute() and tripping
    // AsyncQueue.panic().
    assertThrows(
        RuntimeException.class, () -> org.robolectric.shadows.ShadowLooper.idleMainLooper());
  }
}
