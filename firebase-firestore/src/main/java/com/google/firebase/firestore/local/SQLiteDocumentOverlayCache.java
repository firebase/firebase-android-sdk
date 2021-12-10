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
import com.google.firebase.firestore.model.mutation.Overlay;
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
  public Overlay getOverlay(DocumentKey key) {
    String collectionPath = EncodedPath.encode(key.getPath().popLast());
    String documentId = key.getPath().getLastSegment();
    return db.query(
            "SELECT overlay_mutation, largest_batch_id FROM document_overlays WHERE uid = ? AND collection_path = ? AND document_id = ?")
        .binding(uid, collectionPath, documentId)
        .firstValue(
            row -> {
              if (row == null) return null;

              try {
                Write mutation = Write.parseFrom(row.getBlob(0));
                int largestBatchId = row.getInt(1);
                return Overlay.create(largestBatchId, serializer.decodeMutation(mutation));
              } catch (InvalidProtocolBufferException e) {
                throw fail("Overlay failed to parse: %s", e);
              }
            });
  }

  private void saveOverlay(int largestBatchId, DocumentKey key, @Nullable Mutation mutation) {
    String collectionPath = EncodedPath.encode(key.getPath().popLast());
    String documentId = key.getPath().getLastSegment();
    db.execute(
        "INSERT OR REPLACE INTO document_overlays "
            + "(uid, collection_path, document_id, largest_batch_id, overlay_mutation) VALUES (?, ?, ?, ?, ?)",
        uid,
        collectionPath,
        documentId,
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
  public Map<DocumentKey, Overlay> getOverlays(ResourcePath collection, int sinceBatchId) {
    String collectionPath = EncodedPath.encode(collection);

    Map<DocumentKey, Overlay> result = new HashMap<>();

    db.query(
            "SELECT document_id, overlay_mutation, largest_batch_id FROM document_overlays "
                + "WHERE uid = ? AND collection_path = ? AND largest_batch_id > ?")
        .binding(uid, collectionPath, sinceBatchId)
        .forEach(
            row -> {
              try {
                String documentId = row.getString(0);
                Write write = Write.parseFrom(row.getBlob(1));
                int largestBatchId = row.getInt(2);
                Mutation mutation = serializer.decodeMutation(write);

                result.put(
                    DocumentKey.fromPath(collection.append(documentId)),
                    Overlay.create(largestBatchId, mutation));
              } catch (InvalidProtocolBufferException e) {
                throw fail("Overlay failed to parse: %s", e);
              }
            });

    return result;
  }
}
