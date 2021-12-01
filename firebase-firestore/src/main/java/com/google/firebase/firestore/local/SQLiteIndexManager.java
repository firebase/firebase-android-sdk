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
import static com.google.firebase.firestore.util.Util.diffCollections;
import static com.google.firebase.firestore.util.Util.repeatSequence;
import static java.lang.Math.max;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.LLRBNode;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.index.DirectionalIndexByteEncoder;
import com.google.firebase.firestore.index.FirestoreIndexValueWriter;
import com.google.firebase.firestore.index.IndexByteEncoder;
import com.google.firebase.firestore.index.IndexEntry;
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
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** A persisted implementation of IndexManager. */
final class SQLiteIndexManager implements IndexManager {
  private static final String TAG = SQLiteIndexManager.class.getSimpleName();

  private final SQLitePersistence db;
  private final LocalSerializer serializer;
  private final String uid;

  /**
   * An in-memory copy of the index entries we've already written since the SDK launched. Used to
   * avoid re-writing the same entry repeatedly.
   *
   * <p>This is *NOT* a complete cache of what's in persistence and so can never be used to satisfy
   * reads.
   */
  private final MemoryIndexManager.MemoryCollectionParentIndex collectionParentsCache =
      new MemoryIndexManager.MemoryCollectionParentIndex();

  private final Map<String, Map<Integer, FieldIndex>> memoizedIndexes = new HashMap<>();
  private final Queue<FieldIndex> nextIndexToUpdate =
      new PriorityQueue<>(
          10,
          (l, r) ->
              Long.compare(
                  l.getIndexState().getSequenceNumber(), r.getIndexState().getSequenceNumber()));

  private boolean started = false;
  private int memoizedMaxIndexId = -1;
  private long memoizedMaxSequenceNumber = -1;

  SQLiteIndexManager(SQLitePersistence persistence, LocalSerializer serializer, User user) {
    this.db = persistence;
    this.serializer = serializer;
    this.uid = user.isAuthenticated() ? user.getUid() : "";
  }

