// Copyright 2019 Google LLC
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
import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.database.Cursor;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.index.FirestoreIndexValueWriter;
import com.google.firebase.firestore.index.IndexByteEncoder;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.TargetIndexMatcher;
import com.google.firebase.firestore.util.Consumer;
import com.google.firebase.firestore.util.Function;
import com.google.firestore.admin.v1.Index;
import com.google.firestore.v1.Value;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** A persisted implementation of IndexManager. */
final class SQLiteIndexManager implements IndexManager {
  public static final Charset UTF_8 = Charset.forName("UTF-8");
  /**
   * An in-memory copy of the index entries we've already written since the SDK launched. Used to
   * avoid re-writing the same entry repeatedly.
   *
   * <p>This is *NOT* a complete cache of what's in persistence and so can never be used to satisfy
   * reads.
   */
  private final MemoryIndexManager.MemoryCollectionParentIndex collectionParentsCache =
      new MemoryIndexManager.MemoryCollectionParentIndex();

  private final SQLitePersistence db;
  private final User user;
  private final LocalSerializer serializer;

  SQLiteIndexManager(SQLitePersistence persistence, User user, LocalSerializer serializer) {
    this.db = persistence;
    this.user = user;
    this.serializer = serializer;
  }

  @Override
  public void addToCollectionParentIndex(ResourcePath collectionPath) {
    hardAssert(collectionPath.length() % 2 == 1, "Expected a collection path.");

    if (collectionParentsCache.add(collectionPath)) {
      String collectionId = collectionPath.getLastSegment();
      ResourcePath parentPath = collectionPath.popLast();
      db.execute(
          "INSERT OR REPLACE INTO collection_parents "
              + "(collection_id, parent) "
              + "VALUES (?, ?)",
          collectionId,
          EncodedPath.encode(parentPath));
    }
  }

  @Override
  public List<ResourcePath> getCollectionParents(String collectionId) {
    ArrayList<ResourcePath> parentPaths = new ArrayList<>();
    db.query("SELECT parent FROM collection_parents WHERE collection_id = ?")
        .binding(collectionId)
        .forEach(
            row -> {
              parentPaths.add(EncodedPath.decodeResourcePath(row.getString(0)));
            });
    return parentPaths;
  }

  @Override
  public void addDocument(Document document) {
    db.query("SELECT index_id, index_proto FROM index_configuration WHERE collection_group = ?")
        .binding(document.getKey().getPath().popLast().canonicalString())
        .forEach(
            row -> {
              try {
                int indexId = row.getInt(0);
                FieldIndex fieldIndex =
                    serializer.decodeFieldIndex(
                        document.getKey().getPath().popLast().getLastSegment(),
                        Index.parseFrom(row.getBlob(1)));
                List<Value> values = new ArrayList<>();
                for (FieldIndex.Segment segment : fieldIndex) {
                  Value field = document.getField(segment.getFieldPath());
                  if (field == null) return;
                  values.add(field);
                }

                List<byte[]> encodeValues = encodeValues(fieldIndex, values, true);
                for (byte[] encoded : encodeValues) {
                  db.execute(
                      "INSERT OR IGNORE INTO index_entries ("
                          + "index_id, "
                          + "index_value, "
                          + "document_id ) VALUES(?, ?, ?)",
                      indexId,
                      encoded,
                      document.getKey().getPath().getLastSegment());
                }
              } catch (InvalidProtocolBufferException e) {
                throw fail("Invalid index: " + e);
              }
            });
  }

  @Override
  public void enableIndex(ResourcePath collectionPath, FieldIndex index) {
    int currentMax =
        db.query("SELECT MAX(index_id) FROM index_configuration")
            .firstValue(
                new Function<Cursor, Integer>() {
                  @javax.annotation.Nullable
                  @Override
                  public Integer apply(@javax.annotation.Nullable Cursor input) {
                    return input.isNull(0) ? 0 : input.getInt(0);
                  }
                });
    db.execute(
        "INSERT OR IGNORE INTO index_configuration ("
            + "collection_group, "
            + "index_proto, "
            + "index_id) VALUES(?, ?, ?)",
        collectionPath.canonicalString(),
        encodeFieldIndex(index),
        currentMax);
  }

