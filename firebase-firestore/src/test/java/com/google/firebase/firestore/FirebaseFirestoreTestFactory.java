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
import com.google.firebase.firestore.integration.AsyncTaskAccumulator;
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

import java.util.concurrent.Executor;

import com.google.firebase.firestore.integration.TestClientCall;

public final class FirebaseFirestoreTestFactory {

    public final DatabaseId databaseId;
    public final AsyncTaskAccumulator<Instance> instances = new AsyncTaskAccumulator<>();

    public static class Instance {
        public ComponentProvider componentProvider;
        public GrpcCallProvider mockGrpcCallProvider;

        private final AsyncTaskAccumulator<TestClientCall<ListenRequest, ListenResponse>> listens = new AsyncTaskAccumulator<>();
        private final AsyncTaskAccumulator<TestClientCall<WriteRequest, WriteResponse>> writes = new AsyncTaskAccumulator<>();
        public Task<Void> enqueue(Runnable runnable) {
            return configuration.asyncQueue.enqueue(runnable);
        }

        public ComponentProvider.Configuration configuration;

        public Task<TestClientCall<ListenRequest, ListenResponse>> getListenClient(int i) {
            return listens.get(i);
        }

        public Task<TestClientCall<WriteRequest, WriteResponse>> getWriteClient(int i) {
            return writes.get(i);
        }

    }

    public final FirebaseFirestore firestore;
    public final FirebaseFirestore.InstanceRegistry instanceRegistry = mock(FirebaseFirestore.InstanceRegistry.class);

    public FirebaseFirestoreTestFactory() {
        databaseId = DatabaseId.forDatabase("p", "d");
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

    private GrpcCallProvider mockGrpcCallProvider(Instance instance) {
        GrpcCallProvider mockGrpcCallProvider = mock(GrpcCallProvider.class);
        when(mockGrpcCallProvider.createClientCall(eq(FirestoreGrpc.getListenMethod())))
                .thenAnswer(invocation -> Tasks.forResult(new TestClientCall<>(instance.listens.next())));
        when(mockGrpcCallProvider.createClientCall(eq(FirestoreGrpc.getWriteMethod())))
                .thenAnswer(invocation -> Tasks.forResult(new TestClientCall<>(instance.writes.next())));
        instance.mockGrpcCallProvider = mockGrpcCallProvider;
        return mockGrpcCallProvider;
    }

    private ComponentProvider componentProvider(FirebaseFirestoreSettings settings) {
        TaskCompletionSource<Instance> next = instances.next();
        Instance instance = new Instance();
        instance.componentProvider = ComponentProvider.defaultFactory(settings);
        instance.componentProvider.setRemoteProvider(new RemoteComponenetProvider() {
            @Override
            protected GrpcCallProvider createGrpcCallProvider(ComponentProvider.Configuration configuration) {
                instance.configuration = configuration;
                next.setResult(instance);
                return mockGrpcCallProvider(instance);
            }
        });
        return instance.componentProvider;
    }
}
