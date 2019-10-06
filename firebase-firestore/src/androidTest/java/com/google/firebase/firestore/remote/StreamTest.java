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

package com.google.firebase.firestore.remote;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.Assert.assertThrows;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.auth.EmptyCredentialsProvider;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.Test;
import org.junit.runner.RunWith;

class MockCredentialsProvider extends EmptyCredentialsProvider {
  @Override
  public Task<String> getToken() {
    states.add("getToken");
    return super.getToken();
  }

  @Override
  public void invalidateToken() {
    states.add("invalidateToken");
    super.invalidateToken();
  }

  public List<String> observedStates() {
    return states;
  }

  private final List<String> states = new ArrayList<>();
}

@RunWith(AndroidJUnit4.class)
public class StreamTest {
  /** Single mutation to send to the write stream. */
  private static final List<Mutation> mutations =
      Collections.singletonList(setMutation("foo/bar", map()));

  /** Callback class that invokes a semaphore for each callback. */
  private static class StreamStatusCallback implements WatchStream.Callback, WriteStream.Callback {
    final Semaphore openSemaphore = new Semaphore(0);
    final Semaphore closeSemaphore = new Semaphore(0);
    final Semaphore watchChangeSemaphore = new Semaphore(0);
    final Semaphore handshakeSemaphore = new Semaphore(0);
    final Semaphore responseReceivedSemaphore = new Semaphore(0);

    @Override
    public void onWatchChange(SnapshotVersion snapshotVersion, WatchChange watchChange) {
      watchChangeSemaphore.release();
    }

    @Override
    public void onOpen() {
      openSemaphore.release();
    }

    @Override
    public void onClose(Status status) {
      closeSemaphore.release();
    }

    @Override
    public void onHandshakeComplete() {
      handshakeSemaphore.release();
    }

    @Override
    public void onWriteResponse(
        SnapshotVersion commitVersion, List<MutationResult> mutationResults) {
      responseReceivedSemaphore.release();
    }
  }

  /** Creates a WriteStream and gets it in a state that accepts mutations. */
  private WriteStream createAndOpenWriteStream(
      AsyncQueue testQueue, StreamStatusCallback callback) {
    Datastore datastore =
        new Datastore(
            IntegrationTestUtil.testEnvDatabaseInfo(),
            testQueue,
            new EmptyCredentialsProvider(),
            ApplicationProvider.getApplicationContext(),
            null);
    final WriteStream writeStream = datastore.createWriteStream(callback);
    waitForWriteStreamOpen(testQueue, writeStream, callback);
    return writeStream;
  }

  /** Waits for a WriteStream to get into a state that accepts mutations. */
  private void waitForWriteStreamOpen(
      AsyncQueue testQueue, WriteStream writeStream, StreamStatusCallback callback) {
    testQueue.enqueueAndForget(writeStream::start);
    waitFor(callback.openSemaphore);
    testQueue.enqueueAndForget(writeStream::writeHandshake);
    waitFor(callback.handshakeSemaphore);
  }

  @Test
  public void testWatchStreamStopBeforeHandshake() throws Exception {
    AsyncQueue testQueue = new AsyncQueue();
    GrpcMetadataProvider mockGrpcProvider = mock(GrpcMetadataProvider.class);
    Datastore datastore =
        new Datastore(
            IntegrationTestUtil.testEnvDatabaseInfo(),
            testQueue,
            new EmptyCredentialsProvider(),
            ApplicationProvider.getApplicationContext(),
            mockGrpcProvider);
    StreamStatusCallback streamCallback = new StreamStatusCallback() {};
    final WatchStream watchStream = datastore.createWatchStream(streamCallback);

    testQueue.enqueueAndForget(watchStream::start);
    waitFor(streamCallback.openSemaphore);

    // Stop should call watchStreamStreamDidClose.
    testQueue.runSync(watchStream::stop);
    assertThat(streamCallback.closeSemaphore.availablePermits()).isEqualTo(1);
    verify(mockGrpcProvider, times(1)).updateMetadata(any());
  }

  @Test
  public void testWriteStreamStopAfterHandshake() throws Exception {
    AsyncQueue testQueue = new AsyncQueue();
    Datastore datastore =
        new Datastore(
            IntegrationTestUtil.testEnvDatabaseInfo(),
            testQueue,
            new EmptyCredentialsProvider(),
            ApplicationProvider.getApplicationContext(),
            null);
    final WriteStream[] writeStreamWrapper = new WriteStream[1];
    StreamStatusCallback streamCallback =
        new StreamStatusCallback() {
          @Override
          public void onHandshakeComplete() {
            assertThat(writeStreamWrapper[0].getLastStreamToken()).isNotEmpty();
            super.onHandshakeComplete();
          }

          @Override
          public void onWriteResponse(
              SnapshotVersion commitVersion, List<MutationResult> mutationResults) {
            assertThat(mutationResults).hasSize(1);
            assertThat(writeStreamWrapper[0].getLastStreamToken()).isNotEmpty();
            super.onWriteResponse(commitVersion, mutationResults);
          }
        };
    WriteStream writeStream = writeStreamWrapper[0] = datastore.createWriteStream(streamCallback);
    testQueue.enqueueAndForget(writeStream::start);
    waitFor(streamCallback.openSemaphore);

    // Writing before the handshake should throw
    testQueue.enqueueAndForget(
        () -> assertThrows(Throwable.class, () -> writeStream.writeMutations(mutations)));

    // Handshake should always be called
    testQueue.enqueueAndForget(writeStream::writeHandshake);
    waitFor(streamCallback.handshakeSemaphore);

    // Now writes should succeed
    testQueue.enqueueAndForget(() -> writeStream.writeMutations(mutations));
    waitFor(streamCallback.responseReceivedSemaphore);

    testQueue.runSync(writeStream::stop);
  }