  @Override
  public void start() {
    if (!Persistence.INDEXING_SUPPORT_ENABLED) {
      started = true;
      return;
    }

    Map<Integer, FieldIndex.IndexState> indexStates = new HashMap<>();

    // Fetch all index states if persisted for the user. These states contain per user information
    // on how up to date the index is.
    db.query(
            "SELECT index_id, sequence_number, read_time_seconds, read_time_nanos, document_key "
                + "FROM index_state WHERE uid = ?")
        .binding(uid)
        .forEach(
            row -> {
              int indexId = row.getInt(0);
              long sequenceNumber = row.getLong(1);
              SnapshotVersion readTime =
                  new SnapshotVersion(new Timestamp(row.getLong(2), row.getInt(3)));
              DocumentKey documentKey =
                  DocumentKey.fromPath(EncodedPath.decodeResourcePath(row.getString(4)));
              indexStates.put(
                  indexId, FieldIndex.IndexState.create(sequenceNumber, readTime, documentKey));
            });

    // Fetch all indices and combine with user's index state if available.
    db.query("SELECT index_id, collection_group, index_proto FROM index_configuration")
        .forEach(
            row -> {
              try {
                int indexId = row.getInt(0);
                String collectionGroup = row.getString(1);
                List<FieldIndex.Segment> segments =
                    serializer.decodeFieldIndexSegments(Index.parseFrom(row.getBlob(2)));

                // If we fetched an index state for the user above, combine it with this index.
                // We use the default state if we don't have an index state (e.g. the index was
                // created while a different user as logged in).
                FieldIndex.IndexState indexState =
                    indexStates.containsKey(indexId)
                        ? indexStates.get(indexId)
                        : FieldIndex.INITIAL_STATE;
                FieldIndex fieldIndex =
                    FieldIndex.create(indexId, collectionGroup, segments, indexState);

                // Store the index and update `memoizedMaxIndexId` and `memoizedMaxSequenceNumber`.
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

    int nextIndexId = memoizedMaxIndexId + 1;
    index =
        FieldIndex.create(
            nextIndexId, index.getCollectionGroup(), index.getSegments(), index.getIndexState());

    db.execute(
        "INSERT INTO index_configuration ("
            + "index_id, "
            + "collection_group, "
            + "index_proto) VALUES(?, ?, ?)",
        nextIndexId,
        index.getCollectionGroup(),
        encodeSegments(index));
    memoizeIndex(index);
  }

  @Override
  public void deleteFieldIndex(FieldIndex index) {
    db.execute("DELETE FROM index_configuration WHERE index_id = ?", index.getIndexId());
    db.execute("DELETE FROM index_entries WHERE index_id = ?", index.getIndexId());
    db.execute("DELETE FROM index_state WHERE index_id = ?", index.getIndexState());

    nextIndexToUpdate.remove(index);
    Map<Integer, FieldIndex> collectionIndices = memoizedIndexes.get(index.getCollectionGroup());
    if (collectionIndices != null) {
      collectionIndices.remove(index.getIndexId());
    }
  }

  @Override
  public @Nullable String getNextCollectionGroupToUpdate() {
    hardAssert(started, "IndexManager not started");
    FieldIndex nextIndex = nextIndexToUpdate.peek();
    return nextIndex != null ? nextIndex.getCollectionGroup() : null;
  }

  @Override
  public void updateIndexEntries(ImmutableSortedMap<DocumentKey, Document> documents) {
    hardAssert(started, "IndexManager not started");
    documents.inOrderTraversal(
        new LLRBNode.NodeVisitor<DocumentKey, Document>() {
          @Override
          public void visitEntry(DocumentKey key, Document document) {
            Collection<FieldIndex> fieldIndexes = getFieldIndexes(key.getCollectionGroup());
            for (FieldIndex fieldIndex : fieldIndexes) {
              SortedSet<IndexEntry> existingEntries = getExistingIndexEntries(key, fieldIndex);
              SortedSet<IndexEntry> newEntries = computeIndexEntries(document, fieldIndex);
              if (!existingEntries.equals(newEntries)) {
                updateEntries(document, existingEntries, newEntries);
              }
            }
          }
        });
  }

  /**
   * Updates the index entries for the provided document by deleting entries that are no longer
   * referenced in {@code newEntries} and adding all newly added entries.
   */
  private void updateEntries(
      Document document, SortedSet<IndexEntry> existingEntries, SortedSet<IndexEntry> newEntries) {
    Logger.debug(TAG, "Updating index entries for document '%s'", document.getKey());
    diffCollections(
        existingEntries,
        newEntries,
        entry -> addIndexEntry(document, entry),
        entry -> deleteIndexEntry(document, entry));
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

  /**
   * Stores the index in the memoized indexes table and updates {@link #nextIndexToUpdate}, {@link
   * #memoizedMaxIndexId} and {@link #memoizedMaxSequenceNumber}.
   */
  private void memoizeIndex(FieldIndex fieldIndex) {
    Map<Integer, FieldIndex> existingIndexes = memoizedIndexes.get(fieldIndex.getCollectionGroup());
    if (existingIndexes == null) {
      existingIndexes = new HashMap<>();
      memoizedIndexes.put(fieldIndex.getCollectionGroup(), existingIndexes);
    }

    FieldIndex existingIndex = existingIndexes.get(fieldIndex.getIndexId());
    if (existingIndex != null) {
      nextIndexToUpdate.remove(existingIndex);
    }

    existingIndexes.put(fieldIndex.getIndexId(), fieldIndex);
    nextIndexToUpdate.add(fieldIndex);
    memoizedMaxIndexId = Math.max(memoizedMaxIndexId, fieldIndex.getIndexId());
    memoizedMaxSequenceNumber =
        Math.max(memoizedMaxSequenceNumber, fieldIndex.getIndexState().getSequenceNumber());
  }

  /** Creates the index entries for the given document. */
  private SortedSet<IndexEntry> computeIndexEntries(Document document, FieldIndex fieldIndex) {
    SortedSet<IndexEntry> result = new TreeSet<>();

    @Nullable byte[] directionalValue = encodeDirectionalElements(fieldIndex, document);
    if (directionalValue == null) {
      return result;
    }

    @Nullable FieldIndex.Segment arraySegment = fieldIndex.getArraySegment();
    if (arraySegment != null) {
      Value value = document.getField(arraySegment.getFieldPath());
      if (isArray(value)) {
        for (Value arrayValue : value.getArrayValue().getValuesList()) {
          result.add(
              IndexEntry.create(
                  fieldIndex.getIndexId(),
                  document.getKey(),
                  encodeSingleElement(arrayValue),
                  directionalValue));
        }
      }
    } else {
      result.add(
          IndexEntry.create(
              fieldIndex.getIndexId(), document.getKey(), new byte[] {}, directionalValue));
    }

    return result;
  }

  private void addIndexEntry(Document document, IndexEntry indexEntry) {
    db.execute(
        "INSERT INTO index_entries (index_id, uid, array_value, directional_value, document_name) "
            + "VALUES(?, ?, ?, ?, ?)",
        indexEntry.getIndexId(),
        uid,
        indexEntry.getArrayValue(),
        indexEntry.getDirectionalValue(),
        document.getKey().toString());
  }

  private void deleteIndexEntry(Document document, IndexEntry indexEntry) {
    db.execute(
        "DELETE FROM index_entries WHERE index_id = ? AND uid = ? AND array_value = ? "
            + "AND directional_value = ? AND document_name = ?",
        indexEntry.getIndexId(),
        uid,
        indexEntry.getArrayValue(),
        indexEntry.getDirectionalValue(),
        document.getKey().toString());
  }

  private SortedSet<IndexEntry> getExistingIndexEntries(
      DocumentKey documentKey, FieldIndex fieldIndex) {
    SortedSet<IndexEntry> results = new TreeSet<>();
    db.query(
            "SELECT array_value, directional_value FROM index_entries "
                + "WHERE index_id = ? AND document_name = ? AND uid = ?")
        .binding(fieldIndex.getIndexId(), documentKey.toString(), uid)
        .forEach(
            row ->
                results.add(
                    IndexEntry.create(
                        fieldIndex.getIndexId(), documentKey, row.getBlob(0), row.getBlob(1))));
    return results;
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
    statement.append("WHERE index_id = ? AND uid = ? ");
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
      bindArgs[offset++] = uid;
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

  @Override
  public void updateCollectionGroup(String collectionGroup, FieldIndex.IndexOffset offset) {
    hardAssert(started, "IndexManager not started");

    ++memoizedMaxSequenceNumber;
    for (FieldIndex fieldIndex : getFieldIndexes(collectionGroup)) {
      FieldIndex updatedIndex =
          FieldIndex.create(
              fieldIndex.getIndexId(),
              fieldIndex.getCollectionGroup(),
              fieldIndex.getSegments(),
              FieldIndex.IndexState.create(memoizedMaxSequenceNumber, offset));
      db.execute(
          "REPLACE INTO index_state (index_id, uid,  sequence_number, "
              + "read_time_seconds, read_time_nanos, document_key) VALUES(?, ?, ?, ?, ?, ?)",
          fieldIndex.getIndexId(),
          uid,
          memoizedMaxSequenceNumber,
          offset.getReadTime().getTimestamp().getSeconds(),
          offset.getReadTime().getTimestamp().getNanoseconds(),
          EncodedPath.encode(offset.getDocumentKey().getPath()));
      memoizeIndex(updatedIndex);
    }
  }
}
