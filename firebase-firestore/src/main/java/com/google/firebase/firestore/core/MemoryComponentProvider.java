// Copyright 2020 Google LLC
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

package com.google.firebase.firestore.core;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.local.DefaultQueryEngine;
import com.google.firebase.firestore.local.GarbageCollectionScheduler;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.MemoryPersistence;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.remote.AndroidConnectivityMonitor;
import com.google.firebase.firestore.remote.RemoteEvent;
import com.google.firebase.firestore.remote.RemoteStore;
import io.grpc.Status;

/**
 * Provides all components needed for Firestore with in-memory persistence. Uses EagerGC garbage
 * collection.
 */
public class MemoryComponentProvider extends ComponentProvider {

  @Override
  @Nullable
  protected GarbageCollectionScheduler createGarbageCollectionScheduler(
      Configuration configuration) {
    return null;
  }

  @Override
  protected EventManager createEventManager(Configuration configuration) {
    return new EventManager(getSyncEngine());
  }

  @Override
  protected LocalStore createLocalStore(Configuration configuration) {
    return new LocalStore(
        getPersistence(), new DefaultQueryEngine(), configuration.getInitialUser());
  }

  @Override
  protected AndroidConnectivityMonitor createConnectivityMonitor(Configuration configuration) {
    return new AndroidConnectivityMonitor(configuration.getContext());
  }

  @Override
  protected Persistence createPersistence(Configuration configuration) {
    return MemoryPersistence.createEagerGcMemoryPersistence();
  }

  @Override
  protected RemoteStore createRemoteStore(Configuration configuration) {
    return new RemoteStore(
        new RemoteStoreCallback(),
        getLocalStore(),
        configuration.getDatastore(),
        configuration.getAsyncQueue(),
        getConnectivityMonitor());
  }

  @Override
  protected SyncEngine createSyncEngine(Configuration configuration) {
    return new SyncEngine(
        getLocalStore(),
        getRemoteStore(),
        configuration.getInitialUser(),
        configuration.getMaxConcurrentLimboResolutions());
  }

  /**
   * A callback interface used by RemoteStore. All calls are forwarded to SyncEngine.
   *
   * <p>This interface exists to allow RemoteStore to access functionality provided by SyncEngine
   * even though SyncEngine is created after RemoteStore.
   */
  private class RemoteStoreCallback implements RemoteStore.RemoteStoreCallback {

    @Override
    public void handleRemoteEvent(RemoteEvent remoteEvent) {
      getSyncEngine().handleRemoteEvent(remoteEvent);
    }

    @Override
    public void handleRejectedListen(int targetId, Status error) {
      getSyncEngine().handleRejectedListen(targetId, error);
    }

    @Override
    public void handleSuccessfulWrite(MutationBatchResult mutationBatchResult) {
      getSyncEngine().handleSuccessfulWrite(mutationBatchResult);
    }

    @Override
    public void handleRejectedWrite(int batchId, Status error) {
      getSyncEngine().handleRejectedWrite(batchId, error);
    }

    @Override
    public void handleOnlineStateChange(OnlineState onlineState) {
      getSyncEngine().handleOnlineStateChange(onlineState);
    }

    @Override
    public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
      return getSyncEngine().getRemoteKeysForTarget(targetId);
    }
  }
}
