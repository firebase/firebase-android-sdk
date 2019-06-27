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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import javax.inject.Inject;
import javax.inject.Named;

final class SchemaManager extends SQLiteOpenHelper {
  // TODO: when we do schema upgrades in the future we need to make sure both downgrades and
  // upgrades work as expected, e.g. `up+down+up` is equivalent to `up`.
  private static int SCHEMA_VERSION = 1;
  private static final String DB_NAME = "com.google.android.datatransport.events";
  private final String createEventsSql;
  private final String createEventMetadataSql;
  private final String createContextsSql;

  private static final String CREATE_EVENT_BACKEND_INDEX =
      "CREATE INDEX events_backend_id on events(context_id)";

  private static final String CREATE_CONTEXT_BACKEND_PRIORITY_INDEX =
      "CREATE UNIQUE INDEX contexts_backend_priority on transport_contexts(backend_name, priority)";

  private boolean configured = false;

  @Inject
  SchemaManager(
      Context context,
      @Named("CREATE_EVENTS_SQL") String createEventsSql,
      @Named("CREATE_EVENT_METADATA_SQL") String createEventMetadataSql,
      @Named("CREATE_CONTEXTS_SQL") String createContextsSql) {
    super(context, DB_NAME, null, SCHEMA_VERSION);
    this.createEventsSql = createEventsSql;
    this.createEventMetadataSql = createEventMetadataSql;
    this.createContextsSql = createContextsSql;
  }

  @Override
  public void onConfigure(SQLiteDatabase db) {
    // Note that this is only called automatically by the SQLiteOpenHelper base class on Jelly
    // Bean and above.
    configured = true;

    db.rawQuery("PRAGMA busy_timeout=0;", new String[0]).close();

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      db.setForeignKeyConstraintsEnabled(true);
    }
  }

  private void ensureConfigured(SQLiteDatabase db) {
    if (!configured) {
      onConfigure(db);
    }
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    ensureConfigured(db);
    db.execSQL(createEventsSql);
    db.execSQL(createEventMetadataSql);
    db.execSQL(createContextsSql);
    db.execSQL(CREATE_EVENT_BACKEND_INDEX);
    db.execSQL(CREATE_CONTEXT_BACKEND_PRIORITY_INDEX);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    ensureConfigured(db);
  }

  @Override
  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    ensureConfigured(db);
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    ensureConfigured(db);
  }
}
