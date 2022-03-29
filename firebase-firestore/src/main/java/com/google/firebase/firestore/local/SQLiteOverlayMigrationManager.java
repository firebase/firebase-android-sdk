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

/** Manages overlay migrations required to have overlay support . */
public class SQLiteOverlayMigrationManager implements OverlayMigrationManager {
  private final SQLitePersistence db;

  /**
   * Creates a new data migration manager.
   *
   * @param persistence The underlying SQLite Persistence to use for data migrations.
   */
  public SQLiteOverlayMigrationManager(SQLitePersistence persistence) {
    this.db = persistence;
  }

  @Override
  public void run() {
    buildOverlays();
  }

  private void buildOverlays() {
    db.runTransaction(
        "build overlays",
        () -> {
          Set<String> userIds = getAllUserIds();
          RemoteDocumentCache remoteDocumentCache = db.getRemoteDocumentCache();
          for (String uid : userIds) {
            User user = new User(uid);
            MutationQueue mutationQueue = db.getMutationQueue(user, db.getIndexManager(user));

            // Get all document keys that have local mutations
            Set<DocumentKey> allDocumentKeys = new HashSet<>();
            List<MutationBatch> batches = mutationQueue.getAllMutationBatches();
            for (MutationBatch batch : batches) {
              allDocumentKeys.addAll(batch.getKeys());
            }

            // Recalculate and save overlays
            DocumentOverlayCache documentOverlayCache = db.getDocumentOverlayCache(user);
            LocalDocumentsView localView =
                new LocalDocumentsView(
                    remoteDocumentCache,
                    mutationQueue,
                    documentOverlayCache,
                    db.getIndexManager(user));
            localView.recalculateAndSaveOverlays(allDocumentKeys);
          }

          removePendingOverlayMigrations();
        });
  }

  private Set<String> getAllUserIds() {
    Set<String> uids = new HashSet<>();
    db.query("SELECT DISTINCT uid FROM mutation_queues").forEach(row -> uids.add(row.getString(0)));
    return uids;
  }

  @VisibleForTesting
  boolean hasPendingOverlayMigration() {
    final Boolean[] result = {false};
    db.query("SELECT migration_name FROM data_migrations")
        .forEach(
            row -> {
              try {
                if (Persistence.DATA_MIGRATION_BUILD_OVERLAYS.equals(row.getString(0))) {
                  result[0] = true;
                  return;
                }
              } catch (IllegalArgumentException e) {
                throw fail("SQLitePersistence.DataMigration failed to parse: %s", e);
              }
            });
    return result[0];
  }

  private void removePendingOverlayMigrations() {
    db.execute(
        "DELETE FROM data_migrations WHERE migration_name = ?",
        Persistence.DATA_MIGRATION_BUILD_OVERLAYS);
  }
}
