// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.util.Executors.BACKGROUND_EXECUTOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreIntegrationTestFactory;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.UserDataReader;
import com.google.firebase.firestore.core.UserData;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firestore.v1.InitRequest;
import com.google.firestore.v1.InitResponse;
import com.google.firestore.v1.ListenRequest;
import com.google.firestore.v1.ListenResponse;
import com.google.firestore.v1.Write;
import com.google.firestore.v1.WriteRequest;
import com.google.firestore.v1.WriteResponse;
import com.google.firestore.v1.WriteResult;
import com.google.protobuf.ByteString;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
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
public class FirebaseFirestoreTest {

  private DatabaseId databaseId;
  private FirebaseFirestore firestore;
  private FirebaseFirestoreIntegrationTestFactory factory;

  private static void waitForSuccess(Task<?> task) throws InterruptedException {
    waitFor(task).getResult();
  }

  private static <T> T waitForResult(Task<T> task) throws InterruptedException {
    return waitFor(task).getResult();
  }

  private static Exception waitForException(Task<?> task) throws InterruptedException {
    Exception exception = waitFor(task).getException();
    assertThat(exception).isNotNull();
    return exception;
  }

  @NonNull
  public static String getResourcePrefixValue(DatabaseId databaseId) {
    return String.format(
        "projects/%s/databases/%s", databaseId.getProjectId(), databaseId.getDatabaseId());
  }

