package com.google.firebase.firestore;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.core.ComponentProvider;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.remote.GrpcCallProvider;
import com.google.firebase.firestore.remote.RemoteComponenetProvider;
import com.google.firebase.firestore.testutil.EmptyAppCheckTokenProvider;
import com.google.firebase.firestore.testutil.EmptyCredentialsProvider;
import com.google.firebase.firestore.util.Function;
import com.google.firestore.v1.FirestoreGrpc;
import com.google.firestore.v1.ListenRequest;
import com.google.firestore.v1.ListenResponse;
import com.google.firestore.v1.WriteRequest;
import com.google.firestore.v1.WriteResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import io.grpc.ClientCall;
import io.grpc.MockClientCall;

public final class FirebaseFirestoreTestFactory {

    public final DatabaseId databaseId;

    private TaskCompletionSource<Instance> nextInstance;
    public final List<Task<Instance>> instances = new ArrayList<>();

    public static class Instance {
        public ComponentProvider componentProvider;
        public GrpcCallProvider mockGrpcCallProvider;
        private TaskCompletionSource<MockClientCall<ListenRequest, ListenResponse>> nextListenClientCallback;
        public final List<Task<MockClientCall<ListenRequest, ListenResponse>>> listenClientCallbacks = new ArrayList<>();
        private TaskCompletionSource<MockClientCall<WriteRequest, WriteResponse>> nextWriteClientCallback;
        public final List<Task<MockClientCall<WriteRequest, WriteResponse>>> writeClientCallbacks = new ArrayList<>();

        private final TaskCompletionSource<Void> initializeComplete;
        public final Task<Void> initializeCompleteTask;

        public Task<Void> enqueue(Runnable runnable) {
            return configuration.asyncQueue.enqueue(runnable);
        }

        public ComponentProvider.Configuration configuration;

        public Instance() {
            prepareListenClientCallbacks();
            prepareWriteClientCallbacks();
            initializeComplete = new TaskCompletionSource<>();
            initializeCompleteTask = initializeComplete.getTask();
        }

        private void prepareWriteClientCallbacks() {
            nextWriteClientCallback = new TaskCompletionSource<>();
            writeClientCallbacks.add(nextWriteClientCallback.getTask());
        }

        private void prepareListenClientCallbacks() {
            nextListenClientCallback = new TaskCompletionSource<>();
            listenClientCallbacks.add(nextListenClientCallback.getTask());
        }

        private Task<ClientCall<ListenRequest, ListenResponse>> createListenCallback() {
            synchronized (listenClientCallbacks) {
                MockClientCall<ListenRequest, ListenResponse> mock = new MockClientCall<>();
                nextListenClientCallback.setResult(mock);
                prepareListenClientCallbacks();
                return Tasks.forResult(mock);
            }
        }

        private Task<ClientCall<WriteRequest, WriteResponse>> createWriteCallback() {
            synchronized (writeClientCallbacks) {
                MockClientCall<WriteRequest, WriteResponse> mock = new MockClientCall<>();
                nextWriteClientCallback.setResult(mock);
                prepareWriteClientCallbacks();
                return Tasks.forResult(mock);
            }
        }
    }

    public final FirebaseFirestore firestore;
    public final FirebaseFirestore.InstanceRegistry instanceRegistry = mock(FirebaseFirestore.InstanceRegistry.class);

    public FirebaseFirestoreTestFactory() {
        databaseId = DatabaseId.forDatabase("p", "d");
        prepareInstances();
        firestore = new FirebaseFirestore(
                ApplicationProvider.getApplicationContext(),
                databaseId,
                "k",
                EmptyCredentialsProvider::new,
                EmptyAppCheckTokenProvider::new,
                this::componentProvider,
                null,
                instanceRegistry,
                null
        );
        FirebaseFirestoreSettings.Builder builder = new FirebaseFirestoreSettings.Builder(firestore.getFirestoreSettings());
        builder.setLocalCacheSettings(MemoryCacheSettings.newBuilder().build());
        firestore.setFirestoreSettings(builder.build());
    }

    public void setClearPersistenceMethod(Function<Executor, Task<Void>> clearPersistenceMethod) {
        firestore.clearPersistenceMethod = clearPersistenceMethod;
    }

    private void prepareInstances() {
        nextInstance = new TaskCompletionSource<>();
        instances.add(nextInstance.getTask());
    }

    private GrpcCallProvider mockGrpcCallProvider(Instance instance) {
        GrpcCallProvider mockGrpcCallProvider = mock(GrpcCallProvider.class);
        when(mockGrpcCallProvider.createClientCall(eq(FirestoreGrpc.getListenMethod())))
                .thenAnswer(invocation -> instance.createListenCallback());
        when(mockGrpcCallProvider.createClientCall(eq(FirestoreGrpc.getWriteMethod())))
                .thenAnswer(invocation -> instance.createWriteCallback());
        instance.mockGrpcCallProvider = mockGrpcCallProvider;
        return mockGrpcCallProvider;
    }

    private ComponentProvider componentProvider(FirebaseFirestoreSettings settings) {
        Instance instance = new Instance();
        instance.componentProvider = ComponentProvider.defaultFactory(settings);
        instance.componentProvider.setRemoteProvider(new RemoteComponenetProvider() {
//            @Override
//            protected Datastore createDatastore(ComponentProvider.Configuration configuration) {
//                instance.configuration = configuration;
//                return mockDatastore(instance);
//            }
//
            @Override
            protected GrpcCallProvider createGrpcCallProvider(ComponentProvider.Configuration configuration) {
                instance.configuration = configuration;
                configuration.asyncQueue.enqueueAndForget(() -> instance.initializeComplete.setResult(null));
                return mockGrpcCallProvider(instance);
            }
        });
        TaskCompletionSource<Instance> nextInstance = this.nextInstance;
        prepareInstances();
        nextInstance.setResult(instance);
        return instance.componentProvider;
    }
}
