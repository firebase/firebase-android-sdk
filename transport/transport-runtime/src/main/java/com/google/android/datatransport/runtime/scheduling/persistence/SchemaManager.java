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

final class SchemaManager extends SQLiteOpenHelper {
  // TODO: when we do schema upgrades in the future we need to make sure both downgrades and
  // upgrades work as expected, e.g. `up+down+up` is equivalent to `up`.
  private static int SCHEMA_VERSION = 1;
  private static final String DB_NAME = "com.google.android.datatransport.events";
  private final DatabaseBootstrapClient bootstrapClient;
  private final DatabaseMigrationClient migrationClient;
  private boolean configured = false;

  @Inject
  SchemaManager(
      Context context,
      DatabaseBootstrapClient bootstrapClient,
      DatabaseMigrationClient migrationClient) {
    super(context, DB_NAME, null, SCHEMA_VERSION);
    this.bootstrapClient = bootstrapClient;
    this.migrationClient = migrationClient;
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
    bootstrapClient.bootstrap(db);
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    ensureConfigured(db);
    migrationClient.upgrade(db, oldVersion, newVersion);
  }

  @Override
  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    bootstrapClient.teardown(db);
    onCreate(db);
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    ensureConfigured(db);
  }
}
