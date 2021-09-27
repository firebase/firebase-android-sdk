package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.fail;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.protobuf.InvalidProtocolBufferException;

public class SQLiteDocumentOverlays implements DocumentOverlays {
  private final SQLitePersistence db;
  private final LocalSerializer serializer;

  public SQLiteDocumentOverlays(SQLitePersistence db, LocalSerializer serializer) {
    this.db = db;
    this.serializer = serializer;
  }

  @Nullable
  @Override
  public Mutation getOverlayMutation(String uid, DocumentKey key) {
    String path = EncodedPath.encode(key.getPath());
    return db.query("SELECT overlay_mutation FROM document_overlays WHERE uid = ? AND path = ?")
        .binding(uid, path)
        .firstValue(
            row -> {
              if (row != null) {
                try {
                  return serializer.decodeMutation(row.getBlob(0));
                } catch (InvalidProtocolBufferException e) {
                  throw fail("Overlay mutations failed to parse: %s", e);
                }
              }

              return null;
            });
  }

  @Override
  public void saveOverlayMutation(String uid, DocumentKey key, Mutation mutation) {
    db.execute(
        "INSERT OR REPLACE INTO document_overlays "
            + "(uid, path, overlay_mutation) VALUES (?, ?, ?)",
        uid,
        EncodedPath.encode(key.getPath()),
        serializer.encodeMutation(mutation));
  }

  @Override
  public void removeOverlayMutation(String uid, DocumentKey key) {
    db.execute(
        "DELETE FROM document_overlays WHERE uid = ? AND path = ?",
        uid,
        EncodedPath.encode(key.getPath()));
  }
}