  private static <T> Task<T> waitFor(Task<T> task) throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(1);
    task.addOnSuccessListener(BACKGROUND_EXECUTOR, t -> countDownLatch.countDown());
    task.addOnFailureListener(BACKGROUND_EXECUTOR, e -> countDownLatch.countDown());
    task.addOnCanceledListener(BACKGROUND_EXECUTOR, () -> countDownLatch.countDown());
    countDownLatch.await(15, TimeUnit.SECONDS);
    return task;
  }

  private <T> T waitForException(Task<QuerySnapshot> task, Class<T> clazz)
      throws InterruptedException {
    return clazz.cast(waitForException(task));
  }

  @Before
  public void before() {
    databaseId = DatabaseId.forDatabase("p", "d");
    factory = new FirebaseFirestoreIntegrationTestFactory(databaseId);
    factory.useMemoryCache();
    firestore = factory.firestore;
  }

  @After
  public void after() throws Exception {
    waitForSuccess(firestore.terminate());
    verify(factory.instanceRegistry, Mockito.atLeastOnce()).remove(databaseId.getDatabaseId());
    Mockito.verifyNoMoreInteractions(factory.instanceRegistry);

    factory = null;
    firestore = null;
  }

  @Test
  public void preserveWritesWhenDisconnectedWithInternalError() throws Exception {
    CollectionReference col = firestore.collection("col");
    DocumentReference doc1 = col.document();
    DocumentReference doc2 = col.document();
    DocumentReference doc3 = col.document();
    doc1.set(map("foo", "A"));
    doc2.set(map("foo", "B"));
    doc3.set(map("foo", "C"));

    // Wait for first FirestoreClient to instantiate
    FirebaseFirestoreIntegrationTestFactory.Instance instance =
        waitForResult(factory.instances.get(0));
    RemoteSerializer serializer = instance.componentProvider.getRemoteSerializer();

    // First Write stream connection
    {
      // Wait for Write CallClient to be created.
      TestClientCall<WriteRequest, WriteResponse> callback =
          waitForResult(instance.getWriteClient(0));
      Iterator<Task<WriteRequest>> requests = callback.requestIterator();

      // Wait for WriteRequest handshake.
      // We expect an empty init request because the database is fresh.
      assertThat(waitForResult(requests.next()))
          .isEqualTo(writeRequest(InitRequest.getDefaultInstance()));

      // Simulate a successful InitResponse from server.
      waitForSuccess(
          instance.enqueue(
              () -> callback.listener.onMessage(writeResponse(initResponse("token1")))));

      // Expect first write request.
      Write write1 = serializer.encodeMutation(setMutation(doc1, map("foo", "A")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write1));

      // Simulate write acknowledgement.
      waitForSuccess(
          instance.enqueue(
              () -> callback.listener.onMessage(writeResponse(WriteResult.getDefaultInstance()))));

      // Expect second write request.
      Write write2 = serializer.encodeMutation(setMutation(doc2, map("foo", "B")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write2));

      // Simulate INTERNAL error that is retryable. (
      waitForSuccess(
          instance.enqueue(() -> callback.listener.onClose(Status.INTERNAL, new Metadata())));
    }

    // Second Write Stream connection
    // Previous connection was closed by server with NOT_FOUND error.
    {
      // Wait for Write CallClient to be created.
      TestClientCall<WriteRequest, WriteResponse> callback =
          waitForResult(instance.getWriteClient(1));
      Iterator<Task<WriteRequest>> requests = callback.requestIterator();

      // Wait for WriteRequest handshake.
      // We expect FirestoreClient to send InitRequest with previous token.
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(initRequest("token1")));

      // Simulate a successful InitResponse from server.
      waitForSuccess(
          instance.enqueue(
              () -> callback.listener.onMessage(writeResponse(initResponse("token2")))));

      // Expect second write to be retried.
      Write write2 = serializer.encodeMutation(setMutation(doc2, map("foo", "B")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write2));

      // Simulate write acknowledgement.
      waitForSuccess(
          instance.enqueue(
              () -> callback.listener.onMessage(writeResponse(WriteResult.getDefaultInstance()))));

      // Expect second write request.
      Write write3 = serializer.encodeMutation(setMutation(doc3, map("foo", "C")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write3));
    }
  }

  @Test()
  public void clearPersistanceAfterStartupShouldRestartFirestoreClient() throws Exception {
    // Trigger instantiation of FirestoreClient
    firestore.collection("col");

    FirebaseFirestoreIntegrationTestFactory.Instance first =
        waitForResult(factory.instances.get(0));

    AsyncQueue firstAsyncQueue = first.configuration.asyncQueue;

    assertFalse(firstAsyncQueue.isShuttingDown());

    // Clearing persistence will require restarting FirestoreClient.
    waitForSuccess(firestore.clearPersistence());

    // Now we have a history of 2 instances.
    FirebaseFirestoreIntegrationTestFactory.Instance second =
        waitForResult(factory.instances.get(1));
    AsyncQueue secondAsyncQueue = second.configuration.asyncQueue;

    assertEquals(firstAsyncQueue.getExecutor(), secondAsyncQueue.getExecutor());

    assertTrue(firstAsyncQueue.isShuttingDown());
    assertFalse(secondAsyncQueue.isShuttingDown());

    // AsyncQueue of first instance should reject tasks.
    Exception firstTask = waitForException(firstAsyncQueue.enqueue(() -> "Hi"));
    assertThat(firstTask).isInstanceOf(RejectedExecutionException.class);
    assertThat(firstTask).hasMessageThat().isEqualTo("AsyncQueue is shutdown");

    // AsyncQueue of second instance should be functional.
    assertThat(waitFor(secondAsyncQueue.enqueue(() -> "Hello")).getResult()).isEqualTo("Hello");

    waitForSuccess(firestore.terminate());

    // After terminate the second instance should also reject tasks.
    Exception afterTerminate = waitForException(secondAsyncQueue.enqueue(() -> "Uh oh"));
    assertThat(afterTerminate).isInstanceOf(RejectedExecutionException.class);
    assertThat(afterTerminate).hasMessageThat().isEqualTo("AsyncQueue is shutdown");
  }

  @Test
  public void clearPersistenceDueToInitResponse() throws Exception {
    // Create a snapshot listener that will be active during handshake clearing of cache.
    TestEventListener<QuerySnapshot> snapshotListener1 = new TestEventListener<>();
    firestore.collection("col").addSnapshotListener(BACKGROUND_EXECUTOR, snapshotListener1);
    Iterator<Task<QuerySnapshot>> snapshots = snapshotListener1.iterator();
    Task<QuerySnapshot> firstSnapshot = snapshots.next();

    // Wait for first FirestoreClient to instantiate
    FirebaseFirestoreIntegrationTestFactory.Instance first =
        waitForResult(factory.instances.get(0));

    // Wait for Listen CallClient to be created.
    TestClientCall<ListenRequest, ListenResponse> callback1 =
        waitForResult(first.getListenClient(0));

    // Wait for ListenRequest handshake.
    // We expect an empty init request because the database is fresh.
    assertThat(waitForResult(callback1.getRequest(0)))
        .isEqualTo(listenRequest(InitRequest.getDefaultInstance()));

    // Simulate a successful InitResponse from server.
    waitForSuccess(
        first.enqueue(() -> callback1.listener.onMessage(listenResponse(initResponse("token1")))));

    // We expect previous addSnapshotListener to cause a, AddTarget request.
    assertTrue(waitForResult(callback1.getRequest(1)).hasAddTarget());

    // TODO(does this make sense?)
    // We have a 10 second timeout on raising snapshot from Cache, that is triggered when Listen
    // connection is closed.
    assertFalse(firstSnapshot.isComplete());

    // Simulate Database deletion by closing connection with NOT_FOUND.
    waitForSuccess(
        first.enqueue(() -> callback1.listener.onClose(Status.NOT_FOUND, new Metadata())));

    // First snapshot is raised from cache immediately after connection is closed.
    assertTrue(waitForResult(firstSnapshot).getMetadata().isFromCache());

    // We expect client to reconnect Listen stream.
    TestClientCall<ListenRequest, ListenResponse> callback2 =
        waitForResult(first.getListenClient(1));

    // Wait for ListenRequest.
    // We expect FirestoreClient to send InitRequest with previous token.
    assertThat(waitForResult(callback2.getRequest(0)))
        .isEqualTo(listenRequest(initRequest("token1")));

    // This task will complete when clearPersistence is invoked on FirebaseFirestore.
    Task<Void> clearPersistenceTask = setupClearPersistenceTask();

    // Simulate a clear cache InitResponse from server.
    waitForSuccess(
        first.enqueue(
            () -> callback2.listener.onMessage(listenResponse(initResponse("token2", true)))));

    // Wait for cleanPersistence to be run.
    waitForSuccess(clearPersistenceTask);

    // Verify that the first FirestoreClient was shutdown. If the GrpcCallProvider component has
    // has it's shutdown method called, then we know shutdown was triggered.
    verify(first.mockGrpcCallProvider, times(1)).shutdown();

    // Snapshot listeners should fail with ABORTED
    FirebaseFirestoreException exception =
        waitForException(snapshots.next(), FirebaseFirestoreException.class);
    assertThat(exception.getCode()).isEqualTo(FirebaseFirestoreException.Code.ABORTED);

    // Start another snapshot listener
    TestEventListener<QuerySnapshot> snapshotListener2 = new TestEventListener<>();
    firestore.collection("col").addSnapshotListener(BACKGROUND_EXECUTOR, snapshotListener2);

    // Wait for second FirestoreClient to instantiate
    FirebaseFirestoreIntegrationTestFactory.Instance second =
        waitForResult(factory.instances.get(1));

    // Wait for Listen CallClient to be created.
    TestClientCall<ListenRequest, ListenResponse> callback3 =
        waitForResult(second.getListenClient(0));

    // Wait for ListenRequest.
    // We expect FirestoreClient to send InitRequest with previous token.
    assertThat(waitForResult(callback3.getRequest(0)))
        .isEqualTo(listenRequest(initRequest("token2")));
  }

  @Test
  public void preserveWritesWhenDisconnectedWithNotFound() throws Exception {
    CollectionReference col = firestore.collection("col");
    DocumentReference doc1 = col.document();
    DocumentReference doc2 = col.document();
    DocumentReference doc3 = col.document();
    doc1.set(map("foo", "A"));
    doc2.set(map("foo", "B"));
    doc3.set(map("foo", "C"));

    // 1st FirestoreClient instance.
    {
      // Wait for first FirestoreClient to instantiate
      FirebaseFirestoreIntegrationTestFactory.Instance instance =
          waitForResult(factory.instances.get(0));
      RemoteSerializer serializer = instance.componentProvider.getRemoteSerializer();

      // First Write stream connection
      {
        // Wait for Write CallClient to be created.
        TestClientCall<WriteRequest, WriteResponse> callback =
            waitForResult(instance.getWriteClient(0));
        Iterator<Task<WriteRequest>> requests = callback.requestIterator();

        // Wait for WriteRequest handshake.
        // We expect an empty init request because the database is fresh.
        assertThat(waitForResult(requests.next()))
            .isEqualTo(writeRequest(InitRequest.getDefaultInstance()));

        // Simulate a successful InitResponse from server.
        waitForSuccess(
            instance.enqueue(
                () -> callback.listener.onMessage(writeResponse(initResponse("token1")))));

        // Expect first write request.
        Write write1 = serializer.encodeMutation(setMutation(doc1, map("foo", "A")));
        assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write1));

        // Simulate write acknowledgement.
        waitForSuccess(
            instance.enqueue(
                () ->
                    callback.listener.onMessage(writeResponse(WriteResult.getDefaultInstance()))));

        // Expect second write request.
        Write write2 = serializer.encodeMutation(setMutation(doc2, map("foo", "B")));
        assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write2));

        // Simulate NOT_FOUND error that was NOT due to database name reuse. (
        waitForSuccess(
            instance.enqueue(() -> callback.listener.onClose(Status.NOT_FOUND, new Metadata())));
      }

      // Second Write Stream connection
      // Previous connection was closed by server with NOT_FOUND error.
      {
        // Wait for Write CallClient to be created.
        TestClientCall<WriteRequest, WriteResponse> callback =
            waitForResult(instance.getWriteClient(1));
        Iterator<Task<WriteRequest>> requests = callback.requestIterator();

        // Wait for WriteRequest handshake.
        // We expect FirestoreClient to send InitRequest with previous token.
        assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(initRequest("token1")));

        // Simulate a successful InitResponse from server.
        waitForSuccess(
            instance.enqueue(
                () -> callback.listener.onMessage(writeResponse(initResponse("token2")))));

        // Expect second write to be retried.
        Write write2 = serializer.encodeMutation(setMutation(doc2, map("foo", "B")));
        assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write2));

        // Simulate write acknowledgement.
        waitForSuccess(
            instance.enqueue(
                () ->
                    callback.listener.onMessage(writeResponse(WriteResult.getDefaultInstance()))));

        // Simulate NOT_FOUND error. This time we will clear cache.
        waitForSuccess(
            instance.enqueue(() -> callback.listener.onClose(Status.NOT_FOUND, new Metadata())));
      }

      // Third Write Stream connection
      // Previous connection was closed by server with NOT_FOUND error.
      {
        // Wait for Write CallClient to be created.
        TestClientCall<WriteRequest, WriteResponse> callback =
            waitForResult(instance.getWriteClient(2));
        Iterator<Task<WriteRequest>> requests = callback.requestIterator();

        // Wait for WriteRequest.
        // We expect FirestoreClient to send InitRequest with previous token.
        assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(initRequest("token2")));

        // Simulate a clear cache InitResponse from server.
        waitForSuccess(
            instance.enqueue(
                () -> callback.listener.onMessage(writeResponse(initResponse("token3", true)))));
      }
    }

    // Interaction with 2nd FirestoreClient instance.
    // Previous instance was shutdown due to clear cache command from server.
    {
      // Wait for second FirestoreClient to instantiate
      FirebaseFirestoreIntegrationTestFactory.Instance instance =
          waitForResult(factory.instances.get(1));
      RemoteSerializer serializer = instance.componentProvider.getRemoteSerializer();

      // The writes should have been cleared, so we will have to create a new one.
      DocumentReference doc4 = col.document();
      doc4.set(map("foo", "D"));

      // Wait for Write CallClient to be created.
      TestClientCall<WriteRequest, WriteResponse> callback =
          waitForResult(instance.getWriteClient(0));
      Iterator<Task<WriteRequest>> requests = callback.requestIterator();

      // Wait for WriteRequest.
      // We expect FirestoreClient to send InitRequest with previous token.
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(initRequest("token3")));

      // Simulate a successful InitResponse from server.
      waitForSuccess(
          instance.enqueue(
              () -> callback.listener.onMessage(writeResponse(initResponse("token4")))));

      // Expect the new write request.
      Write write4 = serializer.encodeMutation(setMutation(doc4, map("foo", "D")));
      assertThat(waitForResult(requests.next())).isEqualTo(writeRequest(write4));

      // Simulate write acknowledgement.
      waitForSuccess(
          instance.enqueue(
              () -> callback.listener.onMessage(writeResponse(WriteResult.getDefaultInstance()))));
    }
  }

  @Test
  public void listenHandshakeMustWaitForWriteHandshakeToComplete() throws Exception {
    CollectionReference col = firestore.collection("col");

    // Wait for FirestoreClient to instantiate
    FirebaseFirestoreIntegrationTestFactory.Instance instance =
        waitForResult(factory.instances.get(0));

    // Trigger Write Stream First
    col.document().set(map("foo", "A"));

    TestClientCall<WriteRequest, WriteResponse> write = waitForResult(instance.getWriteClient(0));
    ClientCall.Listener<WriteResponse> writeResponses = write.listener;
    Iterator<Task<WriteRequest>> writeRequests = write.requestIterator();

    // Then Trigger Listen Stream;
    TestEventListener<QuerySnapshot> snapshotListener = new TestEventListener<>();
    firestore.collection("col").addSnapshotListener(BACKGROUND_EXECUTOR, snapshotListener);
    Iterator<Task<QuerySnapshot>> snapshots = snapshotListener.iterator();

    TestClientCall<ListenRequest, ListenResponse> listen =
        waitForResult(instance.getListenClient(0));
    Iterator<Task<ListenRequest>> listenRequests = listen.requestIterator();
    ClientCall.Listener<ListenResponse> listenResponses = listen.listener;

    // Prepare
    Task<WriteRequest> writeInitRequest = writeRequests.next();
    Task<ListenRequest> listenInitRequest = listenRequests.next();

    // Expect empty InitRequest from Write stream.
    assertThat(waitForResult(writeInitRequest))
        .isEqualTo(writeRequest(InitRequest.getDefaultInstance()));

    // No request should have come from Listen stream yet.
    assertFalse(listenInitRequest.isComplete());

    // Simulate a successful InitResponse from server.
    waitForSuccess(
        instance.enqueue(() -> writeResponses.onMessage(writeResponse(initResponse("token1")))));

    // Now that Write handshake is complete, the Listen stream should send a InitRequest with token
    // from Write handshake.
    assertThat(waitForResult(listenInitRequest)).isEqualTo(listenRequest(initRequest("token1")));
  }

  @Test
  public void writeHandshakeMustWaitForListenHandshakeToComplete() throws Exception {
    CollectionReference col = firestore.collection("col");

    // Wait for FirestoreClient to instantiate
    FirebaseFirestoreIntegrationTestFactory.Instance instance =
        waitForResult(factory.instances.get(0));

    // Trigger Listen Stream First
    TestEventListener<QuerySnapshot> snapshotListener = new TestEventListener<>();
    firestore.collection("col").addSnapshotListener(BACKGROUND_EXECUTOR, snapshotListener);
    Iterator<Task<QuerySnapshot>> snapshots = snapshotListener.iterator();

    TestClientCall<ListenRequest, ListenResponse> listen =
        waitForResult(instance.getListenClient(0));
    Iterator<Task<ListenRequest>> listenRequests = listen.requestIterator();
    ClientCall.Listener<ListenResponse> listenResponses = listen.listener;

    // Then Trigger Write Stream;
    col.document().set(map("foo", "A"));

    TestClientCall<WriteRequest, WriteResponse> write = waitForResult(instance.getWriteClient(0));
    ClientCall.Listener<WriteResponse> writeResponses = write.listener;
    Iterator<Task<WriteRequest>> writeRequests = write.requestIterator();

    // Prepare
    Task<WriteRequest> writeInitRequest = writeRequests.next();
    Task<ListenRequest> listenInitRequest = listenRequests.next();

    // Expect empty InitRequest from Listen stream.
    assertThat(waitForResult(listenInitRequest))
        .isEqualTo(listenRequest(InitRequest.getDefaultInstance()));

    // No request should have come from Listen stream yet.
    assertFalse(writeInitRequest.isComplete());

    // Simulate a successful InitResponse from server.
    waitForSuccess(
        instance.enqueue(() -> listenResponses.onMessage(listenResponse(initResponse("token1")))));

    // Now that Write handshake is complete, the Listen stream should send a InitRequest with token
    // from Write handshake.
    assertThat(waitForResult(writeInitRequest)).isEqualTo(writeRequest(initRequest("token1")));
  }

  @NonNull
  private DocumentKey key(DocumentReference doc) {
    return DocumentKey.fromPathString(doc.getPath());
  }

  @NonNull
  public SetMutation setMutation(DocumentReference doc, Map<String, Object> values) {
    UserDataReader dataReader = new UserDataReader(databaseId);
    UserData.ParsedSetData parsed = dataReader.parseSetData(values);

    // The order of the transforms doesn't matter, but we sort them so tests can assume a particular
    // order.
    ArrayList<FieldTransform> fieldTransforms = new ArrayList<>(parsed.getFieldTransforms());
    Collections.sort(fieldTransforms, Comparator.comparing(FieldTransform::getFieldPath));

    return new SetMutation(key(doc), parsed.getData(), Precondition.NONE, fieldTransforms);
  }

  @NonNull
  private ListenRequest listenRequest(InitRequest initRequest) {
    return ListenRequest.newBuilder()
        .setDatabase(getResourcePrefixValue(databaseId))
        .setInitRequest(initRequest)
        .build();
  }

  @NonNull
  private static ListenResponse listenResponse(InitResponse initResponse) {
    return ListenResponse.newBuilder().setInitResponse(initResponse).build();
  }

  @NonNull
  private WriteRequest writeRequest(InitRequest initRequest) {
    return WriteRequest.newBuilder()
        .setDatabase(getResourcePrefixValue(databaseId))
        .setInitRequest(initRequest)
        .build();
  }

  @NonNull
  private WriteRequest writeRequest(Write... writes) {
    WriteRequest.Builder builder = WriteRequest.newBuilder();
    for (Write write : writes) {
      builder.addWrites(write);
    }
    return builder.build();
  }

  @NonNull
  private static WriteResponse writeResponse(InitResponse initResponse) {
    return WriteResponse.newBuilder().setInitResponse(initResponse).build();
  }

  @NonNull
  private static WriteResponse writeResponse(WriteResult... writeResults) {
    WriteResponse.Builder builder = WriteResponse.newBuilder();
    for (WriteResult writeResult : writeResults) {
      builder.addWriteResults(writeResult);
    }
    return builder.build();
  }

  @NonNull
  private static InitResponse initResponse(String token) {
    return InitResponse.newBuilder().setSessionToken(ByteString.copyFromUtf8(token)).build();
  }

  @NonNull
  private static InitResponse initResponse(String token, boolean clearCache) {
    return InitResponse.newBuilder()
        .setSessionToken(ByteString.copyFromUtf8(token))
        .setClearCache(clearCache)
        .build();
  }

  @NonNull
  private static InitRequest initRequest(String token) {
    return InitRequest.newBuilder().setSessionToken(ByteString.copyFromUtf8(token)).build();
  }

  @NonNull
  private Task<Void> setupClearPersistenceTask() {
    TaskCompletionSource<Void> clearPersistenceTask = new TaskCompletionSource<>();
    factory.setClearPersistenceMethod(
        executor -> {
          executor.execute(() -> clearPersistenceTask.setResult(null));
          return clearPersistenceTask.getTask();
        });
    return clearPersistenceTask.getTask();
  }
}
