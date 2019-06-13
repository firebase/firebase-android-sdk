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

package com.google.firebase.segmentation;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.firebase.FirebaseApp;

class CustomInstallationIdMappingCache {

  // Status of each cache entry
  enum CacheStatus {
    // Cache entry is synced to Firebase backend
    SYNCED,
    // Cache entry is waiting for Firebase backend response or pending internal retry for retryable
    // errors.
    PENDING,
    // Cache entry is not accepted by Firebase backend.
    ERROR
  }

  private static final String LOCAL_DB_NAME = "CustomInstallationIdCache";
  private static final String TABLE_NAME = "InstallationIdMapping";

  private static final String GMP_APP_ID_COLUMN_NAME = "GmpAppId";
  private static final String FIREBASE_APP_NAME_COLUMN_NAME = "AppName";
  private static final String INSTANCE_ID_COLUMN_NAME = "Iid";
  private static final String CUSTOM_INSTALLATION_ID_COLUMN_NAME = "Cid";
  private static final String CACHE_STATUS_COLUMN = "Status";

  private static final String QUERY_WHERE_CLAUSE =
      String.format(
          "%s = ? " + "AND " + "%s = ?", GMP_APP_ID_COLUMN_NAME, FIREBASE_APP_NAME_COLUMN_NAME);

  private final SQLiteDatabase localDb;

  CustomInstallationIdMappingCache() {
    // Since different FirebaseApp in the same Android application should have the same application
    // context and same dir path, so that use the context of the default FirebaseApp to create/open
    // the database.
    localDb =
        SQLiteDatabase.openOrCreateDatabase(
            FirebaseApp.getInstance()
                    .getApplicationContext()
                    .getNoBackupFilesDir()
                    .getAbsolutePath()
                + "/"
                + LOCAL_DB_NAME,
            null);

    localDb.execSQL(
        String.format(
            "CREATE TABLE IF NOT EXISTS %s(%s TEXT PRIMARY KEY, %s TEXT PRIMARY KEY, "
                + "%s TEXT NOT NULL, %s TEXT NOT NULL, %s INTEGER NOT NULL);",
            TABLE_NAME,
            GMP_APP_ID_COLUMN_NAME,
            FIREBASE_APP_NAME_COLUMN_NAME,
            INSTANCE_ID_COLUMN_NAME,
            CUSTOM_INSTALLATION_ID_COLUMN_NAME,
            CACHE_STATUS_COLUMN));
  }

  @Nullable
  String readIid(FirebaseApp firebaseApp) {
    String gmpAppId = firebaseApp.getOptions().getApplicationId();
    String appName = firebaseApp.getName();
    Cursor cursor =
        localDb.query(
            TABLE_NAME,
            new String[] {INSTANCE_ID_COLUMN_NAME},
            QUERY_WHERE_CLAUSE,
            new String[] {gmpAppId, appName},
            null,
            null,
            null);
    String iid = null;
    while (cursor.moveToNext()) {
      Preconditions.checkArgument(
          iid == null, "Multiple iid found for " + "firebase app %s", appName);
      iid = cursor.getString(cursor.getColumnIndex(INSTANCE_ID_COLUMN_NAME));
    }
    return iid;
  }
}
