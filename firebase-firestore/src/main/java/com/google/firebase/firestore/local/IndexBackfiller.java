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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.index.IndexEntry;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Implements the steps for backfilling indexes. */
public class IndexBackfiller {
  /** How long we wait to try running index backfill after SDK initialization. */
  private static final long INITIAL_BACKFILL_DELAY_MS = TimeUnit.SECONDS.toMillis(15);
  /** Minimum amount of time between backfill checks, after the first one. */
  private static final long REGULAR_BACKFILL_DELAY_MS = TimeUnit.MINUTES.toMillis(1);
  /** The number of field indexes to process every time backfill() is called. */
  private static final int MAX_FIELD_INDEXES_TO_PROCESS = 10;
  /** The number of documents to query for a collection. */
  private static final int DOCUMENTS_TO_QUERY_LIMIT = 100;
  /** The maximum number of entries to write each time backfill() is called. */
  private static final int MAX_DOCUMENTS_TO_PROCESS = 1000;

  private final SQLitePersistence persistence;
  private final SQLiteIndexManager indexManager;

  /** List of all FieldIndexes configured by the user */
  private List<FieldIndex> fieldIndexQueue = new ArrayList<>();

  /** The index id of the last field index loaded from the configuration table. */
  private int lastFieldIndexId = 0;

  private int maxFieldIndexesToProcess = MAX_FIELD_INDEXES_TO_PROCESS;

  public IndexBackfiller(SQLitePersistence sqLitePersistence) {
    this.persistence = sqLitePersistence;
    this.indexManager = (SQLiteIndexManager) sqLitePersistence.getIndexManager();
  }

  public static class Results {
    private final boolean hasRun;

    private final int entriesAdded;
    private final int entriesRemoved;

    static IndexBackfiller.Results DidNotRun() {
      return new IndexBackfiller.Results(/* hasRun= */ false, 0, 0);
    }

    Results(boolean hasRun, int entriesAdded, int entriesRemoved) {
      this.hasRun = hasRun;
      this.entriesAdded = entriesAdded;
      this.entriesRemoved = entriesRemoved;
    }

    public boolean hasRun() {
      return hasRun;
    }

    public int getEntriesAdded() {
      return entriesAdded;
    }

    public int getEntriesRemoved() {
      return entriesRemoved;
    }
  }

  public class BackfillScheduler implements Scheduler {
    private final AsyncQueue asyncQueue;
    private final LocalStore localStore;
    private boolean hasRun = false;
    @Nullable private AsyncQueue.DelayedTask backfillTask;

    public BackfillScheduler(AsyncQueue asyncQueue, LocalStore localStore) {
      this.asyncQueue = asyncQueue;
      this.localStore = localStore;
    }

    @Override
    public void start() {
      scheduleBackfill();
    }

    @Override
    public void stop() {
      if (backfillTask != null) {
        backfillTask.cancel();
      }
    }

    private void scheduleBackfill() {
      long delay = hasRun ? REGULAR_BACKFILL_DELAY_MS : INITIAL_BACKFILL_DELAY_MS;
      backfillTask =
          asyncQueue.enqueueAfterDelay(
              AsyncQueue.TimerId.INDEX_BACKFILL,
              delay,
              () -> {
                localStore.backfillIndexes(IndexBackfiller.this);
                hasRun = true;
                scheduleBackfill();
              });
    }
  }

  public BackfillScheduler newScheduler(AsyncQueue asyncQueue, LocalStore localStore) {
    return new BackfillScheduler(asyncQueue, localStore);
  }

  public Results backfill(LocalStore localStore) {
    // TODO(indexing): Handle field indexes that are removed by the user.
    fetchNewFieldIndexes();

    int numIndexesWritten = writeIndexEntries(localStore);
    int numIndexesRemoved = 0;
    return new Results(/* hasRun= */ true, numIndexesWritten, numIndexesRemoved);
  }

  private void fetchNewFieldIndexes() {
    List<FieldIndex> fieldIndexes = indexManager.getFieldIndexes(lastFieldIndexId + 1);

    if (fieldIndexes.size() > 0) {
      fieldIndexQueue.addAll(fieldIndexes);

      // Since index configuration entries are sorted by index id, we can set the last fetched id to
      // the id of the last field index in the array.
      lastFieldIndexId = fieldIndexes.get(fieldIndexes.size() - 1).getIndexId();
    }
  }

