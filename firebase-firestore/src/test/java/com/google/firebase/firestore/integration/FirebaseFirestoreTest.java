package com.google.firebase.firestore.integration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.util.Executors.BACKGROUND_EXECUTOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreTestFactory;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.TestAccessHelper;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firestore.v1.InitRequest;
import com.google.firestore.v1.InitResponse;
import com.google.firestore.v1.ListenRequest;
import com.google.firestore.v1.ListenResponse;
import com.google.protobuf.ByteString;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MockClientCall;
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

    private <T> T waitForException(Task<QuerySnapshot> task, Class<T> clazz) throws InterruptedException {
        return clazz.cast(waitForException(task));
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
        waitForSuccess(first.initializeCompleteTask);

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
        TaskCompletionSource<QuerySnapshot> snapshotTask1 = new TaskCompletionSource<>();
        firestore.collection("col")
                .addSnapshotListener(BACKGROUND_EXECUTOR, (value, error) -> {
                    if (error == null) {
                        // Skip cached results.
                        if (value.getMetadata().isFromCache()) return;
                        snapshotTask1.setResult(value);
                    } else {
                        snapshotTask1.setException(error);
                    }
                });

        // Wait for first FirestoreClient to instantiate
        FirebaseFirestoreTestFactory.Instance first = waitForResult(factory.instances.get(0));

        // Wait for Listen CallClient to be created.
        MockClientCall<ListenRequest, ListenResponse> callbackTask1 = waitForResult(first.listenClientCallbacks.get(0));

        // Wait for Listen CallClient to have start called by FirestoreClient.
        // This gives us a response callback to simulate response from server.
        ClientCall.Listener<ListenResponse> responseListener1 = waitForResult(callbackTask1.getStart()).first;

        // Wait for ListenRequest.
        // We expect an empty init request because the database is fresh.
        assertThat(waitForResult(callbackTask1.getSent(0)))
                .isEqualTo(listenRequestWith(InitRequest.getDefaultInstance()));

        // Simulate a successful InitResponse from server.
        waitForSuccess(first.enqueue(() -> responseListener1.onMessage(ListenResponse.newBuilder()
                .setInitResponse(InitResponse.newBuilder()
                        .setSessionToken(ByteString.copyFromUtf8("token1")))
                .build())));

        // We expect previous addSnapshotListener to cause a, AddTarget request.
        assertTrue(waitForResult(callbackTask1.getSent(1)).hasAddTarget());

        // Simulate Database deletion by closing connection with NOT_FOUND.
        waitForSuccess(first.enqueue(() -> responseListener1.onClose(Status.NOT_FOUND, new Metadata())));

        // We expect client to reconnect Listen stream.
        MockClientCall<ListenRequest, ListenResponse> callbackTask2 = waitForResult(first.listenClientCallbacks.get(1));

        // Wait for Listen CallClient to have start called by FirestoreClient.
        // This gives us a response callback to simulate response from server.
        ClientCall.Listener<ListenResponse> responseListener2 = waitForResult(callbackTask2.getStart()).first;

        // Wait for ListenRequest.
        // We expect FirestoreClient to send InitRequest with previous token.
        assertThat(waitForResult(callbackTask2.getSent(0)))
                .isEqualTo(listenRequestWith(InitRequest.newBuilder()
                        .setSessionToken(ByteString.copyFromUtf8("token1"))
                        .build()));


        // This task will complete when clearPersistence is invoked on FirebaseFirestore.
        Task<Void> clearPersistenceTask = setupClearPersistenceTask();

        // Simulate a clear cache InitResponse from server.
        waitForSuccess(first.enqueue(() -> responseListener2.onMessage(ListenResponse.newBuilder()
                .setInitResponse(InitResponse.newBuilder()
                        .setSessionToken(ByteString.copyFromUtf8("token2"))
                        .setClearCache(true))
                .build())));

        // Wait for cleanPersistence to be run.
        waitForSuccess(clearPersistenceTask);

        // Verify that the first FirestoreClient was shutdown. If the GrpcCallProvider component has
        // has it's shutdown method called, then we know shutdown was triggered.
        verify(first.mockGrpcCallProvider, times(1)).shutdown();

        // Snapshot listeners should fail with ABORTED
        FirebaseFirestoreException exception = waitForException(snapshotTask1.getTask(), FirebaseFirestoreException.class);
        assertThat(exception.getCode()).isEqualTo(FirebaseFirestoreException.Code.ABORTED);

        // Start another snapshot listener
        TaskCompletionSource<QuerySnapshot> snapshotTask2 = new TaskCompletionSource<>();
        firestore.collection("col")
                .addSnapshotListener(BACKGROUND_EXECUTOR, (value, error) -> {
                    if (error == null) {
                        // Skip cached results.
                        if (value.getMetadata().isFromCache()) return;
                        snapshotTask2.setResult(value);
                    } else {
                        snapshotTask2.setException(error);
                    }
                });

        // Wait for first FirestoreClient to instantiate
        FirebaseFirestoreTestFactory.Instance second = waitForResult(factory.instances.get(1));

        // Wait for Listen CallClient to be created.
        MockClientCall<ListenRequest, ListenResponse> callbackTask3 = waitForResult(second.listenClientCallbacks.get(0));

        // Wait for Listen CallClient to have start called by FirestoreClient.
        // This gives us a response callback to simulate response from server.
        ClientCall.Listener<ListenResponse> responseListener3 = waitForResult(callbackTask3.getStart()).first;

        // Wait for ListenRequest.
        // We expect FirestoreClient to send InitRequest with previous token.
        assertThat(waitForResult(callbackTask3.getSent(0)))
                .isEqualTo(listenRequestWith(InitRequest.newBuilder()
                        .setSessionToken(ByteString.copyFromUtf8("token2"))
                        .build()));
    }

    private ListenRequest listenRequestWith(InitRequest initRequest) {
        return ListenRequest.newBuilder()
                .setDatabase(getResourcePrefixValue(factory.databaseId))
                .setInitRequest(initRequest)
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