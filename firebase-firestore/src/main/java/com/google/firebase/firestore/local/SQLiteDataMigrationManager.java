package com.google.firebase.firestore.local;

import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SQLiteDataMigrationManager implements DataMigrationManager {
  private final SQLitePersistence db;
  private final Set<SQLitePersistence.DataMigration> migrations;

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
    Set<String> userIds = getAllUserIds();
    for (String uid : userIds) {
      User user = new User(uid);
      RemoteDocumentCache remoteDocumentCache = db.getRemoteDocumentCache();
      MutationQueue mutationQueue = db.getMutationQueue(user);
      DocumentOverlayCache documentOverlayCache = db.getDocumentOverlay(user);
      LocalDocumentsView localView =
          new LocalDocumentsView(
              remoteDocumentCache, mutationQueue, documentOverlayCache, db.getIndexManager());
      Set<DocumentKey> allDocumentKeys = new HashSet<>();
      List<MutationBatch> batches = mutationQueue.getAllMutationBatches();
      for (MutationBatch batch : batches) {
        allDocumentKeys.addAll(batch.getKeys());
      }

      localView.recalculateOverlays(allDocumentKeys);
    }
  }

  private Set<String> getAllUserIds() {
    Set<String> uids = new HashSet<>();
    db.query("SELECT DISTINCT uid FROM mutation_queues").forEach(row -> uids.add(row.getString(0)));
    return uids;
  }
}