  /** Writes index entries based on the FieldIndexQueue. Returns the number of entries written. */
  private int writeIndexEntries(LocalStore localStore) {
    int numIndexesWritten = 0;
    SnapshotVersion lastReadTime = null;

    // TODO(indexing): Track the number of entries written and process more field indexes if
    // we're under the maximum.
    int lastArrayIndex = Math.min(maxFieldIndexesToProcess, fieldIndexQueue.size());
    List<FieldIndex> fieldIndexesToProcess = fieldIndexQueue.subList(0, lastArrayIndex);

    // Create a map of each collection group to all the FieldIndexes on it.
    Map<String, ArrayList<FieldIndex>> collectionToFieldIndexes = new HashMap<>();

    // Map of field index to the most recent snapshot version written.
    Map<FieldIndex, SnapshotVersion> fieldIndexToNewestVersion = new HashMap<>();

    for (FieldIndex fieldIndex : fieldIndexesToProcess) {

      // Use the lowest version number as the base.
      lastReadTime =
          lastReadTime != null
              ? fieldIndex.getVersion().compareTo(lastReadTime) < 0
                  ? fieldIndex.getVersion()
                  : lastReadTime
              : fieldIndex.getVersion();
      String collectionGroup = fieldIndex.getCollectionGroup();

      if (collectionToFieldIndexes.containsKey(collectionGroup)) {
        collectionToFieldIndexes.get(collectionGroup).add(fieldIndex);
      } else {
        collectionToFieldIndexes.put(
            collectionGroup, new ArrayList<>(Collections.singletonList(fieldIndex)));
      }
    }

    for (String collectionGroup : collectionToFieldIndexes.keySet()) {
      Query query = new Query(ResourcePath.EMPTY, collectionGroup);

      // TODO(indexing): Make sure the docs matching the query are sorted by read time.
      // TODO(indexing): Use limit queries to allow incremental progress.
      // TODO(indexing): Support mutation batch Ids when sorting and writing indexes.
      ImmutableSortedMap<DocumentKey, Document> matchingDocuments =
          localStore.getDocumentsMatchingQuery(query, lastReadTime);
      for (Map.Entry<DocumentKey, Document> entry : matchingDocuments) {
        Document document = entry.getValue();
        numIndexesWritten +=
            indexManager.addIndexEntry(document, fieldIndexesToProcess, fieldIndexToNewestVersion);
      }
    }

    // Update index configurations with the progress made.
    // TODO(indexing): Use RemoteDocumentCache's readTime version rather than the document version.
    // This will require plumbing out the RDC's readTime into the IndexBackfiller.
    for (FieldIndex fieldIndex : fieldIndexToNewestVersion.keySet()) {
      SnapshotVersion newVersion = fieldIndexToNewestVersion.get(fieldIndex);
      fieldIndex.setVersion(newVersion);
      indexManager.updateFieldIndex(fieldIndex);
    }

    // Move the FieldIndexes to the back of the FieldIndexQueue if they've finished processing.
    if (fieldIndexQueue.size() > maxFieldIndexesToProcess) {
      fieldIndexQueue = fieldIndexQueue.subList(lastArrayIndex, fieldIndexQueue.size());
      fieldIndexQueue.addAll(fieldIndexesToProcess);
    }

    return numIndexesWritten;
  }

  @VisibleForTesting
  void setMaxFieldIndexesToProcess(int newMax) {
    maxFieldIndexesToProcess = newMax;
  }

  @VisibleForTesting
  List<FieldIndex> getFieldIndexQueue() {
    return fieldIndexQueue;
  }

  @VisibleForTesting
  void addIndexEntry(IndexEntry entry) {
    persistence.execute(
        "INSERT OR IGNORE INTO index_entries ("
            + "index_id, "
            + "index_value, "
            + "uid, "
            + "document_name) VALUES(?, ?, ?, ?)",
        entry.getIndexId(),
        entry.getIndexValue(),
        entry.getUid(),
        entry.getDocumentName());
  }

  @VisibleForTesting
  void removeIndexEntry(int indexId, String uid, String documentName) {
    persistence.execute(
        "DELETE FROM index_entries "
            + "WHERE index_id = ? "
            + "AND uid = ?"
            + "AND document_name = ?",
        indexId,
        uid,
        documentName);
    ;
  }

  @Nullable
  @VisibleForTesting
  IndexEntry getIndexEntry(int indexId) {
    return persistence
        .query("SELECT index_value, uid, document_name FROM index_entries WHERE index_id = ?")
        .binding(indexId)
        .firstValue(
            row ->
                row == null
                    ? null
                    : new IndexEntry(indexId, row.getBlob(0), row.getString(1), row.getString(2)));
  }
}
