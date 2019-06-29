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

package com.google.android.datatransport.runtime.scheduling.persistence;

/**
 * Contains SQL strings that were used to bootstrap databases at previous versions.
 *
 * <p>Preserving these helps us simulate database state at previous versions and test migrations.
 */
class LegacySQL {
  static final String CREATE_EVENTS_SQL_V1 =
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

  static final String CREATE_EVENT_METADATA_SQL_V1 =
      "CREATE TABLE event_metadata "
          + "(_id INTEGER PRIMARY KEY,"
          + " event_id INTEGER NOT NULL,"
          + " name TEXT NOT NULL,"
          + " value TEXT NOT NULL,"
          + "FOREIGN KEY (event_id) REFERENCES events(_id) ON DELETE CASCADE)";

  static final String CREATE_CONTEXTS_SQL_V1 =
      "CREATE TABLE transport_contexts "
          + "(_id INTEGER PRIMARY KEY,"
          + " backend_name TEXT NOT NULL,"
          + " priority INTEGER NOT NULL,"
          + " next_request_ms INTEGER NOT NULL)";

  static final String CREATE_EVENT_BACKEND_INDEX_V1 =
      "CREATE INDEX events_backend_id on events(context_id)";

  static final String CREATE_CONTEXT_BACKEND_PRIORITY_INDEX_V1 =
      "CREATE UNIQUE INDEX contexts_backend_priority on transport_contexts(backend_name, priority)";
}
