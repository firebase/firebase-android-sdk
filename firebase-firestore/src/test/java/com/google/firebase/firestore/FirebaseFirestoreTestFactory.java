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
import com.google.firebase.firestore.integration.TestClientCall;

public final class FirebaseFirestoreTestFactory {

    public final DatabaseId databaseId;

    private TaskCompletionSource<Instance> nextInstance;
    public final List<Task<Instance>> instances = new ArrayList<>();

    public static class Instance {
        public ComponentProvider componentProvider;
        public GrpcCallProvider mockGrpcCallProvider;
        private TaskCompletionSource<TestClientCall<ListenRequest, ListenResponse>> nextListen;
        public final List<Task<TestClientCall<ListenRequest, ListenResponse>>> listens = new ArrayList<>();
        private TaskCompletionSource<TestClientCall<WriteRequest, WriteResponse>> nextWrite;
        public final List<Task<TestClientCall<WriteRequest, WriteResponse>>> writes = new ArrayList<>();

        private final TaskCompletionSource<Void> initializeComplete;
        public final Task<Void> initializeCompleteTask;

        public Task<Void> enqueue(Runnable runnable) {
            return configuration.asyncQueue.enqueue(runnable);
        }

        public ComponentProvider.Configuration configuration;

        public Instance() {
            nextListen = new TaskCompletionSource<>();
            listens.add(nextListen.getTask());
            nextWrite = new TaskCompletionSource<>();
            writes.add(nextWrite.getTask());
            initializeComplete = new TaskCompletionSource<>();
            initializeCompleteTask = initializeComplete.getTask();
        }

        private Task<ClientCall<ListenRequest, ListenResponse>> createListenCallback() {
            synchronized (listens) {
                TestClientCall<ListenRequest, ListenResponse> mock = new TestClientCall<>(nextListen);
                nextListen = new TaskCompletionSource<>();
                listens.add(nextListen.getTask());
                return Tasks.forResult(mock);
            }
        }

        private Task<ClientCall<WriteRequest, WriteResponse>> createWriteCallback() {
            synchronized (writes) {
                TestClientCall<WriteRequest, WriteResponse> mock = new TestClientCall<>(nextWrite);
                nextWrite = new TaskCompletionSource<>();
                writes.add(nextWrite.getTask());
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
