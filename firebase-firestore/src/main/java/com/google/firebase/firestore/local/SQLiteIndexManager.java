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

import static com.google.firebase.firestore.model.Values.isArray;
import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;
import static com.google.firebase.firestore.util.Util.repeatSequence;
import static java.lang.Math.max;

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
import java.util.Iterator;
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
        "INSERT INTO index_configuration ("
            + "index_id, "
            + "collection_group, "
            + "index_proto, "
            + "active, "
            + "update_time_seconds, "
            + "update_time_nanos) VALUES(?, ?, ?, ?, ?, ?)",
        currentMax + 1,
        index.getCollectionGroup(),
        encodeFieldIndex(index),
        true,
        index.getVersion().getTimestamp().getSeconds(),
        index.getVersion().getTimestamp().getNanoseconds());
  }

  @Override
  public void addIndexEntries(Document document) {
    DocumentKey documentKey = document.getKey();
    String collectionGroup = documentKey.getCollectionGroup();
    db.query(
            "SELECT index_id, index_proto, update_time_seconds, update_time_nanos "
                + "FROM index_configuration WHERE collection_group = ? AND active = 1")
        .binding(collectionGroup)
        .forEach(
            row -> {
              try {
                int indexId = row.getInt(0);
                FieldIndex fieldIndex =
                    serializer.decodeFieldIndex(
                        collectionGroup,
                        row.getInt(0),
                        Index.parseFrom(row.getBlob(1)),
                        row.getInt(2),
                        row.getInt(3));

                List<Value> arrayValues = new ArrayList<>();
                for (FieldIndex.Segment segment : fieldIndex.getArraySegments()) {
                  Value value = document.getField(segment.getFieldPath());
                  if (!isArray(value)) {
                    return;
                  }
                  arrayValues.addAll(value.getArrayValue().getValuesList());
                }

                List<Value> directionalValues = new ArrayList<>();
                for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
                  Value field = document.getField(segment.getFieldPath());
                  if (field == null) {
                    return;
                  }
                  directionalValues.add(field);
                }

                if (Logger.isDebugEnabled()) {
                  Logger.debug(
                      TAG,
                      "Adding index values for document '%s' to index '%s'",
                      documentKey,
                      fieldIndex);
                }

                for (int i = 0; i < max(arrayValues.size(), 1); ++i) {
                  addSingleEntry(
                      documentKey,
                      indexId,
                      encode(i < arrayValues.size() ? arrayValues.get(i) : null),
                      encode(directionalValues));
                }
              } catch (InvalidProtocolBufferException e) {
                throw fail("Invalid index: " + e);
              }
            });
  }

  private void addSingleEntry(
      DocumentKey documentKey, int indexId, @Nullable Object arrayIndex, Object directionalIndex) {
    // TODO(indexing): Handle different values for different users
    db.execute(
        "INSERT INTO index_entries (index_id, array_value, directional_value, document_name) "
            + "VALUES(?, ?, ?, ?)",
        indexId,
        arrayIndex,
        directionalIndex,
        documentKey.toString());
  }

  @Override
  @Nullable
  public Set<DocumentKey> getDocumentsMatchingTarget(Target target) {
    @Nullable FieldIndex fieldIndex = getMatchingIndex(target);
    if (fieldIndex == null) return null;

    List<Value> arrayValues = target.getArrayValues(fieldIndex);
    Bound lowerBound = target.getLowerBound(fieldIndex);
    @Nullable Bound upperBound = target.getUpperBound(fieldIndex);

    if (Logger.isDebugEnabled()) {
      Logger.debug(
          TAG,
          "Using index '%s' to execute '%s' (Arrays: %s, Lower bound: %s, Upper bound: %s)",
          fieldIndex,
          target,
          arrayValues,
          lowerBound,
          upperBound);
    }

    Object[] lowerBoundValues = encodeBound(fieldIndex, target, lowerBound);
    String lowerBoundOp = lowerBound.isInclusive() ? ">=" : ">";
    Object[] upperBoundValues = encodeBound(fieldIndex, target, upperBound);
    String upperBoundOp = upperBound != null && upperBound.isInclusive() ? "<=" : "<";

    SQLitePersistence.Query query =
        generateQuery(
            target,
            fieldIndex.getIndexId(),
            arrayValues,
            lowerBoundValues,
            lowerBoundOp,
            upperBoundValues,
            upperBoundOp);

    Set<DocumentKey> result = new HashSet<>();
    query.forEach(
        row -> result.add(DocumentKey.fromPath(ResourcePath.fromString(row.getString(0)))));

    Logger.debug(TAG, "Index scan returned %s documents", result.size());
    return result;
  }

  /** Returns a SQL query on 'index_entries' that unions all bounds. */
  private SQLitePersistence.Query generateQuery(
      Target target,
      int indexId,
      List<Value> arrayValues,
      Object[] lowerBounds,
      String lowerBoundOp,
      @Nullable Object[] upperBounds,
      String upperBoundOp) {
    // The number of total statements we union together. This is similar to a distributed normal
    // form, but adapted for array values. We create a single statement per value in an
    // ARRAY_CONTAINS or ARRAY_CONTAINS_ANY filter combined with the values from the query bounds.
    int statementCount = max(arrayValues.size(), 1) * lowerBounds.length;
    int bindsPerStatement = 2 + (arrayValues.isEmpty() ? 0 : 1) + (upperBounds != null ? 1 : 0);
    Object[] bindArgs = new Object[statementCount * bindsPerStatement];

    // Build the statement. We always include the lower bound, and optionally include an array value
    // and an upper bound.
    StringBuilder statement = new StringBuilder();
    statement.append(
        "SELECT document_name, directional_value FROM index_entries WHERE index_id = ? ");
    if (!arrayValues.isEmpty()) {
      statement.append("AND array_value = ? ");
    }
    statement.append("AND directional_value ").append(lowerBoundOp).append(" ? ");
    if (upperBounds != null) {
      statement.append("AND directional_value ").append(upperBoundOp).append(" ? ");
    }

    // Create the UNION statement by repeating the above generated statement. We can then add
    // ordering and a limit clause.
    String sql = repeatSequence(statement, statementCount, " UNION ");
    if (target.getLimit() != -1) {
      String direction = target.getFirstOrderBy().getDirection().canonicalString();
      sql += "ORDER BY directional_value " + direction + ", document_name " + direction + " ";
      sql += "LIMIT " + target.getLimit() + " ";
    }

    // Fill in the bind ("question marks") variables.
    Iterator<Value> arrayValueIterator = arrayValues.iterator();
    for (int offset = 0; offset < bindArgs.length; ) {
      Object arrayValue = encode(arrayValueIterator.hasNext() ? arrayValueIterator.next() : null);
      offset = fillBounds(bindArgs, offset, indexId, arrayValue, lowerBounds, upperBounds);
    }

    return db.query(sql).binding(bindArgs);
  }

  /** Fills bindArgs starting at offset and returns the new offset. */
  private int fillBounds(
      Object[] bindArgs,
      int offset,
      int indexId,
      @Nullable Object arrayValue,
      Object[] lowerBounds,
      @Nullable Object[] upperBounds) {
    hardAssert(
        upperBounds == null || upperBounds.length == lowerBounds.length,
        "Length of upper and lower bound should match");
    // Add bind variables for each combination of arrayValue, lowerBound and upperBound.
    for (int i = 0; i < lowerBounds.length; ++i) {
      bindArgs[offset++] = indexId;
      if (arrayValue != null) {
        bindArgs[offset++] = arrayValue;
      }
      bindArgs[offset++] = lowerBounds[i];
      if (upperBounds != null) {
        bindArgs[offset++] = upperBounds[i];
      }
    }
    return offset;
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
            "SELECT index_id, index_proto, update_time_seconds, update_time_nanos FROM index_configuration WHERE collection_group = ? AND active = 1")
        .binding(collectionGroup)
        .forEach(
            row -> {
              try {
                FieldIndex fieldIndex =
                    serializer.decodeFieldIndex(
                        collectionGroup,
                        row.getInt(0),
                        Index.parseFrom(row.getBlob(1)),
                        row.getInt(2),
                        row.getInt(3));
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

  /** Encodes a list of values to the index format. */
  private Object encode(List<Value> values) {
    IndexByteEncoder encoder = new IndexByteEncoder();
    for (Value value : values) {
      FirestoreIndexValueWriter.INSTANCE.writeIndexValue(value, encoder);
    }
    return encoder.getEncodedBytes();
  }

  /** Encodes a value to the index format. */
  private @Nullable Object encode(@Nullable Value value) {
    return value != null ? encode(Collections.singletonList(value)) : null;
  }

  /**
   * Encodes the given field values according to the specification in {@code target}. For IN
   * queries, a list of possible values is returned.
   */
  private @Nullable Object[] encodeBound(
      FieldIndex fieldIndex, Target target, @Nullable Bound bound) {
    if (bound == null) return null;

    List<IndexByteEncoder> encoders = new ArrayList<>();
    encoders.add(new IndexByteEncoder());

    Iterator<Value> position = bound.getPosition().iterator();
    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      Value value = position.next();
      for (IndexByteEncoder encoder : encoders) {
        if (isInFilter(target, segment.getFieldPath()) && isArray(value)) {
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

  private boolean isInFilter(Target target, FieldPath fieldPath) {
    for (Filter filter : target.getFilters()) {
      if (filter.getField().equals(fieldPath)) {
        Filter.Operator operator = ((FieldFilter) filter).getOperator();
        return operator.equals(Filter.Operator.IN) || operator.equals(Filter.Operator.NOT_IN);
      }
    }
    return false;
  }

  private byte[] encodeFieldIndex(FieldIndex fieldIndex) {
    return serializer.encodeFieldIndex(fieldIndex).toByteArray();
  }

  // TODO(indexing): Add support for fetching last N updated entries.
  public List<FieldIndex> getFieldIndexes() {
    List<FieldIndex> allIndexes = new ArrayList<>();
    db.query(
            "SELECT index_id, collection_group, index_proto, update_time_seconds, update_time_nanos FROM index_configuration "
                + "WHERE active = 1")
        .forEach(
            row -> {
              try {
                allIndexes.add(
                    serializer.decodeFieldIndex(
                        row.getString(1),
                        row.getInt(0),
                        Index.parseFrom(row.getBlob(2)),
                        row.getInt(3),
                        row.getInt(4)));
              } catch (InvalidProtocolBufferException e) {
                throw fail("Failed to decode index: " + e);
              }
            });

    return allIndexes;
  }
}
