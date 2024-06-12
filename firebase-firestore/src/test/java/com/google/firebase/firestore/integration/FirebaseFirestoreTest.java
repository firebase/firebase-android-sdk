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
import com.google.firebase.firestore.FirebaseFirestoreTestFactory;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firestore.v1.InitRequest;
import com.google.firestore.v1.InitResponse;
import com.google.firestore.v1.ListenRequest;
import com.google.firestore.v1.ListenResponse;
import com.google.firestore.v1.WriteRequest;
import com.google.firestore.v1.WriteResponse;
import com.google.protobuf.ByteString;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.grpc.Metadata;
import io.grpc.Status;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseFirestoreTest {

    private FirebaseFirestore firestore;
    private FirebaseFirestoreTestFactory factory;

    private static void waitForSuccess(Task<?> task) throws InterruptedException {
        waitFor(task).getResult();
    }

    private static <T> T waitForResult(Task<T> task) throws InterruptedException {
        return waitFor(task).getResult();
    }

    private static Exception waitForException(Task<?> task) throws InterruptedException {
        return waitFor(task).getException();
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
        countDownLatch.await(900, TimeUnit.SECONDS);
        return task;
    }

    private <T> T waitForException(Task<QuerySnapshot> task, Class<T> clazz) throws InterruptedException {
        return clazz.cast(waitForException(task));
    }

    @Before
    public void before() {
        factory = new FirebaseFirestoreTestFactory();
        firestore = factory.firestore;
    }

    @After
    public void after() throws Exception {
        waitForSuccess(firestore.terminate());
        verify(factory.instanceRegistry, Mockito.atLeastOnce()).remove(factory.databaseId.getDatabaseId());
        Mockito.verifyNoMoreInteractions(factory.instanceRegistry);

        factory = null;
        firestore = null;
    }

    @Test()
    public void clearPersistanceAfterStartupShouldRestartFirestoreClient() throws Exception {
        // Trigger instantiation of FirestoreClient
        firestore.collection("col");

        FirebaseFirestoreTestFactory.Instance first = waitForResult(factory.instances.get(0));

        AsyncQueue firstAsyncQueue = first.configuration.asyncQueue;

        assertFalse(firstAsyncQueue.isShuttingDown());

        // Clearing persistence will require restarting FirestoreClient.
        waitForSuccess(firestore.clearPersistence());

        // Now we have a history of 2 instances.
        FirebaseFirestoreTestFactory.Instance second = waitForResult(factory.instances.get(1));
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
        Iterator<Task<QuerySnapshot>> snapshots1 = snapshotListener1.iterator();

        // First snapshot will be from cache.
        assertTrue(waitForResult(snapshots1.next()).getMetadata().isFromCache());

        // Wait for first FirestoreClient to instantiate
        FirebaseFirestoreTestFactory.Instance first = waitForResult(factory.instances.get(0));

        // Wait for Listen CallClient to be created.
        TestClientCall<ListenRequest, ListenResponse> callback1 = waitForResult(first.getListenClient(0));

        // Wait for ListenRequest handshake.
        // We expect an empty init request because the database is fresh.
        assertThat(waitForResult(callback1.getRequest(0)))
                .isEqualTo(listenRequest(InitRequest.getDefaultInstance()));

        // Simulate a successful InitResponse from server.
        waitForSuccess(first.enqueue(() -> callback1.listener.onMessage(listenResponse(initResponse("token1")))));

        // We expect previous addSnapshotListener to cause a, AddTarget request.
        assertTrue(waitForResult(callback1.getRequest(1)).hasAddTarget());

        // Simulate Database deletion by closing connection with NOT_FOUND.
        waitForSuccess(first.enqueue(() -> callback1.listener.onClose(Status.NOT_FOUND, new Metadata())));

        // We expect client to reconnect Listen stream.
        TestClientCall<ListenRequest, ListenResponse> callback2 = waitForResult(first.getListenClient(1));

        // Wait for ListenRequest.
        // We expect FirestoreClient to send InitRequest with previous token.
        assertThat(waitForResult(callback2.getRequest(0)))
                .isEqualTo(listenRequest(initRequest("token1")));


        // This task will complete when clearPersistence is invoked on FirebaseFirestore.
        Task<Void> clearPersistenceTask = setupClearPersistenceTask();

        // Simulate a clear cache InitResponse from server.
        waitForSuccess(first.enqueue(() -> callback2.listener.onMessage(
                listenResponse(initResponse("token2", true)))));

        // Wait for cleanPersistence to be run.
        waitForSuccess(clearPersistenceTask);

        // Verify that the first FirestoreClient was shutdown. If the GrpcCallProvider component has
        // has it's shutdown method called, then we know shutdown was triggered.
        verify(first.mockGrpcCallProvider, times(1)).shutdown();

        // Snapshot listeners should fail with ABORTED
        FirebaseFirestoreException exception = waitForException(snapshots1.next(), FirebaseFirestoreException.class);
        assertThat(exception.getCode()).isEqualTo(FirebaseFirestoreException.Code.ABORTED);

        // Start another snapshot listener
        TestEventListener<QuerySnapshot> snapshotListener2 = new TestEventListener<>();
        firestore.collection("col").addSnapshotListener(BACKGROUND_EXECUTOR, snapshotListener2);

        // Wait for first FirestoreClient to instantiate
        FirebaseFirestoreTestFactory.Instance second = waitForResult(factory.instances.get(1));

        // Wait for Listen CallClient to be created.
        TestClientCall<ListenRequest, ListenResponse> callback3 = waitForResult(second.getListenClient(0));

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

        // Wait for first FirestoreClient to instantiate
        FirebaseFirestoreTestFactory.Instance first = waitForResult(factory.instances.get(0));

        // Wait for Listen CallClient to be created.
        TestClientCall<WriteRequest, WriteResponse> callback1 = waitForResult(first.getWriteClient(0));

        // Wait for WriteRequest handshake.
        // We expect an empty init request because the database is fresh.
        assertThat(waitForResult(callback1.getRequest(0)))
                .isEqualTo(writeRequest(InitRequest.getDefaultInstance()));
    }

    private static ListenResponse listenResponse(InitResponse initResponse) {
    return ListenResponse.newBuilder()
            .setInitResponse(initResponse)
            .build();
    }

    private ListenRequest listenRequest(InitRequest initRequest) {
        return ListenRequest.newBuilder()
                .setDatabase(getResourcePrefixValue(factory.databaseId))
                .setInitRequest(initRequest)
                .build();
    }

    private WriteRequest writeRequest(InitRequest initRequest) {
        return WriteRequest.newBuilder()
                .setDatabase(getResourcePrefixValue(factory.databaseId))
                .setInitRequest(initRequest)
                .build();
    }

    private static InitResponse initResponse(String token) {
        return InitResponse.newBuilder()
                .setSessionToken(ByteString.copyFromUtf8(token))
                .build();
    }

    private static InitResponse initResponse(String token, boolean clearCache) {
        return InitResponse.newBuilder()
                .setSessionToken(ByteString.copyFromUtf8(token))
                .setClearCache(clearCache)
                .build();
    }

    private static InitRequest initRequest(String token) {
        return InitRequest.newBuilder()
                .setSessionToken(ByteString.copyFromUtf8(token))
                .build();
    }

    @NonNull
    private Task<Void> setupClearPersistenceTask() {
        TaskCompletionSource<Void> clearPersistenceTask = new TaskCompletionSource<>();
        factory.setClearPersistenceMethod(executor -> {
            executor.execute(() -> clearPersistenceTask.setResult(null));
            return clearPersistenceTask.getTask();
        });
        return clearPersistenceTask.getTask();
    }
}
