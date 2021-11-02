// Copyright 2021 Google LLC
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

import static com.google.firebase.firestore.util.Assert.fail;

import androidx.annotation.VisibleForTesting;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Manages SQLite data migration required by SDK version upgrades. */
public class SQLiteDataMigrationManager implements DataMigrationManager {
  private final SQLitePersistence db;

  /**
   * Creates a new data migration manager.
   *
   * @param persistence The underlying SQLite Persistence to use for data migrations.
   */
  public SQLiteDataMigrationManager(SQLitePersistence persistence) {
    this.db = persistence;
  }

  @Override
  public void run() {
    for (SQLitePersistence.DataMigration migration : getPendingMigrations()) {
      switch (migration) {
        case BuildOverlays:
          buildOverlays();
          break;
      }
    }
  }

  private void buildOverlays() {
    db.runTransaction(
        "build overlays",
        () -> {
          Set<String> userIds = getAllUserIds();
          for (String uid : userIds) {
            User user = new User(uid);
            RemoteDocumentCache remoteDocumentCache = db.getRemoteDocumentCache();
            MutationQueue mutationQueue = db.getMutationQueue(user);

            // Get all document keys that have local mutations
            Set<DocumentKey> allDocumentKeys = new HashSet<>();
            List<MutationBatch> batches = mutationQueue.getAllMutationBatches();
            for (MutationBatch batch : batches) {
              allDocumentKeys.addAll(batch.getKeys());
            }

            // Recalculate overlays
            DocumentOverlayCache documentOverlayCache = db.getDocumentOverlay(user);
            LocalDocumentsView localView =
                new LocalDocumentsView(
                    remoteDocumentCache, mutationQueue, documentOverlayCache, db.getIndexManager());
            localView.recalculateAndSaveOverlays(allDocumentKeys);
          }

          removePendingMigrations(SQLitePersistence.DataMigration.BuildOverlays);
        });
  }

  private Set<String> getAllUserIds() {
    Set<String> uids = new HashSet<>();
    db.query("SELECT DISTINCT uid FROM mutation_queues").forEach(row -> uids.add(row.getString(0)));
    return uids;
  }

  @VisibleForTesting
  Set<SQLitePersistence.DataMigration> getPendingMigrations() {
    Set<SQLitePersistence.DataMigration> result = new HashSet<>();
    boolean tableNotExist =
        db.query("SELECT 1=1 FROM sqlite_master WHERE tbl_name = 'data_migrations'").isEmpty();
    if (tableNotExist) {
      return result;
    }

    db.query("SELECT migration_name FROM data_migrations")
        .forEach(
            row -> {
              try {
                result.add(SQLitePersistence.DataMigration.valueOf(row.getString(0)));
              } catch (IllegalArgumentException e) {
                throw fail("SQLitePersistence.DataMigration failed to parse: %s", e);
              }
            });
    return result;
  }

  private void removePendingMigrations(SQLitePersistence.DataMigration migration) {
    db.execute("DELETE FROM data_migrations WHERE migration_name = ?", migration.name());
  }
}
