package com.google.firebase.firestore.local;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.index.IndexEntry;

public class SQLiteIndexBackfillerDelegate implements IndexBackfillerDelegate {
  private final SQLitePersistence persistence;
  private final IndexBackfiller indexBackfiller;

  SQLiteIndexBackfillerDelegate(SQLitePersistence persistence) {
    this.persistence = persistence;
    this.indexBackfiller = new IndexBackfiller(this);
  }

  public IndexBackfiller getIndexBackfiller() {
    return indexBackfiller;
  }

  @Override
  public void addIndexEntry() {
    // TODO: implement adding actual values into table
    persistence.execute(
        "INSERT OR IGNORE INTO index_entries ("
            + "index_id, "
            + "index_value, "
            + "uid, "
            + "document_id, "
            + "active) VALUES(?, ?, ?, ?)",
        1,
        "TEST-BLOB",
        "sample-uid",
        "sample-documentId");
  }

  @Override
  public void removeIndexEntry() {
    // TODO: implement removing actual values into table
    persistence.execute(
        "INSERT OR IGNORE INTO index_entries ("
            + "index_id, "
            + "index_value, "
            + "uid, "
            + "document_id, "
            + "active) VALUES(?, ?, ?, ?)",
        1,
        "TEST-BLOB".getBytes(),
        "sample-uid",
        "sample-documentId");
    ;
  }

  @Override
  @Nullable
  public IndexEntry getIndexEntry(int indexId) {
    return persistence
        .query(
            "SELECT index_value, uid, document_id, active"
                + " FROM index_entries WHERE index_id = ?")
        .binding(indexId)
        .firstValue(
            row ->
                row == null
                    ? null
                    : new IndexEntry(indexId, row.getBlob(0), row.getString(1), row.getString(2)));
  }
}
