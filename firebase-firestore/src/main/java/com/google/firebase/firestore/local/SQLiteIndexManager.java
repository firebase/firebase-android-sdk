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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.database.Cursor;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.index.FirestoreIndexValueWriter;
import com.google.firebase.firestore.index.IndexByteEncoder;
import com.google.firebase.firestore.index.OrderedCodeReader;
import com.google.firebase.firestore.index.OrderedCodeWriter;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.util.Function;
import com.google.firestore.v1.Value;
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
    db.query("SELECT index_id, field_paths FROM index_configuration WHERE parent_path = ?")
        .binding(document.getKey().getPath().popLast().canonicalString())
        .forEach(
            row -> {
              int indexId = row.getInt(0);
              IndexDefinition components = decodeFilterPath(row.getBlob(1));

              List<Value> values = new ArrayList<>();
              for (IndexComponent component : components) {
                Value field = document.getField(component.fieldPath);
                if (field == null) return;
                values.add(field);
              }

              List<byte[]> encodeValues = encodeValues(components, values, true);
              for (byte[] encoded : encodeValues) {
                db.execute(
                    "INSERT OR IGNORE INTO field_index ("
                        + "index_id, "
                        + "index_value, "
                        + "document_id ) VALUES(?, ?, ?)",
                    indexId,
                    encoded,
                    document.getKey().getPath().getLastSegment());
              }
            });
  }

  @Override
  public void enableIndex(ResourcePath collectionPath, IndexDefinition index) {
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
            + "uid, "
            + "parent_path, "
            + "field_paths, " // field path, direction pairs
            + "index_id) VALUES(?, ?, ?, ?)",
        user.getUid(),
        collectionPath.canonicalString(),
        encodeFilterPath(index),
        currentMax);
  }

  private byte[] encodeFilterPath(IndexDefinition index) {
    OrderedCodeWriter orderedCode = new OrderedCodeWriter();
    for (IndexComponent component : index) {
      orderedCode.writeUtf8Ascending(component.fieldPath.canonicalString());
      // Use long
      orderedCode.writeUtf8Ascending(component.getType().name());
    }
    return orderedCode.encodedBytes();
  }

  private IndexDefinition decodeFilterPath(byte[] bytes) {
    IndexDefinition components = new IndexDefinition();
    OrderedCodeReader orderedCodeReader = new OrderedCodeReader(bytes);
    while (orderedCodeReader.hasRemainingBytes()) {
      String fieldPath = orderedCodeReader.readUtf8Ascending();
      String direction = orderedCodeReader.readUtf8Ascending();
      components.add(
          new IndexComponent(
              FieldPath.fromServerFormat(fieldPath), IndexComponent.IndexType.valueOf(direction)));
    }
    return components;
  }

  @Override
  @Nullable
  public Iterable<DocumentKey> getDocumentsMatchingQuery(Query query) {
    ResourcePath parentPath = query.getPath();
    List<IndexManager.IndexDefinition> indexComponents = query.getIndexComponents();
    List<Value> lowerBound = query.getLowerBound();
    boolean lowerInclusive = query.isLowerInclusive();
    List<Value> upperBound = query.getUpperBound();
    boolean upperInclusive = query.isUpperInclusive();
    for (IndexManager.IndexDefinition index : indexComponents) {
      Integer indexId =
          db.query(
                  "SELECT index_id FROM index_configuration WHERE parent_path = ? AND field_paths = ?")
              .binding(parentPath.canonicalString(), encodeFilterPath(index))
              .firstValue(row -> row.getInt(0));

      if (indexId == null) continue;

      // Could we do a join here and return the documents?
      ArrayList<DocumentKey> documents = new ArrayList<>();

      if (lowerBound != null && upperBound != null) {
        List<byte[]> lowerEncoded = encodeValues(index, lowerBound, false);
        List<byte[]> upperEncoded = encodeValues(index, upperBound, false);
        for (byte[] b1 : lowerEncoded) {
          for (byte[] b2 : upperEncoded) {
            db.query(
                    "SELECT document_id from field_index WHERE index_id = ? AND index_value "
                        + (lowerInclusive ? ">=" : ">")
                        + " ? AND index_value "
                        + (upperInclusive ? "<=" : "<")
                        + " ?")
                .binding(indexId, b1, b2)
                .forEach(
                    row ->
                        documents.add(DocumentKey.fromPath(parentPath.append(row.getString(0)))));
          }
        }
      } else if (lowerBound != null) {
        List<byte[]> lowerEncoded = encodeValues(index, lowerBound, false);
        for (byte[] b : lowerEncoded) {
          db.query(
                  "SELECT document_id from field_index WHERE index_id = ? AND index_value "
                      + (lowerInclusive ? ">=" : ">")
                      + "  ?")
              .binding(indexId, b)
              .forEach(
                  row -> documents.add(DocumentKey.fromPath(parentPath.append(row.getString(0)))));
        }
      } else {
        List<byte[]> upperEncoded = encodeValues(index, upperBound, false);
        for (byte[] b : upperEncoded) {
          db.query(
                  "SELECT document_id from field_index WHERE index_id = ? AND index_value "
                      + (upperInclusive ? "<=" : "<")
                      + "  ?")
              .binding(indexId, b)
              .forEach(
                  row -> documents.add(DocumentKey.fromPath(parentPath.append(row.getString(0)))));
        }
      }
      return documents;
    }

    return null;
  }

  private List<byte[]> encodeValues(
      IndexDefinition index, List<Value> values, boolean enforceArrays) {
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
      IndexDefinition index,
      List<Value> values,
      boolean enforceArrays,
      List<IndexByteEncoder> encoders) {
    if (values.isEmpty()) return;
    for (IndexByteEncoder indexByteEncoder : new ArrayList<>(encoders)) {
      switch (index.get(0).type) {
        case ASC:
        case DESC:
          FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
              values.get(0), indexByteEncoder.forDirection(index.get(0).type));
          break;
        case ANY:
          throw new Error("fail");
        case ARRAY_CONTAINS:
          if (values.get(0).hasArrayValue()) {
            encoders.clear();
            for (Value value : values.get(0).getArrayValue().getValuesList()) {
              IndexByteEncoder clonedEncoder = new IndexByteEncoder();
              clonedEncoder.seed(indexByteEncoder.getEncodedBytes());
              encoders.add(clonedEncoder);
              FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
                  value, clonedEncoder.forDirection(IndexComponent.IndexType.ASC));
            }
          } else if (!enforceArrays) {
            FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
                values.get(0), indexByteEncoder.forDirection(IndexComponent.IndexType.ASC));
          }
          break;
      }
    }

    encodeValues(index.popFirst(), values.subList(1, index.size()), enforceArrays, encoders);
  }

  public void addFieldIndex(FieldIndex index) {
    int currentMax =
        db.query("SELECT MAX(index_id) FROM index_configuration")
            .firstValue(input -> input.isNull(0) ? 0 : input.getInt(0));

    db.execute(
        "INSERT OR IGNORE INTO index_configuration ("
            + "index_id, "
            + "collection_id, "
            + "index_proto, "
            + "active) VALUES(?, ?, ?, ?)",
        currentMax + 1,
        index.getCollectionId(),
        encodeFieldIndex(index),
        true);
  }

  private byte[] encodeFieldIndex(FieldIndex fieldIndex) {
    return serializer.encodeFieldIndex(fieldIndex).toByteArray();
  }
}
