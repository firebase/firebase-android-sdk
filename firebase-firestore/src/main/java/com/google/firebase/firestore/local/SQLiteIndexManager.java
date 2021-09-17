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

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.index.FirestoreIndexValueWriter;
import com.google.firebase.firestore.index.IndexByteEncoder;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.TargetIndexMatcher;
import com.google.firebase.firestore.util.Logger;
import com.google.firestore.admin.v1.Index;
import com.google.firestore.v1.Value;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A persisted implementation of IndexManager. */
final class SQLiteIndexManager implements IndexManager {
  private static final String TAG = SQLiteIndexManager.class.getSimpleName();

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
  private final LocalSerializer serializer;

  SQLiteIndexManager(SQLitePersistence persistence, LocalSerializer serializer) {
    this.db = persistence;
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
        index.getCollectionId(),
        encodeFieldIndex(index),
        true);
  }

  @Override
  public void addIndexEntries(Document document) {
    DocumentKey documentKey = document.getKey();
    String collectionGroup = documentKey.getCollectionGroup();
    db.query(
            "SELECT index_id, index_proto FROM index_configuration WHERE collection_group = ? AND active = 1")
        .binding(collectionGroup)
        .forEach(
            row -> {
              try {
                int indexId = row.getInt(0);
                FieldIndex fieldIndex =
                    serializer.decodeFieldIndex(
                        collectionGroup, row.getInt(0), Index.parseFrom(row.getBlob(1)));

                List<Value> values = extractFieldValue(document, fieldIndex);
                if (values == null) return;

                if (Logger.isDebugEnabled()) {
                  Logger.debug(
                      TAG,
                      "Adding index values for document '%s' to index '%s'",
                      documentKey,
                      fieldIndex);
                }

                Object[] encodeValues = encodeDocumentValues(fieldIndex, values);
                for (Object encoded : encodeValues) {
                  // TODO(indexing): Handle different values for different users
                  db.execute(
                      "INSERT OR IGNORE INTO index_entries ("
                          + "index_id, "
                          + "index_value, "
                          + "document_name) VALUES(?, ?, ?)",
                      indexId,
                      encoded,
                      documentKey.toString());
                }
              } catch (InvalidProtocolBufferException e) {
                throw fail("Invalid index: " + e);
              }
            });
  }

  /**
   * Returns the list of values for all fields indexed by the provided field index. Returns {@code
   * null} if one or more of the fields are not set.
   */
  @Nullable
  private List<Value> extractFieldValue(Document document, FieldIndex fieldIndex) {
    List<Value> values = new ArrayList<>();
    for (FieldIndex.Segment segment : fieldIndex) {
      Value field = document.getField(segment.getFieldPath());
      if (field == null) {
        return null;
      }
      values.add(field);
    }
    return values;
  }

  @Override
  @Nullable
  public Set<DocumentKey> getDocumentsMatchingTarget(Target target) {
    @Nullable FieldIndex fieldIndex = getMatchingIndex(target);
    if (fieldIndex == null) return null;

    Bound lowerBound = target.getLowerBound(fieldIndex);
    @Nullable Bound upperBound = target.getUpperBound(fieldIndex);

    if (Logger.isDebugEnabled()) {
      Logger.debug(
          TAG,
          "Using index '%s' to execute '%s' (Lower bound: %s, Upper bound: %s)",
          fieldIndex,
          target,
          lowerBound,
          upperBound);
    }

    Set<DocumentKey> result = new HashSet<>();
    SQLitePersistence.Query query;

    Object[] lowerBoundValues = encodeTargetValues(fieldIndex, target, lowerBound.getPosition());
    String lowerBoundOp = lowerBound.isBefore() ? ">=" : ">"; // `startAt()` versus `startAfter()`
    if (upperBound != null) {
      Object[] upperBoundValues = encodeTargetValues(fieldIndex, target, upperBound.getPosition());
      String upperBoundOp = upperBound.isBefore() ? "<" : "<="; // `endBefore()` versus `endAt()`
      query =
          generateQuery(
              fieldIndex.getIndexId(),
              lowerBoundValues,
              lowerBoundOp,
              upperBoundValues,
              upperBoundOp);
    } else {
      query = generateQuery(fieldIndex.getIndexId(), lowerBoundValues, lowerBoundOp);
    }

    query.forEach(
        row -> result.add(DocumentKey.fromPath(ResourcePath.fromString(row.getString(0)))));

    // TODO(indexing): Add limit handling

    Logger.debug(TAG, "Index scan returned %s documents", result.size());
    return result;
  }

  /** Returns a SQL query on 'index_entries' that unions all bounds. */
  private SQLitePersistence.Query generateQuery(int indexId, Object[] bounds, String op) {
    String[] statements = new String[bounds.length];
    Object[] bingArgs = new Object[bounds.length * 2];
    for (int i = 0; i < bounds.length; ++i) {
      statements[i] =
          String.format(
              "SELECT document_name FROM index_entries WHERE index_id = ? AND index_value %s ?",
              op);
      bingArgs[i * 2] = indexId;
      bingArgs[i * 2 + 1] = bounds[i];
    }
    String sql = TextUtils.join(" UNION ", statements);
    return db.query(sql).binding(bingArgs);
  }

  /** Returns a SQL query on 'index_entries' that unions all bounds. */
  private SQLitePersistence.Query generateQuery(
      int indexId,
      Object[] lowerBounds,
      String lowerBoundOp,
      Object[] upperBounds,
      String upperBoundOp) {
    String[] statements = new String[lowerBounds.length * upperBounds.length];
    Object[] bingArgs = new Object[lowerBounds.length * upperBounds.length * 3];
    int i = 0;
    for (Object value1 : lowerBounds) {
      for (Object value2 : upperBounds) {
        statements[i] =
            String.format(
                "SELECT document_name FROM index_entries WHERE index_id = ? AND index_value %s ? AND index_value %s ?",
                lowerBoundOp, upperBoundOp);
        bingArgs[i * 3] = indexId;
        bingArgs[i * 3 + 1] = value1;
        bingArgs[i * 3 + 2] = value2;
        ++i;
      }
    }
    String sql = TextUtils.join(" UNION ", statements);
    return db.query(sql).binding(bingArgs);
  }

  /**
   * Returns an index that can be used to serve the provided target. Returns {@code null} if no
   * index is configured.
   */
  private @Nullable FieldIndex getMatchingIndex(Target target) {
    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(target);
    String collectionGroup =
        target.getCollectionGroup() != null
            ? target.getCollectionGroup()
            : target.getPath().getLastSegment();

    List<FieldIndex> activeIndices = new ArrayList<>();

    db.query(
            "SELECT index_id, index_proto FROM index_configuration WHERE collection_group = ? AND active = 1")
        .binding(collectionGroup)
        .forEach(
            row -> {
              try {
                FieldIndex fieldIndex =
                    serializer.decodeFieldIndex(
                        collectionGroup, row.getInt(0), Index.parseFrom(row.getBlob(1)));
                boolean matches = targetIndexMatcher.servedByIndex(fieldIndex);
                if (matches) {
                  activeIndices.add(fieldIndex);
                }
              } catch (InvalidProtocolBufferException e) {
                throw fail("Failed to decode index: " + e);
              }
            });

    if (activeIndices.isEmpty()) {
      return null;
    }

    // Return the index with the most number of segments
    return Collections.max(
        activeIndices, (l, r) -> Integer.compare(l.segmentCount(), r.segmentCount()));
  }

  /**
   * Encodes the given field values according to the specification in {@code fieldIndex}. For
   * CONTAINS indices, a list of possible values is returned.
   */
  private Object[] encodeDocumentValues(FieldIndex fieldIndex, List<Value> values) {
    List<IndexByteEncoder> encoders = new ArrayList<>();
    encoders.add(new IndexByteEncoder());
    for (int i = 0; i < fieldIndex.segmentCount(); ++i) {
      FieldIndex.Segment segment = fieldIndex.getSegment(i);
      Value value = values.get(i);
      for (IndexByteEncoder encoder : encoders) {
        if (segment.getKind() == FieldIndex.Segment.Kind.CONTAINS) {
          encoders = expandIndexValues(encoders, value);
        } else {
          hardAssert(
              segment.getKind() == FieldIndex.Segment.Kind.ORDERED,
              "Only ORDERED and CONTAINS are supported");
          FirestoreIndexValueWriter.INSTANCE.writeIndexValue(value, encoder);
        }
      }
    }
    return getEncodedBytes(encoders);
  }

  /**
   * Encodes the given field values according to the specification in {@code target}. For IN and
   * ArrayContainsAny queries, a list of possible values is returned.
   */
  private Object[] encodeTargetValues(FieldIndex fieldIndex, Target target, List<Value> values) {
    List<IndexByteEncoder> encoders = new ArrayList<>();
    encoders.add(new IndexByteEncoder());
    for (int i = 0; i < fieldIndex.segmentCount(); ++i) {
      FieldIndex.Segment segment = fieldIndex.getSegment(i);
      Value value = values.get(i);
      for (IndexByteEncoder encoder : encoders) {
        if (isMultiValueFilter(target, segment.getFieldPath())) {
          encoders = expandIndexValues(encoders, value);
        } else {
          FirestoreIndexValueWriter.INSTANCE.writeIndexValue(value, encoder);
        }
      }
    }
    return getEncodedBytes(encoders);
  }

  /** Returns the byte representation for all encoders. */
  private Object[] getEncodedBytes(List<IndexByteEncoder> encoders) {
    Object[] result = new Object[encoders.size()];
    for (int i = 0; i < encoders.size(); ++i) {
      result[i] = encoders.get(i).getEncodedBytes();
    }
    return result;
  }

  /**
   * Creates a separate encoder for each element of an array.
   *
   * <p>The method appends each value to all existing encoders (e.g. filter("a", "==",
   * "a1").filter("b", "in", ["b1", "b2"]) becomes ["a1,b1", "a1,b2"]). A list of new encoders is
   * returned.
   */
  private List<IndexByteEncoder> expandIndexValues(List<IndexByteEncoder> encoders, Value value) {
    List<IndexByteEncoder> prefixes = new ArrayList<>(encoders);
    List<IndexByteEncoder> results = new ArrayList<>();
    for (Value arrayElement : value.getArrayValue().getValuesList()) {
      for (IndexByteEncoder prefix : prefixes) {
        IndexByteEncoder clonedEncoder = new IndexByteEncoder();
        clonedEncoder.seed(prefix.getEncodedBytes());
        FirestoreIndexValueWriter.INSTANCE.writeIndexValue(arrayElement, clonedEncoder);
        results.add(clonedEncoder);
      }
    }
    return results;
  }

  private boolean isMultiValueFilter(Target target, FieldPath fieldPath) {
    for (Filter filter : target.getFilters()) {
      if (filter.getField().equals(fieldPath))
        switch (((FieldFilter) filter).getOperator()) {
          case ARRAY_CONTAINS_ANY:
          case NOT_IN:
          case IN:
            return true;
          default:
            return false;
        }
    }
    return false;
  }

  private byte[] encodeFieldIndex(FieldIndex fieldIndex) {
    return serializer.encodeFieldIndex(fieldIndex).toByteArray();
  }
}
