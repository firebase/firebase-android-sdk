// Copyright 2026 Google LLC
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
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.targetData;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.MemoryPersistence;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.QueryEngine;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.firestore.v1.WriteResponse;
import com.google.protobuf.ByteString;
import io.grpc.ClientCall;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests the status that streams report when they close. Only closes that the client initiates are
 * reported with status OK, which RemoteStore relies on.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class StreamCloseStatusTest {
  private static final DatabaseId DATABASE_ID = DatabaseId.forProject("project");

  private final AsyncQueue queue = new AsyncQueue();
  private final RemoteSerializer serializer = new RemoteSerializer(DATABASE_ID);

  /** The observers of the RPCs that the streams started, in the order they were started. */
  private final List<IncomingStreamObserver<?>> rpcObservers = new ArrayList<>();

  private FirestoreChannel channel;

  @Before
  public void setUp() {
    channel = mock(FirestoreChannel.class);
    when(channel.runBidiStreamingRpc(any(), any()))
        .thenAnswer(
            invocation -> {
              rpcObservers.add(invocation.getArgument(1));
              return mock(ClientCall.class);
            });
  }

  @Test
  public void watchStreamReportsServerCloseWithOkStatusAsTransientError() throws Exception {
    WatchStream.Callback callback = mock(WatchStream.Callback.class);
    WatchStream stream = new WatchStream(channel, queue, serializer, callback);
    queue.runSync(stream::start);
    openRpc(0);

    closeRpcFromServer(0, Status.OK);

    Status status = closeStatus(callback);
    assertThat(status.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
    assertThat(Datastore.isPermanentError(status)).isFalse();
  }

  @Test
  public void writeStreamReportsServerCloseWithOkStatusAsTransientError() throws Exception {
    WriteStream.Callback callback = mock(WriteStream.Callback.class);
    WriteStream stream = new WriteStream(channel, queue, serializer, callback);
    queue.runSync(stream::start);
    openRpc(0);

    closeRpcFromServer(0, Status.OK);

    Status status = closeStatus(callback);
    assertThat(status.getCode()).isEqualTo(Status.Code.UNAVAILABLE);
    assertThat(Datastore.isPermanentError(status)).isFalse();
    assertThat(Datastore.isPermanentWriteError(status)).isFalse();
  }

  @Test
  public void serverCloseWithOkStatusDoesNotResetBackoff() throws Exception {
    WatchStream stream =
        new WatchStream(channel, queue, serializer, mock(WatchStream.Callback.class));
    queue.runSync(stream::start);
    // RESOURCE_EXHAUSTED raises the backoff delay to its maximum (at least 30 seconds with jitter),
    // so a reset is easy to detect.
    closeRpcFromServerAndRestart(stream, 0, Status.RESOURCE_EXHAUSTED);
    queue.runDelayedTasksUntil(TimerId.LISTEN_STREAM_CONNECTION_BACKOFF);
    assertThat(rpcObservers).hasSize(2);

    closeRpcFromServerAndRestart(stream, 1, Status.OK);

    // After a reset, the stream would have restarted without delay, before this check runs.
    queue.runSync(
        () -> {
          assertThat(queue.containsDelayedTask(TimerId.LISTEN_STREAM_CONNECTION_BACKOFF)).isTrue();
          assertThat(rpcObservers).hasSize(2);
        });
    queue.runDelayedTasksUntil(TimerId.LISTEN_STREAM_CONNECTION_BACKOFF);
    assertThat(rpcObservers).hasSize(3);
  }

  @Test
  public void serverCloseWithErrorStatusIsReportedUnchanged() throws Exception {
    WatchStream.Callback callback = mock(WatchStream.Callback.class);
    WatchStream stream = new WatchStream(channel, queue, serializer, callback);
    queue.runSync(stream::start);
    openRpc(0);
    Status error = Status.PERMISSION_DENIED.withDescription("Permission denied");

    closeRpcFromServer(0, error);

    assertThat(closeStatus(callback)).isSameInstanceAs(error);
  }

  @Test
  public void stopReportsOkStatus() throws Exception {
    WriteStream.Callback callback = mock(WriteStream.Callback.class);
    WriteStream stream = new WriteStream(channel, queue, serializer, callback);
    queue.runSync(stream::start);
    openRpc(0);

    queue.runSync(stream::stop);

    assertThat(closeStatus(callback)).isSameInstanceAs(Status.OK);
    // The stream restarts without backoff.
    queue.runSync(stream::start);
    assertThat(rpcObservers).hasSize(2);
  }

  @Test
  public void idleTimeoutReportsOkStatus() throws Exception {
    WatchStream.Callback callback = mock(WatchStream.Callback.class);
    WatchStream stream = new WatchStream(channel, queue, serializer, callback);
    queue.runSync(stream::start);
    openRpc(0);
    queue.runSync(stream::markIdle);

    queue.runDelayedTasksUntil(TimerId.LISTEN_STREAM_IDLE);

    assertThat(closeStatus(callback)).isSameInstanceAs(Status.OK);
  }

  @Test
  public void remoteStoreRestartsWatchStreamAfterServerCloseWithOkStatus() throws Exception {
    RemoteStore.RemoteStoreCallback callback = mock(RemoteStore.RemoteStoreCallback.class);
    RemoteStore remoteStore = createRemoteStore(callback, createLocalStore());
    queue.runSync(
        () -> {
          remoteStore.start();
          remoteStore.listen(targetData(1, QueryPurpose.LISTEN, "coll"));
        });
    openRpc(0);

    // The target is still active, so the stream is still needed.
    closeRpcFromServer(0, Status.OK);
    queue.runDelayedTasksUntil(TimerId.ALL);

    assertThat(rpcObservers).hasSize(2);
    verify(callback, never()).handleRejectedListen(anyInt(), any());
  }

  @Test
  public void remoteStoreRestartsWriteStreamAfterServerCloseWithOkStatus() throws Exception {
    RemoteStore.RemoteStoreCallback callback = mock(RemoteStore.RemoteStoreCallback.class);
    LocalStore localStore = createLocalStore();
    localStore.writeLocally(singletonList(setMutation("coll/doc", map("key", "value"))));
    RemoteStore remoteStore = createRemoteStore(callback, localStore);
    queue.runSync(remoteStore::start);
    openRpc(0);
    // Completes the handshake, after which RemoteStore sends the pending write.
    receiveOnRpc(
        0, WriteResponse.newBuilder().setStreamToken(ByteString.copyFromUtf8("t")).build());

    // The write is still pending, so the stream is still needed.
    closeRpcFromServer(0, Status.OK);
    queue.runDelayedTasksUntil(TimerId.ALL);

    assertThat(rpcObservers).hasSize(2);
    verify(callback, never()).handleRejectedWrite(anyInt(), any());
  }

  private static LocalStore createLocalStore() {
    Persistence persistence = MemoryPersistence.createEagerGcMemoryPersistence();
    persistence.start();
    LocalStore localStore = new LocalStore(persistence, new QueryEngine(), User.UNAUTHENTICATED);
    localStore.start();
    return localStore;
  }

  private RemoteStore createRemoteStore(
      RemoteStore.RemoteStoreCallback callback, LocalStore localStore) {
    return new RemoteStore(
        DATABASE_ID,
        callback,
        localStore,
        new Datastore(queue, serializer, channel),
        queue,
        mock(ConnectivityMonitor.class));
  }

  private void openRpc(int index) throws InterruptedException {
    queue.runSync(() -> rpcObservers.get(index).onOpen());
  }

  @SuppressWarnings("unchecked")
  private <RespT> void receiveOnRpc(int index, RespT response) throws InterruptedException {
    queue.runSync(() -> ((IncomingStreamObserver<RespT>) rpcObservers.get(index)).onNext(response));
  }

  private void closeRpcFromServer(int index, Status status) throws InterruptedException {
    queue.runSync(() -> rpcObservers.get(index).onClose(status));
  }

  /** Closes the RPC from the server, then restarts the stream as RemoteStore does when needed. */
  private void closeRpcFromServerAndRestart(Stream<?> stream, int index, Status status)
      throws InterruptedException {
    queue.runSync(
        () -> {
          rpcObservers.get(index).onClose(status);
          stream.start();
        });
  }

  private static Status closeStatus(Stream.StreamCallback callback) {
    ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
    verify(callback).onClose(status.capture());
    return status.getValue();
  }
}
