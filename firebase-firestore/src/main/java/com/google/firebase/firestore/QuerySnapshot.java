// Copyright 2018 Google LLC
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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.ViewSnapshot;
import com.google.firebase.firestore.model.Document;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * A {@code QuerySnapshot} contains the results of a query. It can contain zero or more {@link
 * DocumentSnapshot} objects.
 *
 * <p><b>Subclassing Note</b>: Cloud Firestore classes are not meant to be subclassed except for use
 * in test mocks. Subclassing is not supported in production code and new SDK releases may break
 * code that does so.
 */
public class QuerySnapshot implements Iterable<QueryDocumentSnapshot> {

  private final Query originalQuery;

  private final ViewSnapshot snapshot;

  private final FirebaseFirestore firestore;

  private List<DocumentChange> cachedChanges;

  private MetadataChanges cachedChangesMetadataState;

  private final SnapshotMetadata metadata;

  QuerySnapshot(Query originalQuery, ViewSnapshot snapshot, FirebaseFirestore firestore) {
    this.originalQuery = checkNotNull(originalQuery);
    this.snapshot = checkNotNull(snapshot);
    this.firestore = checkNotNull(firestore);
    this.metadata = new SnapshotMetadata(snapshot.hasPendingWrites(), snapshot.isFromCache());
  }

  private class QuerySnapshotIterator implements Iterator<QueryDocumentSnapshot> {
    private final Iterator<com.google.firebase.firestore.model.Document> it;

    QuerySnapshotIterator(Iterator<com.google.firebase.firestore.model.Document> it) {
      this.it = it;
    }

    @Override
    public boolean hasNext() {
      return it.hasNext();
    }

    @Override
    public QueryDocumentSnapshot next() {
      return convertDocument(it.next());
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("QuerySnapshot does not support remove().");
    }
  }

  @NonNull
  public Query getQuery() {
    return originalQuery;
  }

  /** @return The metadata for this query snapshot. */
  @NonNull
  public SnapshotMetadata getMetadata() {
    return metadata;
  }

  /**
   * Returns the list of documents that changed since the last snapshot. If it's the first snapshot
   * all documents will be in the list as added changes.
   *
   * <p>Documents with changes only to their metadata will not be included.
   *
   * @return The list of document changes since the last snapshot.
   */
  @NonNull
  public List<DocumentChange> getDocumentChanges() {
    return getDocumentChanges(MetadataChanges.EXCLUDE);
  }

  /**
   * Returns the list of documents that changed since the last snapshot. If it's the first snapshot
   * all documents will be in the list as added changes.
   *
   * @param metadataChanges Indicates whether metadata-only changes (i.e. only {@code
   *     DocumentSnapshot.getMetadata()} changed) should be included.
   * @return The list of document changes since the last snapshot.
   */
  @NonNull
  public List<DocumentChange> getDocumentChanges(@NonNull MetadataChanges metadataChanges) {
    if (MetadataChanges.INCLUDE.equals(metadataChanges) && snapshot.excludesMetadataChanges()) {
      throw new IllegalArgumentException(
          "To include metadata changes with your document changes, you must also pass MetadataChanges.INCLUDE to addSnapshotListener().");
    }

    if (cachedChanges == null || cachedChangesMetadataState != metadataChanges) {
      cachedChanges =
          Collections.unmodifiableList(
              DocumentChange.changesFromSnapshot(firestore, metadataChanges, snapshot));
      cachedChangesMetadataState = metadataChanges;
    }
    return cachedChanges;
  }

  /**
   * Returns the documents in this {@code QuerySnapshot} as a List in order of the query.
   *
   * @return The list of documents.
   */
  @NonNull
  public List<DocumentSnapshot> getDocuments() {
    List<DocumentSnapshot> res = new ArrayList<>(snapshot.getDocuments().size());
    for (com.google.firebase.firestore.model.Document doc : snapshot.getDocuments()) {
      res.add(convertDocument(doc));
    }
    return res;
  }

  /** Returns true if there are no documents in the {@code QuerySnapshot}. */
  public boolean isEmpty() {
    return snapshot.getDocuments().isEmpty();
  }

  /** Returns the number of documents in the {@code QuerySnapshot}. */
  public int size() {
    return snapshot.getDocuments().size();
  }

  @Override
  @NonNull
  public Iterator<QueryDocumentSnapshot> iterator() {
    return new QuerySnapshotIterator(snapshot.getDocuments().iterator());
  }

  /**
   * Returns the contents of the documents in the {@code QuerySnapshot}, converted to the provided
   * class, as a list.
   *
   * @param clazz The POJO type used to convert the documents in the list.
   */
  @NonNull
  public <T> List<T> toObjects(@NonNull Class<T> clazz) {
    return toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.DEFAULT);
  }

  /**
   * Returns the contents of the documents in the {@code QuerySnapshot}, converted to the provided
   * class, as a list.
   *
   * @param clazz The POJO type used to convert the documents in the list.
   * @param serverTimestampBehavior Configures the behavior for server timestamps that have not yet
   *     been set to their final value.
   */
  @NonNull
  public <T> List<T> toObjects(
      @NonNull Class<T> clazz,
      @NonNull DocumentSnapshot.ServerTimestampBehavior serverTimestampBehavior) {
    checkNotNull(clazz, "Provided POJO type must not be null.");
    List<T> res = new ArrayList<>();
    for (DocumentSnapshot d : this) {
      res.add(d.toObject(clazz, serverTimestampBehavior));
    }
    return res;
  }

  private QueryDocumentSnapshot convertDocument(Document document) {
    return QueryDocumentSnapshot.fromDocument(
        firestore,
        document,
        snapshot.isFromCache(),
        snapshot.getMutatedKeys().contains(document.getKey()));
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof QuerySnapshot)) {
      return false;
    }
    QuerySnapshot other = (QuerySnapshot) obj;
    return firestore.equals(other.firestore)
        && originalQuery.equals(other.originalQuery)
        && snapshot.equals(other.snapshot)
        && metadata.equals(other.metadata);
  }

  @Override
  public int hashCode() {
    int hash = firestore.hashCode();
    hash = hash * 31 + originalQuery.hashCode();
    hash = hash * 31 + snapshot.hashCode();
    hash = hash * 31 + metadata.hashCode();
    return hash;
  }
}
