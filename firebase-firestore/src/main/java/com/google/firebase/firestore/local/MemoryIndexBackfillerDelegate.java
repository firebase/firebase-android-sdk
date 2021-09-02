package com.google.firebase.firestore.local;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.index.IndexEntry;

public class MemoryIndexBackfillerDelegate implements IndexBackfillerDelegate {
  private IndexBackfiller backfiller;
  private MemoryPersistence persistence;

  MemoryIndexBackfillerDelegate(MemoryPersistence persistence) {
    this.persistence = persistence;
    this.backfiller = new IndexBackfiller(this);
  }

  @Override
  public IndexBackfiller getIndexBackfiller() {
    return backfiller;
  }

  @Override
  public void addIndexEntry() {
    // Not supported.
  }

  @Override
  public void removeIndexEntry() {
    // Not supported.
  }

  @Nullable
  @Override
  public IndexEntry getIndexEntry(int indexId) {
    // Not supported.
    return null;
  }
}
