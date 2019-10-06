// Copyright 2019 Google LLC
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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.OnlineState;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.MemoryPersistence;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.SimpleQueryEngine;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Consumer;
import io.grpc.Status;
import java.util.concurrent.Semaphore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RemoteStoreTest {
  @Test
  public void testRemoteStoreStreamStopsWhenNetworkUnreachable() {
    AsyncQueue testQueue = new AsyncQueue();
    Datastore datastore =
        new Datastore(
            IntegrationTestUtil.testEnvDatabaseInfo(),
            testQueue,
            null,
            ApplicationProvider.getApplicationContext(),
            null);
    Semaphore networkChangeSemaphore = new Semaphore(0);
    RemoteStore.RemoteStoreCallback callback =
        new RemoteStore.RemoteStoreCallback() {
          @Override
          public void handleRemoteEvent(RemoteEvent remoteEvent) {}

          @Override
          public void handleRejectedListen(int targetId, Status error) {}

          @Override
          public void handleSuccessfulWrite(MutationBatchResult successfulWrite) {}

          @Override
          public void handleRejectedWrite(int batchId, Status error) {}

          @Override
          public void handleOnlineStateChange(OnlineState onlineState) {
            networkChangeSemaphore.release();
          }

          @Override
          public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
            return null;
          }
        };

    FakeConnectivityMonitor connectivityMonitor = new FakeConnectivityMonitor();
    SimpleQueryEngine queryEngine = new SimpleQueryEngine();
    Persistence persistence = MemoryPersistence.createEagerGcMemoryPersistence();
    persistence.start();
    LocalStore localStore = new LocalStore(persistence, queryEngine, User.UNAUTHENTICATED);
    RemoteStore remoteStore =
        new RemoteStore(callback, localStore, datastore, testQueue, connectivityMonitor);

    waitFor(testQueue.enqueue(() -> remoteStore.forceEnableNetwork()));
    drain(testQueue);
    networkChangeSemaphore.drainPermits();

    connectivityMonitor.goOffline();
    waitFor(networkChangeSemaphore);
    drain(testQueue);

    waitFor(testQueue.enqueue(() -> remoteStore.forceEnableNetwork()));
    networkChangeSemaphore.drainPermits();
    connectivityMonitor.goOnline();
    waitFor(networkChangeSemaphore);
  }

  private void drain(AsyncQueue testQueue) {
    waitFor(testQueue.enqueue(() -> {}));
  }

  static class FakeConnectivityMonitor implements ConnectivityMonitor {
    private Consumer<NetworkStatus> callback = null;

    @Override
    public void addCallback(Consumer<NetworkStatus> callback) {
      this.callback = callback;
    }

    @Override
    public void shutdown() {}

    public void goOffline() {
      if (callback != null) {
        callback.accept(ConnectivityMonitor.NetworkStatus.UNREACHABLE);
      }
    }

    public void goOnline() {
      if (callback != null) {
        callback.accept(ConnectivityMonitor.NetworkStatus.REACHABLE);
      }
    }
  }
}
