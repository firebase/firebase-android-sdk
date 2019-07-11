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

package com.google.firebase.firestore.local;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.remote.RemoteSerializer;

public final class PersistenceTestHelpers {

  /** A counter for generating unique database names. */
  private static int databaseNameCounter = 0;

  public static String nextSQLiteDatabaseName() {
    return "test-" + databaseNameCounter++;
  }

  public static SQLitePersistence createSQLitePersistence(String name) {
    return openSQLitePersistence(
        name, StatsCollector.NO_OP_STATS_COLLECTOR, LruGarbageCollector.Params.Default());
  }
  /**
   * Creates and starts a new SQLitePersistence instance for testing.
   *
   * @return a new SQLitePersistence with an empty database and an up-to-date schema.
   */
  public static SQLitePersistence createSQLitePersistence() {
    return createSQLitePersistence(LruGarbageCollector.Params.Default());
  }

  public static SQLitePersistence createSQLitePersistence(StatsCollector statsCollector) {
    return openSQLitePersistence(
        nextSQLiteDatabaseName(), statsCollector, LruGarbageCollector.Params.Default());
  }

  public static SQLitePersistence createSQLitePersistence(LruGarbageCollector.Params params) {
    // Robolectric's test runner will clear out the application database directory in between test
    // cases, but sometimes (particularly the spec tests) we create multiple databases per test
    // case and each should be fresh. A unique name is sufficient to keep these separate.
    return openSQLitePersistence(
        nextSQLiteDatabaseName(), StatsCollector.NO_OP_STATS_COLLECTOR, params);
  }

  /** Creates and starts a new MemoryPersistence instance for testing. */
  public static MemoryPersistence createEagerGCMemoryPersistence() {
    MemoryPersistence persistence =
        MemoryPersistence.createEagerGcMemoryPersistence(StatsCollector.NO_OP_STATS_COLLECTOR);
    persistence.start();
    return persistence;
  }

  public static MemoryPersistence createEagerGCMemoryPersistence(StatsCollector statsCollector) {
    MemoryPersistence persistence =
        MemoryPersistence.createEagerGcMemoryPersistence(statsCollector);
    persistence.start();
    return persistence;
  }

  public static MemoryPersistence createLRUMemoryPersistence() {
    return createLRUMemoryPersistence(LruGarbageCollector.Params.Default());
  }

  public static MemoryPersistence createLRUMemoryPersistence(LruGarbageCollector.Params params) {
    DatabaseId databaseId = DatabaseId.forProject("projectId");
    LocalSerializer serializer = new LocalSerializer(new RemoteSerializer(databaseId));
    MemoryPersistence persistence =
        MemoryPersistence.createLruGcMemoryPersistence(
            params, StatsCollector.NO_OP_STATS_COLLECTOR, serializer);
    persistence.start();
    return persistence;
  }

  private static SQLitePersistence openSQLitePersistence(
      String name, StatsCollector statsCollector, LruGarbageCollector.Params params) {
    DatabaseId databaseId = DatabaseId.forProject("projectId");
    LocalSerializer serializer = new LocalSerializer(new RemoteSerializer(databaseId));
    Context context = ApplicationProvider.getApplicationContext();
    SQLitePersistence persistence =
        new SQLitePersistence(context, name, databaseId, serializer, statsCollector, params);
    persistence.start();
    return persistence;
  }
}