  /** Verifies that the stream issues an onClose callback after a call to stop(). */
  @Test
  public void testWriteStreamStopPartial() throws Exception {
    AsyncQueue testQueue = new AsyncQueue();
    Datastore datastore =
        new Datastore(
            IntegrationTestUtil.testEnvDatabaseInfo(),
            testQueue,
            new EmptyCredentialsProvider(),
            ApplicationProvider.getApplicationContext(),
            null);
    StreamStatusCallback streamCallback = new StreamStatusCallback() {};
    final WriteStream writeStream = datastore.createWriteStream(streamCallback);

    testQueue.enqueueAndForget(writeStream::start);
    waitFor(streamCallback.openSemaphore);

    // Don't start the handshake

    testQueue.runSync(writeStream::stop);
    assertThat(streamCallback.closeSemaphore.availablePermits()).isEqualTo(1);
  }

  @Test
  public void testWriteStreamStop() throws Exception {
    AsyncQueue testQueue = new AsyncQueue();
    StreamStatusCallback streamCallback = new StreamStatusCallback();
    WriteStream writeStream = createAndOpenWriteStream(testQueue, streamCallback);

    // Stop should call watchStreamStreamDidClose.
    testQueue.runSync(writeStream::stop);
    assertThat(streamCallback.closeSemaphore.availablePermits()).isEqualTo(1);
  }

  @Test
  public void testStreamClosesWhenIdle() throws Exception {
    AsyncQueue testQueue = new AsyncQueue();
    StreamStatusCallback callback = new StreamStatusCallback();
    WriteStream writeStream = createAndOpenWriteStream(testQueue, callback);

    testQueue.enqueueAndForget(
        () -> {
          writeStream.markIdle();
          assertTrue(testQueue.containsDelayedTask(TimerId.WRITE_STREAM_IDLE));
        });

    testQueue.runDelayedTasksUntil(TimerId.WRITE_STREAM_IDLE);
    waitFor(callback.closeSemaphore);
    testQueue.runSync(() -> assertFalse(writeStream.isOpen()));
  }

  @Test
  public void testStreamCancelsIdleOnWrite() throws Exception {
    AsyncQueue testQueue = new AsyncQueue();
    WriteStream writeStream = createAndOpenWriteStream(testQueue, new StreamStatusCallback());

    testQueue.runSync(
        () -> {
          writeStream.markIdle();

          writeStream.writeMutations(mutations);
        });

    assertFalse(testQueue.containsDelayedTask(TimerId.WRITE_STREAM_IDLE));
  }

  @Test
  public void testStreamStaysIdle() throws Exception {
    AsyncQueue testQueue = new AsyncQueue();
    WriteStream writeStream = createAndOpenWriteStream(testQueue, new StreamStatusCallback());

    testQueue.runSync(
        () -> {
          writeStream.markIdle();
          writeStream.markIdle();
        });

    assertTrue(testQueue.containsDelayedTask(TimerId.WRITE_STREAM_IDLE));
  }

  @Test
  public void testStreamRefreshesTokenUponExpiration() throws Exception {
    AsyncQueue testQueue = new AsyncQueue();
    MockCredentialsProvider mockCredentialsProvider = new MockCredentialsProvider();
    Datastore datastore =
        new Datastore(
            IntegrationTestUtil.testEnvDatabaseInfo(),
            testQueue,
            mockCredentialsProvider,
            ApplicationProvider.getApplicationContext(),
            null);
    StreamStatusCallback callback = new StreamStatusCallback();
    WriteStream writeStream = datastore.createWriteStream(callback);
    waitForWriteStreamOpen(testQueue, writeStream, callback);

    // Simulate callback from GRPC with an unauthenticated error -- this should invalidate the
    // token.
    testQueue.runSync(() -> writeStream.handleServerClose(Status.UNAUTHENTICATED));
    waitForWriteStreamOpen(testQueue, writeStream, callback);

    // Simulate a different error -- token should not be invalidated this time.
    testQueue.runSync(() -> writeStream.handleServerClose(Status.UNAVAILABLE));
    waitForWriteStreamOpen(testQueue, writeStream, callback);

    assertThat(mockCredentialsProvider.observedStates())
        .containsExactly("getToken", "invalidateToken", "getToken", "getToken")
        .inOrder();
  }
}
