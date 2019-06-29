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

package com.google.android.datatransport.runtime.scheduling.persistence;

import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.Collections;
import javax.inject.Named;

@Module
public abstract class EventStoreModule {
  // Schema migration guidelines
  // 1. Copy the SQL statements to create the tables and change as necessary. Rename variables to
  // fit the format {}_SQL_Vn
  // 2. Move the legacy SQL statements into the LegacySQL.java test class to reference in tests.
  // 3. Write the Migration for existing clients, assign to variable name migrateToVn and add it to
  // the collection injected into DatabaseMigrationClient
  // 4. Write tests that cover the following scenarios
  //    4a. Migrating versions V1..Vn-1 to Vn
  //    4b. Bootstrapping the table at Vn

  // Note: 4. Migrations handle only upgrades. Downgrades will drop and recreate all tables/indices.
  static final String CREATE_EVENTS_SQL_V2 =
      "CREATE TABLE events "
          + "(_id INTEGER PRIMARY KEY,"
          + " context_id INTEGER NOT NULL,"
          + " transport_name TEXT NOT NULL,"
          + " timestamp_ms INTEGER NOT NULL,"
          + " uptime_ms INTEGER NOT NULL,"
          + " payload BLOB NOT NULL,"
          + " code INTEGER,"
          + " num_attempts INTEGER NOT NULL,"
          + "FOREIGN KEY (context_id) REFERENCES transport_contexts(_id) ON DELETE CASCADE)";

  static final String CREATE_EVENT_METADATA_SQL_V2 =
      "CREATE TABLE event_metadata "
          + "(_id INTEGER PRIMARY KEY,"
          + " event_id INTEGER NOT NULL,"
          + " name TEXT NOT NULL,"
          + " value TEXT NOT NULL,"
          + "FOREIGN KEY (event_id) REFERENCES events(_id) ON DELETE CASCADE)";

  static final String CREATE_CONTEXTS_SQL_V2 =
      "CREATE TABLE transport_contexts "
          + "(_id INTEGER PRIMARY KEY,"
          + " backend_name TEXT NOT NULL,"
          + " priority INTEGER NOT NULL,"
          + " next_request_ms INTEGER NOT NULL,"
          + " extras BLOB)";

  static final String CREATE_EVENT_BACKEND_INDEX_V2 =
      "CREATE INDEX events_backend_id on events(context_id)";

  static final String CREATE_CONTEXT_BACKEND_PRIORITY_EXTRAS_INDEX_V2 =
      "CREATE UNIQUE INDEX contexts_backend_priority_extras on transport_contexts(backend_name, priority, extras)";

  static final String DROP_EVENTS_SQL = "DROP TABLE events";

  static final String DROP_EVENT_METADATA_SQL = "DROP TABLE event_metadata";

  static final String DROP_CONTEXTS_SQL = "DROP TABLE transport_contexts";

  static final DatabaseMigrationClient.Migration MIGRATE_TO_V2 =
      (db, fromVersion, toVersion) -> {
        if (fromVersion == 1 && toVersion == 2) {
          db.execSQL("ALTER TABLE transport_contexts ADD COLUMN extras BLOB");
          db.execSQL(
              "CREATE UNIQUE INDEX contexts_backend_priority_extras on transport_contexts(backend_name, priority, extras)");
          db.execSQL("DROP INDEX contexts_backend_priority");
        }
      };

  @Provides
  static EventStoreConfig storeConfig() {
    return EventStoreConfig.DEFAULT;
  }

  @Binds
  abstract EventStore eventStore(SQLiteEventStore store);

  @Binds
  abstract SynchronizationGuard synchronizationGuard(SQLiteEventStore store);

  @Provides
  @Named("CREATE_EVENTS_SQL")
  static String createEventsSql() {
    return CREATE_EVENTS_SQL_V2;
  }

  @Provides
  @Named("CREATE_EVENT_METADATA_SQL")
  static String createEventMetadataSql() {
    return CREATE_EVENT_METADATA_SQL_V2;
  }

  @Provides
  @Named("CREATE_CONTEXTS_SQL")
  static String createContextsSql() {
    return CREATE_CONTEXTS_SQL_V2;
  }

  @Provides
  @Named("CREATE_EVENT_BACKEND_INDEX")
  static String getCreateEventBackendIndex() {
    return CREATE_EVENT_BACKEND_INDEX_V2;
  }

  @Provides
  @Named("CREATE_CONTEXT_BACKEND_PRIORITY_INDEX")
  static String createEventBackendPriorityIndex() {
    return CREATE_CONTEXT_BACKEND_PRIORITY_EXTRAS_INDEX_V2;
  }

  @Provides
  @Named("DROP_EVENTS_SQL")
  static String dropEventsSQL() {
    return DROP_EVENTS_SQL;
  }

  @Provides
  @Named("DROP_EVENT_METADATA_SQL")
  static String dropEventMetadataSql() {
    return DROP_EVENT_METADATA_SQL;
  }

  @Provides
  @Named("DROP_CONTEXTS_SQL")
  static String dropContextsSql() {
    return DROP_CONTEXTS_SQL;
  }

  @Provides
  static DatabaseMigrationClient createDatabaseMigrationClient() {
    return new DatabaseMigrationClient(Collections.singletonList(MIGRATE_TO_V2));
  }
}
