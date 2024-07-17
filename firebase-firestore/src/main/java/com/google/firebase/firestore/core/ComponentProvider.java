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

import static com.google.firebase.firestore.util.Assert.hardAssert;
import static com.google.firebase.firestore.util.Assert.hardAssertNonNull;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.local.IndexBackfiller;
import com.google.firebase.firestore.local.LocalStore;
import com.google.firebase.firestore.local.Persistence;
import com.google.firebase.firestore.local.Scheduler;
import com.google.firebase.firestore.remote.ConnectivityMonitor;
import com.google.firebase.firestore.remote.Datastore;
import com.google.firebase.firestore.remote.GrpcMetadataProvider;
import com.google.firebase.firestore.remote.RemoteComponenetProvider;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firebase.firestore.remote.RemoteStore;
import com.google.firebase.firestore.util.AsyncQueue;

/**
 * Initializes and wires up all core components for Firestore.
 *
 * <p>Implementations provide custom components by overriding the `createX()` methods.
 */
public abstract class ComponentProvider {

  protected final FirebaseFirestoreSettings settings;
  private RemoteComponenetProvider remoteProvider = new RemoteComponenetProvider();
  private Persistence persistence;
  private LocalStore localStore;
  private SyncEngine syncEngine;
  private RemoteStore remoteStore;
  private EventManager eventManager;
  @Nullable private IndexBackfiller indexBackfiller;
  @Nullable private Scheduler garbageCollectionScheduler;

  public ComponentProvider(FirebaseFirestoreSettings settings) {
    this.settings = settings;
  }

  @NonNull
  public static ComponentProvider defaultFactory(@NonNull FirebaseFirestoreSettings settings) {
    return settings.isPersistenceEnabled()
        ? new SQLiteComponentProvider(settings)
        : new MemoryComponentProvider(settings);
  }

  /** Configuration options for the component provider. */
  public static final class Configuration {

    public final Context context;
    public final AsyncQueue asyncQueue;
    public final DatabaseInfo databaseInfo;
    public final User initialUser;
    public final int maxConcurrentLimboResolutions;
    public final CredentialsProvider<User> authProvider;
    public final CredentialsProvider<String> appCheckProvider;

    @Nullable public final GrpcMetadataProvider metadataProvider;

    public Configuration(
        Context context,
        AsyncQueue asyncQueue,
        DatabaseInfo databaseInfo,
        User initialUser,
        int maxConcurrentLimboResolutions,
        CredentialsProvider<User> authProvider,
        CredentialsProvider<String> appCheckProvider,
        @Nullable GrpcMetadataProvider metadataProvider) {
      this.context = context;
      this.asyncQueue = asyncQueue;
      this.databaseInfo = databaseInfo;
      this.initialUser = initialUser;
      this.maxConcurrentLimboResolutions = maxConcurrentLimboResolutions;
      this.authProvider = authProvider;
      this.appCheckProvider = appCheckProvider;
      this.metadataProvider = metadataProvider;
    }
  }

  @VisibleForTesting
  public void setRemoteProvider(RemoteComponenetProvider remoteProvider) {
    hardAssert(remoteStore == null, "cannot set remoteProvider after initialize");
    this.remoteProvider = remoteProvider;
  }

  public RemoteSerializer getRemoteSerializer() {
    return remoteProvider.getRemoteSerializer();
  }

  public Datastore getDatastore() {
    return remoteProvider.getDatastore();
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
    return remoteProvider.getConnectivityMonitor();
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
    remoteProvider.initialize(configuration);
    persistence = createPersistence(configuration);
    persistence.start();
    localStore = createLocalStore(configuration);
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

  protected abstract Persistence createPersistence(Configuration configuration);

  protected abstract RemoteStore createRemoteStore(Configuration configuration);

  protected abstract SyncEngine createSyncEngine(Configuration configuration);
}
