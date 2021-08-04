package com.google.firebase.firestore.local;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import java.util.Map;

public interface LocalDocumentCache {
  void add(MutableDocument doc);

  void remove(DocumentKey key);

  void setMutationFlags(MutableDocument doc);

  Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> keys);

  MutableDocument get(DocumentKey key);

  ImmutableSortedMap<DocumentKey, MutableDocument> getAllDocumentsMatchingQuery(Query query);
}
