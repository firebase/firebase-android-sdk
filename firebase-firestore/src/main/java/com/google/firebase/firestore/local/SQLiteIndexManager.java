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
import androidx.annotation.VisibleForTesting;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.index.DirectionalIndexByteEncoder;
import com.google.firebase.firestore.index.FirestoreIndexValueWriter;
import com.google.firebase.firestore.index.IndexByteEncoder;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.TargetIndexMatcher;
import com.google.firebase.firestore.util.Logger;
import com.google.firestore.admin.v1.Index;
import com.google.firestore.v1.Value;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
  private final User user;
  private final Map<String, Map<Integer, FieldIndex>> memoizedIndexes;

  private boolean started = false;
  private int memoizedMaxId = -1;

  SQLiteIndexManager(SQLitePersistence persistence, LocalSerializer serializer, User user) {
    this.db = persistence;
    this.serializer = serializer;
    this.user = user;
    this.memoizedIndexes = new HashMap<>();
  }

  @Override
  public void start() {
    if (!Persistence.INDEXING_SUPPORT_ENABLED) {
      started = true;
      return;
    }

    db.query(
            "SELECT index_id, collection_group, index_proto, update_time_seconds, "
                + "update_time_nanos FROM index_configuration")
        .forEach(
            row -> {
              try {
                int indexId = row.getInt(0);
                String collectionGroup = row.getString(1);
                List<FieldIndex.Segment> segments =
                    serializer.decodeFieldIndexSegments(Index.parseFrom(row.getBlob(2)));
                SnapshotVersion updateTime =
                    new SnapshotVersion(new Timestamp(row.getLong(3), row.getInt(4)));

                FieldIndex fieldIndex =
                    FieldIndex.create(indexId, collectionGroup, segments, updateTime);
                memoizeIndex(fieldIndex);
              } catch (InvalidProtocolBufferException e) {
                throw fail("Failed to decode index: " + e);
              }
            });

    started = true;
  }

  @Override
  public void addToCollectionParentIndex(ResourcePath collectionPath) {
    hardAssert(started, "IndexManager not started");
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
    hardAssert(started, "IndexManager not started");

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
    hardAssert(started, "IndexManager not started");

    int nextIndexId = memoizedMaxId + 1;
    index =
        FieldIndex.create(
            nextIndexId, index.getCollectionGroup(), index.getSegments(), index.getUpdateTime());

    db.execute(
        "INSERT INTO index_configuration ("
            + "index_id, "
            + "collection_group, "
            + "index_proto, "
            + "update_time_seconds, "
            + "update_time_nanos) VALUES(?, ?, ?, ?, ?)",
        nextIndexId,
        index.getCollectionGroup(),
        encodeSegments(index),
        index.getUpdateTime().getTimestamp().getSeconds(),
        index.getUpdateTime().getTimestamp().getNanoseconds());

    // TODO(indexing): Use a 0-counter rather than the timestamp.
    setCollectionGroupUpdateTime(index.getCollectionGroup(), SnapshotVersion.NONE.getTimestamp());

    memoizeIndex(index);
  }

  @Override
  public void deleteFieldIndex(FieldIndex index) {
    db.execute("DELETE FROM index_configuration WHERE index_id = ?", index.getIndexId());
    db.execute("DELETE FROM index_entries WHERE index_id = ?", index.getIndexId());

    Map<Integer, FieldIndex> collectionIndices = memoizedIndexes.get(index.getCollectionGroup());
    if (collectionIndices != null) {
      collectionIndices.remove(index.getIndexId());
    }
  }

  private void updateFieldIndex(FieldIndex index) {
    db.execute(
        "REPLACE INTO index_configuration ("
            + "index_id, "
            + "collection_group, "
            + "index_proto, "
            + "update_time_seconds, "
            + "update_time_nanos) VALUES(?, ?, ?, ?, ?)",
        index.getIndexId(),
        index.getCollectionGroup(),
        encodeSegments(index),
        index.getUpdateTime().getTimestamp().getSeconds(),
        index.getUpdateTime().getTimestamp().getNanoseconds());

    memoizeIndex(index);
  }

  // TODO(indexing): Use a counter rather than the starting timestamp.
  @Override
  public @Nullable String getNextCollectionGroupToUpdate(Timestamp startingTimestamp) {
    hardAssert(started, "IndexManager not started");

    final String[] nextCollectionGroup = {null};
    db.query(
            "SELECT collection_group "
                + "FROM collection_group_update_times "
                + "WHERE update_time_seconds <= ? AND update_time_nanos <= ?"
                + "ORDER BY update_time_seconds, update_time_nanos "
                + "LIMIT 1")
        .binding(startingTimestamp.getSeconds(), startingTimestamp.getNanoseconds())
        .firstValue(
            row -> {
              nextCollectionGroup[0] = row.getString(0);
              return null;
            });

    if (nextCollectionGroup[0] == null) {
      return null;
    }

    // Store that this collection group will updated.
    // TODO(indexing): Store progress with a counter rather than a timestamp. If using a timestamp,
    // use the read time of the last document read in the loop.
    setCollectionGroupUpdateTime(nextCollectionGroup[0], Timestamp.now());

    return nextCollectionGroup[0];
  }

  @Override
  public void updateIndexEntries(Collection<Document> documents) {
    hardAssert(started, "IndexManager not started");

    Map<Integer, FieldIndex> updatedFieldIndexes = new HashMap<>();

    for (Document document : documents) {
      Collection<FieldIndex> fieldIndexes = getFieldIndexes(document.getKey().getCollectionGroup());
      for (FieldIndex fieldIndex : fieldIndexes) {
        boolean modified = writeEntries(document, fieldIndex);
        if (modified) {
          // TODO(indexing): This would be much simpler with a sequence counter since we would
          // always update the index to the next sequence value.
          FieldIndex latestIndex =
              updatedFieldIndexes.get(fieldIndex.getIndexId()) != null
                  ? updatedFieldIndexes.get(fieldIndex.getIndexId())
                  : fieldIndex;
          FieldIndex updatedIndex = getPostUpdateIndex(latestIndex, document.getReadTime());
          updatedFieldIndexes.put(fieldIndex.getIndexId(), updatedIndex);
        }
      }
    }

    // TODO(indexing): Use RemoteDocumentCache's readTime version rather than the document version.
    // This will require plumbing out the RDC's readTime into the IndexBackfiller.
    for (FieldIndex updatedFieldIndex : updatedFieldIndexes.values()) {
      updateFieldIndex(updatedFieldIndex);
    }
  }

  @Override
  public Collection<FieldIndex> getFieldIndexes(String collectionGroup) {
    hardAssert(started, "IndexManager not started");
    Map<Integer, FieldIndex> indexes = memoizedIndexes.get(collectionGroup);
    return indexes == null ? Collections.emptyList() : indexes.values();
  }

  @Override
  public Collection<FieldIndex> getFieldIndexes() {
    List<FieldIndex> allIndices = new ArrayList<>();
    for (Map<Integer, FieldIndex> indices : memoizedIndexes.values()) {
      allIndices.addAll(indices.values());
    }
    return allIndices;
  }

  /** Stores the index in the memoized indexes table. */
  private void memoizeIndex(FieldIndex fieldIndex) {
    Map<Integer, FieldIndex> existingIndexes = memoizedIndexes.get(fieldIndex.getCollectionGroup());
    if (existingIndexes == null) {
      existingIndexes = new HashMap<>();
      memoizedIndexes.put(fieldIndex.getCollectionGroup(), existingIndexes);
    }
    existingIndexes.put(fieldIndex.getIndexId(), fieldIndex);
    memoizedMaxId = Math.max(memoizedMaxId, fieldIndex.getIndexId());
  }

  /**
   * Returns a field index with the later read time.
   *
   * <p>This method should only be called on field indexes that had index entries written.
   */
  private FieldIndex getPostUpdateIndex(FieldIndex baseIndex, SnapshotVersion newReadTime) {
    if (baseIndex.getUpdateTime().compareTo(newReadTime) > 0) {
      return baseIndex;
    } else {
      return FieldIndex.create(
          baseIndex.getIndexId(),
          baseIndex.getCollectionGroup(),
          baseIndex.getSegments(),
          newReadTime);
    }
  }

  /**
   * If applicable, writes index entries for the given document. Returns whether any index entry was
   * written.
   */
  private boolean writeEntries(Document document, FieldIndex fieldIndex) {
    @Nullable byte[] directionalValue = encodeDirectionalElements(fieldIndex, document);
    if (directionalValue == null) {
      return false;
    }

    @Nullable FieldIndex.Segment arraySegment = fieldIndex.getArraySegment();
    if (arraySegment != null) {
      Value value = document.getField(arraySegment.getFieldPath());
      if (!isArray(value)) {
        return false;
      }

      for (Value arrayValue : value.getArrayValue().getValuesList()) {
        addSingleEntry(
            document, fieldIndex.getIndexId(), encodeSingleElement(arrayValue), directionalValue);
      }
      return true;
    } else {
      addSingleEntry(document, fieldIndex.getIndexId(), /* arrayValue= */ null, directionalValue);
      return true;
    }
  }

  @Override
  public void handleDocumentChange(@Nullable Document oldDocument, @Nullable Document newDocument) {
    hardAssert(started, "IndexManager not started");
    hardAssert(oldDocument == null, "Support for updating documents is not yet available");
    hardAssert(newDocument != null, "Support for removing documents is not yet available");

    DocumentKey documentKey = newDocument.getKey();
    Collection<FieldIndex> fieldIndices = getFieldIndexes(documentKey.getCollectionGroup());
    addIndexEntry(newDocument, fieldIndices);
  }

  /**
   * Writes index entries for the field indexes that apply to the provided document.
   *
   * @param document The provided document to index.
   * @param fieldIndexes A list of field indexes to apply.
   */
  private void addIndexEntry(Document document, Collection<FieldIndex> fieldIndexes) {
    List<FieldIndex> updatedIndexes = new ArrayList<>();

    for (FieldIndex fieldIndex : fieldIndexes) {
      boolean modified = writeEntries(document, fieldIndex);
      if (modified) {
        updatedIndexes.add(getPostUpdateIndex(fieldIndex, document.getVersion()));
      }
    }

    for (FieldIndex updatedIndex : updatedIndexes) {
      updateFieldIndex(updatedIndex);
    }
  }

  /** Adds a single index entry into the index entries table. */
  private void addSingleEntry(
      Document document, int indexId, @Nullable Object arrayValue, Object directionalValue) {
    if (Logger.isDebugEnabled()) {
      Logger.debug(
          TAG, "Adding index values for document '%s' to index '%s'", document.getKey(), indexId);
    }

    db.execute(
        "INSERT INTO index_entries (index_id, uid, array_value, directional_value, document_name) "
            + "VALUES(?, ?, ?, ?, ?)",
        indexId,
        document.hasLocalMutations() ? user.getUid() : null,
        arrayValue,
        directionalValue,
        document.getKey().toString());
  }

  @Override
  public Set<DocumentKey> getDocumentsMatchingTarget(FieldIndex fieldIndex, Target target) {
    hardAssert(started, "IndexManager not started");

    @Nullable List<Value> arrayValues = target.getArrayValues(fieldIndex);
    @Nullable List<Value> notInValues = target.getNotInValues(fieldIndex);
    @Nullable Bound lowerBound = target.getLowerBound(fieldIndex);
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

    Object[] lowerBoundEncoded = encodeBound(fieldIndex, target, lowerBound);
    String lowerBoundOp = lowerBound != null && lowerBound.isInclusive() ? ">=" : ">";
    Object[] upperBoundEncoded = encodeBound(fieldIndex, target, upperBound);
    String upperBoundOp = upperBound != null && upperBound.isInclusive() ? "<=" : "<";
    Object[] notInEncoded = encodeValues(fieldIndex, target, notInValues);

    SQLitePersistence.Query query =
        generateQuery(
            target,
            fieldIndex.getIndexId(),
            arrayValues,
            lowerBoundEncoded,
            lowerBoundOp,
            upperBoundEncoded,
            upperBoundOp,
            notInEncoded);

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
      @Nullable List<Value> arrayValues,
      @Nullable Object[] lowerBounds,
      String lowerBoundOp,
      @Nullable Object[] upperBounds,
      String upperBoundOp,
      @Nullable Object[] notIn) {
    // The number of total statements we union together. This is similar to a distributed normal
    // form, but adapted for array values. We create a single statement per value in an
    // ARRAY_CONTAINS or ARRAY_CONTAINS_ANY filter combined with the values from the query bounds.
    int statementCount =
        (arrayValues != null ? arrayValues.size() : 1)
            * max(
                lowerBounds != null ? lowerBounds.length : 1,
                upperBounds != null ? upperBounds.length : 1);

    // Build the statement. We always include the lower bound, and optionally include an array value
    // and an upper bound.
    StringBuilder statement = new StringBuilder();
    statement.append("SELECT document_name, directional_value FROM index_entries ");
    statement.append("WHERE index_id = ?  AND (uid IS NULL or uid = ?) ");
    if (arrayValues != null) {
      statement.append("AND array_value = ? ");
    }
    if (lowerBounds != null) {
      statement.append("AND directional_value ").append(lowerBoundOp).append(" ? ");
    }
    if (upperBounds != null) {
      statement.append("AND directional_value ").append(upperBoundOp).append(" ? ");
    }

    // Create the UNION statement by repeating the above generated statement. We can then add
    // ordering and a limit clause.
    StringBuilder sql = repeatSequence(statement, statementCount, " UNION ");
    sql.append(" ORDER BY directional_value, document_name ");
    if (target.getLimit() != -1) {
      sql.append("LIMIT ").append(target.getLimit()).append(" ");
    }

    if (notIn != null) {
      // Wrap the statement in a NOT-IN call.
      sql = new StringBuilder("SELECT document_name, directional_value FROM (").append(sql);
      sql.append(") WHERE directional_value NOT IN (");
      sql.append(repeatSequence("?", notIn.length, ", "));
      sql.append(")");
    }

    // Fill in the bind ("question marks") variables.
    Object[] bindArgs =
        fillBounds(statementCount, indexId, arrayValues, lowerBounds, upperBounds, notIn);
    return db.query(sql.toString()).binding(bindArgs);
  }

  /** Returns the bind arguments for all {@code statementCount} statements. */
  private Object[] fillBounds(
      int statementCount,
      int indexId,
      @Nullable List<Value> arrayValues,
      @Nullable Object[] lowerBounds,
      @Nullable Object[] upperBounds,
      @Nullable Object[] notInValues) {
    int bindsPerStatement =
        2
            + (arrayValues != null ? 1 : 0)
            + (lowerBounds != null ? 1 : 0)
            + (upperBounds != null ? 1 : 0);
    int statementsPerArrayValue = statementCount / (arrayValues != null ? arrayValues.size() : 1);

    Object[] bindArgs =
        new Object
            [statementCount * bindsPerStatement + (notInValues != null ? notInValues.length : 0)];
    int offset = 0;
    for (int i = 0; i < statementCount; ++i) {
      bindArgs[offset++] = indexId;
      bindArgs[offset++] = user.getUid();
      if (arrayValues != null) {
        bindArgs[offset++] = encodeSingleElement(arrayValues.get(i / statementsPerArrayValue));
      }
      if (lowerBounds != null) {
        bindArgs[offset++] = lowerBounds[i % statementsPerArrayValue];
      }
      if (upperBounds != null) {
        bindArgs[offset++] = upperBounds[i % statementsPerArrayValue];
      }
    }
    if (notInValues != null) {
      for (Object notInValue : notInValues) {
        bindArgs[offset++] = notInValue;
      }
    }
    return bindArgs;
  }

  @Nullable
  @Override
  public FieldIndex getFieldIndex(Target target) {
    hardAssert(started, "IndexManager not started");

    TargetIndexMatcher targetIndexMatcher = new TargetIndexMatcher(target);
    String collectionGroup =
        target.getCollectionGroup() != null
            ? target.getCollectionGroup()
            : target.getPath().getLastSegment();

    Collection<FieldIndex> collectionIndexes = getFieldIndexes(collectionGroup);
    if (collectionIndexes.isEmpty()) {
      return null;
    }

    List<FieldIndex> matchingIndexes = new ArrayList<>();
    for (FieldIndex fieldIndex : collectionIndexes) {
      boolean matches = targetIndexMatcher.servedByIndex(fieldIndex);
      if (matches) {
        matchingIndexes.add(fieldIndex);
      }
    }

    if (matchingIndexes.isEmpty()) {
      return null;
    }

    // Return the index with the most number of segments
    return Collections.max(
        matchingIndexes, (l, r) -> Integer.compare(l.getSegments().size(), r.getSegments().size()));
  }

  /**
   * Returns the byte encoded form of the directional values in the field index. Returns {@code
   * null} if the document does not have all fields specified in the index.
   */
  private @Nullable byte[] encodeDirectionalElements(FieldIndex fieldIndex, Document document) {
    IndexByteEncoder encoder = new IndexByteEncoder();
    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      Value field = document.getField(segment.getFieldPath());
      if (field == null) {
        return null;
      }
      DirectionalIndexByteEncoder directionalEncoder = encoder.forKind(segment.getKind());
      FirestoreIndexValueWriter.INSTANCE.writeIndexValue(field, directionalEncoder);
    }
    return encoder.getEncodedBytes();
  }

  /** Encodes a single value to the ascending index format. */
  private byte[] encodeSingleElement(Value value) {
    IndexByteEncoder encoder = new IndexByteEncoder();
    FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
        value, encoder.forKind(FieldIndex.Segment.Kind.ASCENDING));
    return encoder.getEncodedBytes();
  }

  /**
   * Encodes the given field values according to the specification in {@code target}. For IN
   * queries, a list of possible values is returned.
   */
  private @Nullable Object[] encodeValues(
      FieldIndex fieldIndex, Target target, @Nullable List<Value> bound) {
    if (bound == null) return null;

    List<IndexByteEncoder> encoders = new ArrayList<>();
    encoders.add(new IndexByteEncoder());

    Iterator<Value> position = bound.iterator();
    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      Value value = position.next();
      for (IndexByteEncoder encoder : encoders) {
        if (isInFilter(target, segment.getFieldPath()) && isArray(value)) {
          encoders = expandIndexValues(encoders, segment, value);
        } else {
          DirectionalIndexByteEncoder directionalEncoder = encoder.forKind(segment.getKind());
          FirestoreIndexValueWriter.INSTANCE.writeIndexValue(value, directionalEncoder);
        }
      }
    }
    return getEncodedBytes(encoders);
  }

  /**
   * Encodes the given bounds according to the specification in {@code target}. For IN queries, a
   * list of possible values is returned.
   */
  private @Nullable Object[] encodeBound(
      FieldIndex fieldIndex, Target target, @Nullable Bound bound) {
    if (bound == null) return null;
    return encodeValues(fieldIndex, target, bound.getPosition());
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
  private List<IndexByteEncoder> expandIndexValues(
      List<IndexByteEncoder> encoders, FieldIndex.Segment segment, Value value) {
    List<IndexByteEncoder> prefixes = new ArrayList<>(encoders);
    List<IndexByteEncoder> results = new ArrayList<>();
    for (Value arrayElement : value.getArrayValue().getValuesList()) {
      for (IndexByteEncoder prefix : prefixes) {
        IndexByteEncoder clonedEncoder = new IndexByteEncoder();
        clonedEncoder.seed(prefix.getEncodedBytes());
        FirestoreIndexValueWriter.INSTANCE.writeIndexValue(
            arrayElement, clonedEncoder.forKind(segment.getKind()));
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

  private byte[] encodeSegments(FieldIndex fieldIndex) {
    return serializer.encodeFieldIndexSegments(fieldIndex.getSegments()).toByteArray();
  }

  @VisibleForTesting
  void setCollectionGroupUpdateTime(String collectionGroup, Timestamp updateTime) {
    db.execute(
        "INSERT OR REPLACE INTO collection_group_update_times "
            + "(collection_group, update_time_seconds, update_time_nanos) "
            + "VALUES (?, ?, ?)",
        collectionGroup,
        updateTime.getSeconds(),
        updateTime.getNanoseconds());
  }
}
