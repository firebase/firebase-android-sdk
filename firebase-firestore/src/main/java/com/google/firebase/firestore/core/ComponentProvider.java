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

import static com.google.firebase.firestore.util.Assert.hardAssertNonNull;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.local.IndexBackfiller;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.Scheduler;
import com.google.firebase.firestore.remote.ConnectivityMonitor;
import com.google.firebase.firestore.remote.Datastore;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;

/**
 * Initializes and wires up all core components for Firestore.
 *
 * <p>Implementations provide custom components by overriding the `createX()` methods.
 */
public abstract class ComponentProvider {

  private Persistence persistence;
  private LocalStore localStore;
  private SyncEngine syncEngine;
  private RemoteStore remoteStore;
  private EventManager eventManager;
  private ConnectivityMonitor connectivityMonitor;
  @Nullable private IndexBackfiller indexBackfiller;
  @Nullable private Scheduler garbageCollectionScheduler;

  /** Configuration options for the component provider. */
  public static class Configuration {

    private final Context context;
    private final AsyncQueue asyncQueue;
    private final DatabaseInfo databaseInfo;
    private final Datastore datastore;
    private final User initialUser;
    private final int maxConcurrentLimboResolutions;
    private final FirebaseFirestoreSettings settings;

    public Configuration(
        Context context,
        AsyncQueue asyncQueue,
        DatabaseInfo databaseInfo,
        Datastore datastore,
        User initialUser,
        int maxConcurrentLimboResolutions,
        FirebaseFirestoreSettings settings) {
      this.context = context;
      this.asyncQueue = asyncQueue;
      this.databaseInfo = databaseInfo;
      this.datastore = datastore;
      this.initialUser = initialUser;
      this.maxConcurrentLimboResolutions = maxConcurrentLimboResolutions;
      this.settings = settings;
    }

    FirebaseFirestoreSettings getSettings() {
      return settings;
    }

    AsyncQueue getAsyncQueue() {
      return asyncQueue;
    }

    DatabaseInfo getDatabaseInfo() {
      return databaseInfo;
    }

    Datastore getDatastore() {
      return datastore;
    }

    User getInitialUser() {
      return initialUser;
    }

    int getMaxConcurrentLimboResolutions() {
      return maxConcurrentLimboResolutions;
    }

    Context getContext() {
      return context;
    }
  }

  public Persistence getPersistence() {
    return hardAssertNonNull(persistence, "persistence not initialized yet");
  }

  @Nullable
  public Scheduler getGarbageCollectionScheduler() {
    return garbageCollectionScheduler;
  }

  @Nullable
  public IndexBackfiller getIndexBackfiller() {
    return indexBackfiller;
  }

  public LocalStore getLocalStore() {
    return hardAssertNonNull(localStore, "localStore not initialized yet");
  }

  public SyncEngine getSyncEngine() {
    return hardAssertNonNull(syncEngine, "syncEngine not initialized yet");
  }

  public RemoteStore getRemoteStore() {
    return hardAssertNonNull(remoteStore, "remoteStore not initialized yet");
  }

  public EventManager getEventManager() {
    return hardAssertNonNull(eventManager, "eventManager not initialized yet");
  }

  protected ConnectivityMonitor getConnectivityMonitor() {
    return hardAssertNonNull(connectivityMonitor, "connectivityMonitor not initialized yet");
  }

  public void initialize(Configuration configuration) {
    /**
     * The order in which components are created is important.
     *
     * <p>The implementation of abstract createX methods (for example createRemoteStore) will call
     * the getX methods (for example getLocalStore). Consequently, creating components out of order
     * will cause createX method to fail because a dependency is null.
     *
     * <p>To catch incorrect order, all getX methods have runtime check for null.
     */
    persistence = createPersistence(configuration);
    persistence.start();
    localStore = createLocalStore(configuration);
    connectivityMonitor = createConnectivityMonitor(configuration);
    remoteStore = createRemoteStore(configuration);
    syncEngine = createSyncEngine(configuration);
    eventManager = createEventManager(configuration);
    localStore.start();
    remoteStore.start();
    garbageCollectionScheduler = createGarbageCollectionScheduler(configuration);
    indexBackfiller = createIndexBackfiller(configuration);
  }

  protected abstract Scheduler createGarbageCollectionScheduler(Configuration configuration);

  protected abstract IndexBackfiller createIndexBackfiller(Configuration configuration);

  protected abstract EventManager createEventManager(Configuration configuration);

  protected abstract LocalStore createLocalStore(Configuration configuration);

  protected abstract ConnectivityMonitor createConnectivityMonitor(Configuration configuration);

  protected abstract Persistence createPersistence(Configuration configuration);

  protected abstract RemoteStore createRemoteStore(Configuration configuration);

  protected abstract SyncEngine createSyncEngine(Configuration configuration);
}
