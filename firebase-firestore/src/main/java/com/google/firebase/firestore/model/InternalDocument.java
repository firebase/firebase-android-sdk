package com.google.firebase.firestore.model;

/**
 * Interface exposing underlying document information that is not visible in {@code Document}.
 */
public interface InternalDocument extends Document {
  /**
   * Returns the version of this document if it exists or a version at which this document was
   * guaranteed to not exist.
   */
  SnapshotVersion getVersion();

  /**
   * Returns the timestamp at which this document was read from the remote server. Returns
   * `SnapshotVersion.NONE` for documents created by the user.
   */
  SnapshotVersion getReadTime();

  /** Creates a mutable copy of this document. */
  MutableDocument mutableCopy();
}