  @Override
  @Nullable
  public Iterable<DocumentKey> getDocumentsMatchingQuery(Query query) {
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(query.toTarget());
    ResourcePath parentPath = query.getPath();
    String collectionId =
        query.isCollectionGroupQuery() ? query.getCollectionGroup() : parentPath.getLastSegment();
    FieldIndex[] bestIndex = new FieldIndex[] {new FieldIndex(collectionId)};
    int[] bestIndexId = new int[] {-1};

    db.query(
            "SELECT index_id, index_proto FROM index_configuration WHERE collection_group = ? AND active = 1")
        .binding(collectionId)
        .forEach(
                value -> {
                  try {
                    FieldIndex fieldIndex =
                        serializer.decodeFieldIndex(collectionId, Index.parseFrom(value.getBlob(1)));
                    boolean matches = targetIndexMatcher.servedByIndex(fieldIndex);
                    if (matches && fieldIndex.segmentCount() > bestIndex[0].segmentCount()) {
                      bestIndex[0] = fieldIndex;
                      bestIndexId[0] = value.getInt(0);
                    }
                  } catch (InvalidProtocolBufferException e) {
                    throw fail("Failed to decode index: " + e);
                  }
                });

    // best index found
    List<Value> lowerBound = query.getLowerBound();
    boolean lowerInclusive = query.isLowerInclusive();
    List<Value> upperBound = query.getUpperBound();
    boolean upperInclusive = query.isUpperInclusive();

    if (bestIndexId[0] == -1) return null;

    // Could we do a join here and return the documents?
    ArrayList<DocumentKey> documents = new ArrayList<>();

    if (lowerBound != null && upperBound != null) {
      List<byte[]> lowerEncoded = encodeValues(bestIndex[0], lowerBound, false);
      List<byte[]> upperEncoded = encodeValues(bestIndex[0], upperBound, false);
      for (byte[] b1 : lowerEncoded) {
        for (byte[] b2 : upperEncoded) {
          db.query(
                  "SELECT document_id from field_index WHERE index_id = ? AND index_value "
                      + (lowerInclusive ? ">=" : ">")
                      + " ? AND index_value "
                      + (upperInclusive ? "<=" : "<")
                      + " ?")
              .binding(bestIndexId[0], b1, b2)
              .forEach(
                  row -> documents.add(DocumentKey.fromPath(parentPath.append(row.getString(0)))));
        }
      }
    } else if (lowerBound != null) {
      List<byte[]> lowerEncoded = encodeValues(bestIndex[0], lowerBound, false);
      for (byte[] b : lowerEncoded) {
        db.query(
                "SELECT document_id from field_index WHERE index_id = ? AND index_value "
                    + (lowerInclusive ? ">=" : ">")
                    + "  ?")
            .binding(bestIndexId[0], b)
            .forEach(
                row -> documents.add(DocumentKey.fromPath(parentPath.append(row.getString(0)))));
      }
    } else {
      List<byte[]> upperEncoded = encodeValues(bestIndex[0], upperBound, false);
      for (byte[] b : upperEncoded) {
        db.query(
                "SELECT document_id from field_index WHERE index_id = ? AND index_value "
                    + (upperInclusive ? "<=" : "<")
                    + "  ?")
            .binding(bestIndexId[0], b)
            .forEach(
                row -> documents.add(DocumentKey.fromPath(parentPath.append(row.getString(0)))));
      }
    }
    return documents;
  }

  private List<byte[]> encodeValues(FieldIndex index, List<Value> values, boolean enforceArrays) {
    List<IndexByteEncoder> encoders = new ArrayList<>();
    encoders.add(new IndexByteEncoder());
    encodeValues(index, values, enforceArrays, encoders);
    List<byte[]> result = new ArrayList<>();
    for (IndexByteEncoder encoder : encoders) {
      result.add(encoder.getEncodedBytes());
    }
    return result;
  }

  private void encodeValues(
      FieldIndex index,
      List<Value> values,
      boolean enforceArrays,
      List<IndexByteEncoder> encoders) {
    if (values.isEmpty()) return;
    for (FieldIndex.Segment indexSegment : index) {
      for (IndexByteEncoder indexByteEncoder : new ArrayList<>(encoders)) {
        switch (indexSegment.getKind()) {
          case ORDERED:
            FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
                values.get(0), new IndexByteEncoder());
            break;
          case CONTAINS:
            if (values.get(0).hasArrayValue()) {
              encoders.clear();
              for (Value value : values.get(0).getArrayValue().getValuesList()) {
                IndexByteEncoder clonedEncoder = new IndexByteEncoder();
                clonedEncoder.seed(indexByteEncoder.getEncodedBytes());
                encoders.add(clonedEncoder);
                FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
                    value, new IndexByteEncoder());
              }
            } else if (!enforceArrays) {
              FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
                  values.get(0), new IndexByteEncoder());
            }
            break;
        }
      }
    }
  }

  public void addFieldIndex(FieldIndex index) {
    int currentMax =
        db.query("SELECT MAX(index_id) FROM index_configuration")
            .firstValue(input -> input.isNull(0) ? 0 : input.getInt(0));

    db.execute(
        "INSERT OR IGNORE INTO index_configuration ("
            + "index_id, "
            + "collection_group, "
            + "index_proto, "
            + "active) VALUES(?, ?, ?, ?)",
        currentMax + 1,
        index.getCollectionGroup(),
        encodeFieldIndex(index),
        true);
  }

  private byte[] encodeFieldIndex(FieldIndex fieldIndex) {
    return serializer.encodeFieldIndex(fieldIndex).toByteArray();
  }
}
