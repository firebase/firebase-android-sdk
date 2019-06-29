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

import android.database.sqlite.SQLiteDatabase;
import javax.inject.Inject;
import javax.inject.Named;

class DatabaseBootstrapClient {
  private final String createContextsSql;
  private final String createEventsSql;
  private final String createEventMetadataSql;
  private final String createEventBackendIndex;
  private final String createContextBackedPriorityIndex;

  private final String dropEventsSql;
  private final String dropEventMetadataSql;
  private final String dropContextsSql;

  @Inject
  DatabaseBootstrapClient(
      @Named("CREATE_EVENTS_SQL") String createEventsSql,
      @Named("CREATE_EVENT_METADATA_SQL") String createEventMetadataSql,
      @Named("CREATE_CONTEXTS_SQL") String createContextsSql,
      @Named("CREATE_EVENT_BACKEND_INDEX") String createEventBackendIndex,
      @Named("CREATE_CONTEXT_BACKEND_PRIORITY_INDEX") String createContextBackendPriorityIndex,
      @Named("DROP_EVENTS_SQL") String dropEventsSql,
      @Named("DROP_EVENT_METADATA_SQL") String dropEventMetadataSql,
      @Named("DROP_CONTEXTS_SQL") String dropContextsSql) {
    this.createEventsSql = createEventsSql;
    this.createEventMetadataSql = createEventMetadataSql;
    this.createContextsSql = createContextsSql;
    this.createEventBackendIndex = createEventBackendIndex;
    this.createContextBackedPriorityIndex = createContextBackendPriorityIndex;
    this.dropEventsSql = dropEventsSql;
    this.dropEventMetadataSql = dropEventMetadataSql;
    this.dropContextsSql = dropContextsSql;
  }

  void bootstrap(SQLiteDatabase db) {
    db.execSQL(createEventsSql);
    db.execSQL(createEventMetadataSql);
    db.execSQL(createContextsSql);
    db.execSQL(createEventBackendIndex);
    db.execSQL(createContextBackedPriorityIndex);
  }

  void teardown(SQLiteDatabase db) {
    db.execSQL(dropEventsSql);
    db.execSQL(dropEventMetadataSql);
    db.execSQL(dropContextsSql);
    // Indices are dropped automatically when the tables are dropped
  }
}
