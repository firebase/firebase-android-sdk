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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.fail;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

final class SQLiteRemoteDocumentCache implements RemoteDocumentCache {

  private final SQLitePersistence db;
  private final LocalSerializer serializer;

  SQLiteRemoteDocumentCache(SQLitePersistence persistence, LocalSerializer serializer) {
    this.db = persistence;
    this.serializer = serializer;
  }

  @Override
  public void add(MaybeDocument maybeDocument) {
    String path = pathForKey(maybeDocument.getKey());
    MessageLite message = serializer.encodeMaybeDocument(maybeDocument);

    db.execute(
        "INSERT OR REPLACE INTO remote_documents (path, contents) VALUES (?, ?)",
        path,
        message.toByteArray());
  }

  @Override
  public void remove(DocumentKey documentKey) {
    String path = pathForKey(documentKey);

    db.execute("DELETE FROM remote_documents WHERE path = ?", path);
  }

  @Nullable
  @Override
  public MaybeDocument get(DocumentKey documentKey) {
    String path = pathForKey(documentKey);

    return db.query("SELECT contents FROM remote_documents WHERE path = ?")
        .binding(path)
        .firstValue(row -> decodeMaybeDocument(row.getBlob(0)));
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getAllDocumentsMatchingQuery(Query query) {
    // Use the query path as a prefix for testing if a document matches the query.
    ResourcePath prefix = query.getPath();
    int immediateChildrenPathLength = prefix.length() + 1;

    String prefixPath = EncodedPath.encode(prefix);
    String prefixSuccessorPath = EncodedPath.prefixSuccessor(prefixPath);

    Map<DocumentKey, Document> results = new HashMap<>();

    db.query("SELECT path, contents FROM remote_documents WHERE path >= ? AND path < ?")
        .binding(prefixPath, prefixSuccessorPath)
        .forEach(
            row -> {
              // TODO: Actually implement a single-collection query
              //
              // The query is actually returning any path that starts with the query path prefix
              // which may include documents in subcollections. For example, a query on 'rooms'
              // will return rooms/abc/messages/xyx but we shouldn't match it. Fix this by
              // discarding rows with document keys more than one segment longer than the query
              // path.
              ResourcePath path = EncodedPath.decodeResourcePath(row.getString(0));
              if (path.length() != immediateChildrenPathLength) {
                return;
              }

              MaybeDocument maybeDoc = decodeMaybeDocument(row.getBlob(1));
              if (!(maybeDoc instanceof Document)) {
                return;
              }

              Document doc = (Document) maybeDoc;
              if (!query.matches(doc)) {
                return;
              }

              results.put(doc.getKey(), doc);
            });

    return ImmutableSortedMap.Builder.fromMap(results, DocumentKey.comparator());
  }

  private String pathForKey(DocumentKey key) {
    return EncodedPath.encode(key.getPath());
  }

  private MaybeDocument decodeMaybeDocument(byte[] bytes) {
    try {
      return serializer.decodeMaybeDocument(
          com.google.firebase.firestore.proto.MaybeDocument.parseFrom(bytes));
    } catch (InvalidProtocolBufferException e) {
      throw fail("MaybeDocument failed to parse: %s", e);
    }
  }
}
