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
import com.google.firebase.firestore.integration.TestClientCall;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.remote.GrpcCallProvider;
import com.google.firebase.firestore.remote.RemoteComponenetProvider;
import com.google.firebase.firestore.testutil.EmptyAppCheckTokenProvider;
import com.google.firebase.firestore.testutil.EmptyCredentialsProvider;
import com.google.firestore.v1.FirestoreGrpc;
import com.google.firestore.v1.ListenRequest;
import com.google.firestore.v1.ListenResponse;
import com.google.firestore.v1.WriteRequest;
import com.google.firestore.v1.WriteResponse;

/**
 * Factory for producing FirebaseFirestore instances that has mocked gRPC layer.
 *
 * <ol>
 *  <li>Response protos from server can be faked.
 *  <li>Request protos from SDK can be verified.
 * </ol>
 *
 * <p>
 * The FirebaseFirestoreIntegrationTestFactory is located in this package to gain package-private
 * access to FirebaseFirestore methods.
 */
public final class FirebaseFirestoreIntegrationTestFactory {

  /**
   * Everytime the `componentProviderFactory` on FirebaseFirestore is run, a new instance is added.
   */
  public final AsyncTaskAccumulator<Instance> instances = new AsyncTaskAccumulator<>();

  /**
   * Instance of Firestore components.
   */
  public static class Instance {

    /** Instance of ComponentProvider */
    public final ComponentProvider componentProvider;

    /** Every listen stream created is captured here. */
    private final AsyncTaskAccumulator<TestClientCall<ListenRequest, ListenResponse>> listens =
        new AsyncTaskAccumulator<>();

    /** Every write stream created is captured here. */
    private final AsyncTaskAccumulator<TestClientCall<WriteRequest, WriteResponse>> writes =
        new AsyncTaskAccumulator<>();

    private Instance(ComponentProvider componentProvider) {
      this.componentProvider = componentProvider;
    }

    /**
     * Queues work on AsyncQueue. This is required when faking responses from server since they
     * must be handled through the AsyncQueue of the FirestoreClient.
     */
    public Task<Void> enqueue(Runnable runnable) {
      return configuration.asyncQueue.enqueue(runnable);
    }

    /**
     * Configuration passed to `ComponentProvider`
     *
     * <p>
     * This is never null because `Task<Instance>` completes after initialization. The
     * `FirebaseFirestoreIntegrationTestFactory` will set `Instance.configuration` from within
     * the ComponentProvider override.
     */
    private ComponentProvider.Configuration configuration;

    /** Every listen stream created */
    public Task<TestClientCall<ListenRequest, ListenResponse>> getListenClient(int i) {
      return listens.get(i);
    }

    /** Every write stream created */
    public Task<TestClientCall<WriteRequest, WriteResponse>> getWriteClient(int i) {
      return writes.get(i);
    }
  }

  /**
   * The FirebaseFirestore instance.
   */
  public final FirebaseFirestore firestore;

  /**
   * Mockito Mock of `FirebaseFirestore.InstanceRegistry` that was passed into FirebaseFirestore
   * constructor.
   */
  public final FirebaseFirestore.InstanceRegistry instanceRegistry =
      mock(FirebaseFirestore.InstanceRegistry.class);

  public FirebaseFirestoreIntegrationTestFactory(DatabaseId databaseId) {
    firestore =
        new FirebaseFirestore(
            ApplicationProvider.getApplicationContext(),
            databaseId,
            "k",
            new EmptyCredentialsProvider(),
            new EmptyAppCheckTokenProvider(),
            this::componentProvider,
            null,
            instanceRegistry,
            null);
  }

  public void useMemoryCache() {
    FirebaseFirestoreSettings.Builder builder =
        new FirebaseFirestoreSettings.Builder(firestore.getFirestoreSettings());
    builder.setLocalCacheSettings(MemoryCacheSettings.newBuilder().build());
    firestore.setFirestoreSettings(builder.build());
  }

  private GrpcCallProvider mockGrpcCallProvider(Instance instance) {
    GrpcCallProvider mockGrpcCallProvider = mock(GrpcCallProvider.class);
    when(mockGrpcCallProvider.createClientCall(eq(FirestoreGrpc.getListenMethod())))
        .thenAnswer(invocation -> Tasks.forResult(new TestClientCall<>(instance.listens.next())));
    when(mockGrpcCallProvider.createClientCall(eq(FirestoreGrpc.getWriteMethod())))
        .thenAnswer(invocation -> Tasks.forResult(new TestClientCall<>(instance.writes.next())));
    return mockGrpcCallProvider;
  }

  private ComponentProvider componentProvider(FirebaseFirestoreSettings settings) {
    TaskCompletionSource<Instance> next = instances.next();
    ComponentProvider componentProvider = ComponentProvider.defaultFactory(settings);
    Instance instance = new Instance(componentProvider);
    componentProvider.setRemoteProvider(
        new RemoteComponenetProvider() {
          @Override
          protected GrpcCallProvider createGrpcCallProvider(
              ComponentProvider.Configuration configuration) {
            instance.configuration = configuration;
            next.setResult(instance);
            return mockGrpcCallProvider(instance);
          }
        });
    return componentProvider;
  }
}
