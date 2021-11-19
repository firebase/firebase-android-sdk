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

import androidx.annotation.Nullable;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firestore.v1.Write;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.HashMap;
import java.util.Map;

public class SQLiteDocumentOverlayCache implements DocumentOverlayCache {
  private final SQLitePersistence db;
  private final LocalSerializer serializer;
  private final String uid;

  public SQLiteDocumentOverlayCache(SQLitePersistence db, LocalSerializer serializer, User user) {
    this.db = db;
    this.serializer = serializer;
    this.uid = user.isAuthenticated() ? user.getUid() : "";
  }

  @Nullable
  @Override
  public Mutation getOverlay(DocumentKey key) {
    String path = EncodedPath.encode(key.getPath());
    return db.query("SELECT overlay_mutation FROM document_overlays WHERE uid = ? AND path = ?")
        .binding(uid, path)
        .firstValue(
            row -> {
              if (row == null) return null;

              try {
                Write mutation = Write.parseFrom(row.getBlob(0));
                return serializer.decodeMutation(mutation);
              } catch (InvalidProtocolBufferException e) {
                throw fail("Overlay failed to parse: %s", e);
              }
            });
  }

  private void saveOverlay(int largestBatchId, DocumentKey key, @Nullable Mutation mutation) {
    db.execute(
        "INSERT OR REPLACE INTO document_overlays "
            + "(uid, path, largest_batch_id, overlay_mutation) VALUES (?, ?, ?, ?)",
        uid,
        EncodedPath.encode(key.getPath()),
        largestBatchId,
        serializer.encodeMutation(mutation).toByteArray());
  }

  @Override
  public void saveOverlays(int largestBatchId, Map<DocumentKey, Mutation> overlays) {
    for (Map.Entry<DocumentKey, Mutation> entry : overlays.entrySet()) {
      if (entry.getValue() != null) {
        saveOverlay(largestBatchId, entry.getKey(), entry.getValue());
      }
    }
  }

  @Override
  public void removeOverlaysForBatchId(int batchId) {
    db.execute(
        "DELETE FROM document_overlays WHERE uid = ? AND largest_batch_id = ?", uid, batchId);
  }

  @Override
  public Map<DocumentKey, Mutation> getOverlays(ResourcePath collection, int sinceBatchId) {
    int immediateChildrenPathLength = collection.length() + 1;

    String prefixPath = EncodedPath.encode(collection);
    String prefixSuccessorPath = EncodedPath.prefixSuccessor(prefixPath);

    Map<DocumentKey, Mutation> result = new HashMap<>();

    db.query(
            "SELECT path, overlay_mutation FROM document_overlays "
                + "WHERE uid = ? AND path >= ? AND path < ? AND largest_batch_id > ?")
        .binding(uid, prefixPath, prefixSuccessorPath, sinceBatchId)
        .forEach(
            row -> {
              try {
                ResourcePath path = EncodedPath.decodeResourcePath(row.getString(0));
                // The query is actually returning any path that starts with the query path prefix
                // which may include documents in subcollections. For example, a query on 'rooms'
                // will return rooms/abc/messages/xyx but we shouldn't match it. Fix this by
                // discarding rows with document keys more than one segment longer than the query
                // path.
                // TODO(Overlay): Introduce a segment count of the path or a terminator to avoid
                //  over selecting.
                if (path.length() != immediateChildrenPathLength) {
                  return;
                }

                Write write = Write.parseFrom(row.getBlob(1));
                Mutation mutation = serializer.decodeMutation(write);

                result.put(DocumentKey.fromPath(path), mutation);
              } catch (InvalidProtocolBufferException e) {
                throw fail("Overlay failed to parse: %s", e);
              }
            });

    return result;
  }
}
