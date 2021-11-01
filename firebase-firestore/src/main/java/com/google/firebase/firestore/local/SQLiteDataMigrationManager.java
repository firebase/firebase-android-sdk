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

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Manages SQLite data migration required by SDK version upgrades. */
public class SQLiteDataMigrationManager implements DataMigrationManager {
  private final SQLitePersistence db;
  private final Set<SQLitePersistence.DataMigration> migrations;

  /**
   * Creates a new data migration manager.
   *
   * @param persistence The underlying SQLite Persistence to use for data migrations.
   * @param migrations The set of migrates needed to run.
   */
  public SQLiteDataMigrationManager(
      SQLitePersistence persistence, Set<SQLitePersistence.DataMigration> migrations) {
    this.db = persistence;
    this.migrations = migrations;
  }

  @Override
  public void run() {
    for (SQLitePersistence.DataMigration migration : migrations) {
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
            localView.recalculateOverlays(allDocumentKeys);
          }
        });
  }

  private Set<String> getAllUserIds() {
    Set<String> uids = new HashSet<>();
    db.query("SELECT DISTINCT uid FROM mutation_queues").forEach(row -> uids.add(row.getString(0)));
    return uids;
  }
}
