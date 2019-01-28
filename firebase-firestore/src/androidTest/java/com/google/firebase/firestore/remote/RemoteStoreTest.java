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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.OnlineState;
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
            InstrumentationRegistry.getContext());
    Semaphore networkChangeSemaphore = new Semaphore(0);
    RemoteStore.RemoteStoreCallback callbacks =
        new RemoteStore.RemoteStoreCallback() {
          public void handleRemoteEvent(RemoteEvent remoteEvent) {}

          public void handleRejectedListen(int targetId, Status error) {}

          public void handleSuccessfulWrite(MutationBatchResult successfulWrite) {}

          public void handleRejectedWrite(int batchId, Status error) {}

          public void handleOnlineStateChange(OnlineState onlineState) {
            networkChangeSemaphore.release();
          }

          public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
            return null;
          }
        };

    waitForIdle(testQueue);
    FakeConnectivityMonitor connectivityMonitor = new FakeConnectivityMonitor();
    RemoteStore remoteStore = new RemoteStore(callback, null, datastore, testQueue, connectivityMonitor);

    connectivityMonitor.goOffline();
    waitFor(networkChangeSemaphore);
  }

  private void waitForIdle(AsyncQueue testQueue) {
    waitFor(testQueue.enqueue(() -> {}));
  }

  class FakeConnectivityMonitor implements ConnectivityMonitor {
    private Consumer<NetworkStatus> callback = null;

    public void addCallback(Consumer<NetworkStatus> callback) {
      this.callback = callback;
    }

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
