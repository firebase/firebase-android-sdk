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

package com.google.firebase.firestore.testutil;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.model.DocumentCollections.emptyDocumentMap;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.common.internal.Preconditions;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.TestAccessHelper;
import com.google.firebase.firestore.UserDataReader;
import com.google.firebase.firestore.UserDataWriter;
import com.google.firebase.firestore.core.Bound;
import com.google.firebase.firestore.core.CompositeFilter;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.FieldFilter.Operator;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.OrderBy;
import com.google.firebase.firestore.core.OrderBy.Direction;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.UserData.ParsedSetData;
import com.google.firebase.firestore.core.UserData.ParsedUpdateData;
import com.google.firebase.firestore.local.LocalViewChanges;
import com.google.firebase.firestore.local.QueryPurpose;
import com.google.firebase.firestore.local.TargetData;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldIndex.IndexState;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.Values;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.model.mutation.VerifyMutation;
import com.google.firebase.firestore.remote.ExistenceFilter;
import com.google.firebase.firestore.remote.RemoteEvent;
import com.google.firebase.firestore.remote.TargetChange;
import com.google.firebase.firestore.remote.WatchChange;
import com.google.firebase.firestore.remote.WatchChange.DocumentChange;
import com.google.firebase.firestore.remote.WatchChangeAggregator;
import com.google.firestore.v1.Value;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** A set of utilities for tests */
public class TestUtil {

  /** A string sentinel that can be used with patchMutation() to mark a field for deletion. */
  public static final String DELETE_SENTINEL = "<DELETE>";

  public static final long ARBITRARY_SEQUENCE_NUMBER = 2;

  private static final DatabaseId TEST_PROJECT = DatabaseId.forProject("project");

  @SuppressWarnings("unchecked")
  public static <T> Map<String, T> map(Object... entries) {
    Map<String, T> res = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      res.put((String) entries[i], (T) entries[i + 1]);
    }
    return res;
  }

  public static Blob blob(int... bytes) {
    return Blob.fromByteString(byteString(bytes));
  }

  public static ByteString byteString(int... bytes) {
    byte[] primitive = new byte[bytes.length];
    for (int i = 0; i < bytes.length; i++) {
      primitive[i] = (byte) bytes[i];
    }
    return ByteString.copyFrom(primitive);
  }

  public static FieldMask fieldMask(String... fields) {
    FieldPath[] mask = new FieldPath[fields.length];
    for (int i = 0; i < fields.length; i++) {
      mask[i] = field(fields[i]);
    }
    return FieldMask.fromSet(new HashSet<>(Arrays.asList(mask)));
  }

  public static final Map<String, Object> EMPTY_MAP = new HashMap<>();

  public static Value wrap(Object value) {
    UserDataReader dataReader = new UserDataReader(TEST_PROJECT);
    // HACK: We use parseQueryValue() since it accepts scalars as well as arrays / objects, and
    // our tests currently use wrap() pretty generically so we don't know the intent.
    return dataReader.parseQueryValue(value);
  }

  public static Value wrapRef(DatabaseId databaseId, DocumentKey key) {
    return Values.refValue(databaseId, key);
  }

  public static ObjectValue wrapObject(Map<String, Object> value) {
    // Cast is safe here because value passed in is a map
    return new ObjectValue(wrap(value));
  }

  public static ObjectValue wrapObject(Object... entries) {
    return wrapObject(map(entries));
  }

  public static Object decodeValue(FirebaseFirestore firestore, Value value) {
    UserDataWriter dataWriter =
        new UserDataWriter(firestore, DocumentSnapshot.ServerTimestampBehavior.NONE);
    return dataWriter.convertValue(value);
  }

  public static DocumentKey key(String key) {
    return DocumentKey.fromPathString(key);
  }

  public static ResourcePath path(String key) {
    return ResourcePath.fromString(key);
  }

  public static Query query(String path) {
    return Query.atPath(path(path));
  }

  public static FieldPath field(String path) {
    return FieldPath.fromServerFormat(path);
  }

  public static DocumentReference ref(String key) {
    return TestAccessHelper.createDocumentReference(key(key));
  }

  public static DatabaseId dbId(String project, String database) {
    return DatabaseId.forDatabase(project, database);
  }

  public static DatabaseId dbId(String project) {
    return DatabaseId.forProject(project);
  }

  public static SnapshotVersion version(long versionMicros) {
    long seconds = versionMicros / 1000000;
    int nanos = (int) (versionMicros % 1000000L) * 1000;
    return new SnapshotVersion(new Timestamp(seconds, nanos));
  }

  public static SnapshotVersion version(int seconds, int nanos) {
    return new SnapshotVersion(new Timestamp(seconds, nanos));
  }

  public static MutableDocument doc(String key, long version, Map<String, Object> data) {
    return doc(key(key), version, wrapObject(data));
  }

  public static MutableDocument doc(DocumentKey key, long version, Map<String, Object> data) {
    return doc(key, version, wrapObject(data));
  }

  public static MutableDocument doc(String key, long version, ObjectValue data) {
    return doc(key(key), version, data);
  }

  public static MutableDocument doc(DocumentKey key, long version, ObjectValue data) {
    return MutableDocument.newFoundDocument(key, version(version), data)
        .setReadTime(version(version));
  }

  public static MutableDocument deletedDoc(String key, long version) {
    return MutableDocument.newNoDocument(key(key), version(version)).setReadTime(version(version));
  }

  public static MutableDocument unknownDoc(String key, long version) {
    return MutableDocument.newUnknownDocument(key(key), version(version));
  }

  public static <T extends Document> ImmutableSortedMap<DocumentKey, T> docMap(T... documents) {
    ImmutableSortedMap<DocumentKey, T> map =
        (ImmutableSortedMap<DocumentKey, T>) emptyDocumentMap();
    for (T maybeDocument : documents) {
      map = map.insert(maybeDocument.getKey(), maybeDocument);
    }
    return map;
  }

  public static DocumentSet docSet(Comparator<Document> comparator, MutableDocument... documents) {
    DocumentSet set = DocumentSet.emptySet(comparator);
    for (MutableDocument document : documents) {
      set = set.add(document);
    }
    return set;
  }

  public static ImmutableSortedSet<DocumentKey> keySet(DocumentKey... keys) {
    ImmutableSortedSet<DocumentKey> keySet = DocumentKey.emptyKeySet();
    for (DocumentKey key : keys) {
      keySet = keySet.insert(key);
    }
    return keySet;
  }

  public static <T> Map<DocumentKey, T> keyMap(Object... entries) {
    Map<DocumentKey, T> res = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      res.put(DocumentKey.fromPathString((String) entries[i]), (T) entries[i + 1]);
    }
    return res;
  }

  public static FieldFilter filter(String key, String operator, Object value) {
    return FieldFilter.create(field(key), operatorFromString(operator), wrap(value));
  }

  public static CompositeFilter andFilters(List<Filter> filters) {
    return new CompositeFilter(filters, CompositeFilter.Operator.AND);
  }

  public static CompositeFilter andFilters(Filter... filters) {
    return new CompositeFilter(Arrays.asList(filters), CompositeFilter.Operator.AND);
  }

  public static CompositeFilter orFilters(Filter... filters) {
    return new CompositeFilter(Arrays.asList(filters), CompositeFilter.Operator.OR);
  }

  public static CompositeFilter orFilters(List<Filter> filters) {
    return new CompositeFilter(filters, CompositeFilter.Operator.OR);
  }

  public static Operator operatorFromString(String s) {
    if (s.equals("<")) {
      return Operator.LESS_THAN;
    } else if (s.equals("<=")) {
      return Operator.LESS_THAN_OR_EQUAL;
    } else if (s.equals("==")) {
      return Operator.EQUAL;
    } else if (s.equals("!=")) {
      return Operator.NOT_EQUAL;
    } else if (s.equals(">")) {
      return Operator.GREATER_THAN;
    } else if (s.equals(">=")) {
      return Operator.GREATER_THAN_OR_EQUAL;
    } else if (s.equals("array-contains")) {
      return Operator.ARRAY_CONTAINS;
    } else if (s.equals("in")) {
      return Operator.IN;
    } else if (s.equals("not-in")) {
      return Operator.NOT_IN;
    } else if (s.equals("array-contains-any")) {
      return Operator.ARRAY_CONTAINS_ANY;
    } else {
      throw new IllegalStateException("Unknown operator: " + s);
    }
  }

  public static OrderBy orderBy(String key) {
    return orderBy(key, "asc");
  }

  public static OrderBy orderBy(String key, String dir) {
    Direction direction;
    if (dir.equals("asc")) {
      direction = Direction.ASCENDING;
    } else if (dir.equals("desc")) {
      direction = Direction.DESCENDING;
    } else {
      throw new IllegalArgumentException("Unknown direction: " + dir);
    }
    return OrderBy.getInstance(direction, field(key));
  }

  public static Bound bound(boolean inclusive, Object... values) {
    return new Bound(
        Arrays.stream(values)
            .map(v -> v instanceof Value ? (Value) v : wrap(v))
            .collect(Collectors.toList()),
        inclusive);
  }

  public static void testEquality(List<List<Integer>> equalityGroups) {
    for (int i = 0; i < equalityGroups.size(); i++) {
      List<?> group = equalityGroups.get(i);
      for (Object value : group) {
        for (List<?> otherGroup : equalityGroups) {
          for (Object otherValue : otherGroup) {
            if (otherGroup == group) {
              assertEquals(value, otherValue);
            } else {
              assertNotEquals(value, otherValue);
            }
          }
        }
      }
    }
  }

  public static TargetData targetData(int targetId, QueryPurpose queryPurpose, String path) {
    return new TargetData(
        query(path).toTarget(), targetId, ARBITRARY_SEQUENCE_NUMBER, queryPurpose);
  }

  public static ImmutableSortedMap<DocumentKey, Document> docUpdates(MutableDocument... docs) {
    ImmutableSortedMap<DocumentKey, Document> res = emptyDocumentMap();
    for (MutableDocument doc : docs) {
      res = res.insert(doc.getKey(), doc);
    }
    return res;
  }

  public static TargetChange targetChange(
      ByteString resumeToken,
      boolean current,
      @Nullable Collection<MutableDocument> addedDocuments,
      @Nullable Collection<MutableDocument> modifiedDocuments,
      @Nullable Collection<? extends MutableDocument> removedDocuments) {
    ImmutableSortedSet<DocumentKey> addedDocumentKeys = DocumentKey.emptyKeySet();
    ImmutableSortedSet<DocumentKey> modifiedDocumentKeys = DocumentKey.emptyKeySet();
    ImmutableSortedSet<DocumentKey> removedDocumentKeys = DocumentKey.emptyKeySet();

    if (addedDocuments != null) {
      for (MutableDocument document : addedDocuments) {
        addedDocumentKeys = addedDocumentKeys.insert(document.getKey());
      }
    }

    if (modifiedDocuments != null) {
      for (MutableDocument document : modifiedDocuments) {
        modifiedDocumentKeys = modifiedDocumentKeys.insert(document.getKey());
      }
    }

    if (removedDocuments != null) {
      for (MutableDocument document : removedDocuments) {
        removedDocumentKeys = removedDocumentKeys.insert(document.getKey());
      }
    }

    return new TargetChange(
        resumeToken, current, addedDocumentKeys, modifiedDocumentKeys, removedDocumentKeys);
  }

  public static TargetChange ackTarget(MutableDocument... docs) {
    return targetChange(ByteString.EMPTY, true, Arrays.asList(docs), null, null);
  }

  public static Map<Integer, TargetData> activeQueries(Iterable<Integer> targets) {
    Query query = query("foo");
    Map<Integer, TargetData> listenMap = new HashMap<>();
    for (Integer targetId : targets) {
      TargetData targetData =
          new TargetData(
              query.toTarget(), targetId, ARBITRARY_SEQUENCE_NUMBER, QueryPurpose.LISTEN);
      listenMap.put(targetId, targetData);
    }
    return listenMap;
  }

  public static Map<Integer, TargetData> activeQueries(Integer... targets) {
    return activeQueries(asList(targets));
  }

  public static Map<Integer, TargetData> activeLimboQueries(
      String docKey, Iterable<Integer> targets) {
    Query query = query(docKey);
    Map<Integer, TargetData> listenMap = new HashMap<>();
    for (Integer targetId : targets) {
      TargetData targetData =
          new TargetData(
              query.toTarget(), targetId, ARBITRARY_SEQUENCE_NUMBER, QueryPurpose.LIMBO_RESOLUTION);
      listenMap.put(targetId, targetData);
    }
    return listenMap;
  }

  public static Map<Integer, TargetData> activeLimboQueries(String docKey, Integer... targets) {
    return activeLimboQueries(docKey, asList(targets));
  }

  public static RemoteEvent noChangeEvent(int targetId, int version) {
    return noChangeEvent(targetId, version, resumeToken(version));
  }

  public static RemoteEvent noChangeEvent(int targetId, int version, ByteString resumeToken) {
    TargetData targetData = TestUtil.targetData(targetId, QueryPurpose.LISTEN, "foo/bar");
    TestTargetMetadataProvider testTargetMetadataProvider = new TestTargetMetadataProvider();
    testTargetMetadataProvider.setSyncedKeys(targetData, DocumentKey.emptyKeySet());

    WatchChangeAggregator aggregator = new WatchChangeAggregator(testTargetMetadataProvider);

    WatchChange.WatchTargetChange watchChange =
        new WatchChange.WatchTargetChange(
            WatchChange.WatchTargetChangeType.NoChange, asList(targetId), resumeToken);
    aggregator.handleTargetChange(watchChange);
    return aggregator.createRemoteEvent(version(version));
  }

  public static RemoteEvent addedRemoteEvent(
      MutableDocument doc, List<Integer> updatedInTargets, List<Integer> removedFromTargets) {
    return addedRemoteEvent(singletonList(doc), updatedInTargets, removedFromTargets);
  }

  public static RemoteEvent existenceFilterEvent(
      int targetId, ImmutableSortedSet<DocumentKey> syncedKeys, int remoteCount, int version) {
    TargetData targetData = TestUtil.targetData(targetId, QueryPurpose.LISTEN, "foo");
    TestTargetMetadataProvider testTargetMetadataProvider = new TestTargetMetadataProvider();
    testTargetMetadataProvider.setSyncedKeys(targetData, syncedKeys);

    ExistenceFilter existenceFilter = new ExistenceFilter(remoteCount);
    WatchChangeAggregator aggregator = new WatchChangeAggregator(testTargetMetadataProvider);

    WatchChange.ExistenceFilterWatchChange existenceFilterWatchChange =
        new WatchChange.ExistenceFilterWatchChange(targetId, existenceFilter);
    aggregator.handleExistenceFilter(existenceFilterWatchChange);
    return aggregator.createRemoteEvent(version(version));
  }

  public static RemoteEvent addedRemoteEvent(
      List<MutableDocument> docs,
      List<Integer> updatedInTargets,
      List<Integer> removedFromTargets) {
    Preconditions.checkArgument(!docs.isEmpty(), "Cannot pass empty docs array");

    WatchChangeAggregator aggregator =
        new WatchChangeAggregator(
            new WatchChangeAggregator.TargetMetadataProvider() {
              @Override
              public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
                return DocumentKey.emptyKeySet();
              }

              @Override
              public TargetData getTargetDataForTarget(int targetId) {
                ResourcePath collectionPath = docs.get(0).getKey().getCollectionPath();
                return targetData(targetId, QueryPurpose.LISTEN, collectionPath.toString());
              }

              @Override
              public DatabaseId getDatabaseId() {
                return TEST_PROJECT;
              }
            });

    SnapshotVersion version = SnapshotVersion.NONE;

    for (MutableDocument doc : docs) {
      DocumentChange change =
          new DocumentChange(updatedInTargets, removedFromTargets, doc.getKey(), doc);
      aggregator.handleDocumentChange(change);
      version = doc.getVersion().compareTo(version) > 0 ? doc.getVersion() : version;
    }

    return aggregator.createRemoteEvent(version);
  }

  public static RemoteEvent addedRemoteEvent(MutableDocument doc, Integer targetId) {
    return addedRemoteEvent(singletonList(doc), singletonList(targetId), emptyList());
  }

  public static RemoteEvent updateRemoteEvent(
      MutableDocument doc, List<Integer> updatedInTargets, List<Integer> removedFromTargets) {
    List<Integer> activeTargets = new ArrayList<>();
    activeTargets.addAll(updatedInTargets);
    activeTargets.addAll(removedFromTargets);
    return updateRemoteEvent(doc, updatedInTargets, removedFromTargets, activeTargets);
  }

  public static RemoteEvent updateRemoteEvent(
      MutableDocument doc,
      List<Integer> updatedInTargets,
      List<Integer> removedFromTargets,
      List<Integer> activeTargets) {
    DocumentChange change =
        new DocumentChange(updatedInTargets, removedFromTargets, doc.getKey(), doc);
    WatchChangeAggregator aggregator =
        new WatchChangeAggregator(
            new WatchChangeAggregator.TargetMetadataProvider() {
              @Override
              public ImmutableSortedSet<DocumentKey> getRemoteKeysForTarget(int targetId) {
                return DocumentKey.emptyKeySet().insert(doc.getKey());
              }

              @Override
              public TargetData getTargetDataForTarget(int targetId) {
                return activeTargets.contains(targetId)
                    ? targetData(targetId, QueryPurpose.LISTEN, doc.getKey().toString())
                    : null;
              }

              @Override
              public DatabaseId getDatabaseId() {
                return TEST_PROJECT;
              }
            });
    aggregator.handleDocumentChange(change);
    return aggregator.createRemoteEvent(doc.getVersion());
  }

  public static SetMutation setMutation(String path, Map<String, Object> values) {
    UserDataReader dataReader = new UserDataReader(TEST_PROJECT);
    ParsedSetData parsed = dataReader.parseSetData(values);

    // The order of the transforms doesn't matter, but we sort them so tests can assume a particular
    // order.
    ArrayList<FieldTransform> fieldTransforms = new ArrayList<>(parsed.getFieldTransforms());
    Collections.sort(
        fieldTransforms, (ft1, ft2) -> ft1.getFieldPath().compareTo(ft2.getFieldPath()));

    return new SetMutation(key(path), parsed.getData(), Precondition.NONE, fieldTransforms);
  }

  public static PatchMutation patchMutation(String path, Map<String, Object> values) {
    return patchMutationHelper(path, values, Precondition.exists(true), null);
  }

  public static PatchMutation mergeMutation(
      String path, Map<String, Object> values, List<FieldPath> updateMask) {
    return patchMutationHelper(path, values, Precondition.NONE, updateMask);
  }

  private static PatchMutation patchMutationHelper(
      String path,
      Map<String, Object> values,
      Precondition precondition,
      @Nullable List<FieldPath> updateMask) {
    // Replace '<DELETE>' from JSON
    for (Entry<String, Object> entry : values.entrySet()) {
      if (entry.getValue().equals(DELETE_SENTINEL)) {
        values.put(entry.getKey(), FieldValue.delete());
      }
    }

    UserDataReader dataReader = new UserDataReader(TEST_PROJECT);
    ParsedUpdateData parsed = dataReader.parseUpdateData(values);

    // `mergeMutation()` provides an update mask for the merged fields, whereas `patchMutation()`
    // requires the update mask to be parsed from the values.
    Collection<FieldPath> mask = updateMask != null ? updateMask : parsed.getFieldMask().getMask();

    // We sort the fieldMaskPaths to make the order deterministic in tests. (Otherwise, when we
    // flatten a Set to a proto repeated field, we'll end up comparing in iterator order and
    // possibly consider {foo,bar} != {bar,foo}.)
    SortedSet<FieldPath> fieldMaskPaths = new TreeSet<>(mask);

    // The order of the transforms doesn't matter, but we sort them so tests can assume a particular
    // order.
    ArrayList<FieldTransform> fieldTransforms = new ArrayList<>(parsed.getFieldTransforms());
    Collections.sort(
        fieldTransforms, (ft1, ft2) -> ft1.getFieldPath().compareTo(ft2.getFieldPath()));

    return new PatchMutation(
        key(path),
        parsed.getData(),
        FieldMask.fromSet(fieldMaskPaths),
        precondition,
        fieldTransforms);
  }

  public static DeleteMutation deleteMutation(String path) {
    return new DeleteMutation(key(path), Precondition.NONE);
  }

  public static VerifyMutation verifyMutation(String path, int micros) {
    return new VerifyMutation(key(path), Precondition.updateTime(version(micros)));
  }

  public static MutationResult mutationResult(long version) {
    return new MutationResult(version(version), emptyList());
  }

  public static LocalViewChanges viewChanges(
      int targetId, boolean fromCache, List<String> addedKeys, List<String> removedKeys) {
    ImmutableSortedSet<DocumentKey> added = DocumentKey.emptyKeySet();
    for (String keyPath : addedKeys) {
      added = added.insert(key(keyPath));
    }
    ImmutableSortedSet<DocumentKey> removed = DocumentKey.emptyKeySet();
    for (String keyPath : removedKeys) {
      removed = removed.insert(key(keyPath));
    }
    return new LocalViewChanges(targetId, fromCache, added, removed);
  }

  /** Creates a resume token to match the given snapshot version. */
  @Nullable
  public static ByteString resumeToken(long snapshotVersion) {
    if (snapshotVersion == 0) {
      return null;
    }

    String snapshotString = "snapshot-" + snapshotVersion;
    return ByteString.copyFrom(snapshotString, Charsets.UTF_8);
  }

  public static ByteString streamToken(String contents) {
    return ByteString.copyFrom(contents, Charsets.UTF_8);
  }

  public static FieldIndex fieldIndex(
      String collectionGroup,
      int indexId,
      IndexState indexState,
      String field,
      FieldIndex.Segment.Kind kind,
      Object... fieldAndKinds) {
    List<FieldIndex.Segment> segments = new ArrayList<>();
    segments.add(FieldIndex.Segment.create(field(field), kind));
    for (int i = 0; i < fieldAndKinds.length; i += 2) {
      segments.add(
          FieldIndex.Segment.create(
              field((String) fieldAndKinds[i]), (FieldIndex.Segment.Kind) fieldAndKinds[i + 1]));
    }
    return FieldIndex.create(indexId, collectionGroup, segments, indexState);
  }

  public static FieldIndex fieldIndex(
      String collectionGroup, String field, FieldIndex.Segment.Kind kind, Object... fieldsAndKind) {
    FieldIndex fieldIndex =
        fieldIndex(collectionGroup, -1, FieldIndex.INITIAL_STATE, field, kind, fieldsAndKind);
    return fieldIndex;
  }

  public static FieldIndex fieldIndex(String collectionGroup, int indexId, IndexState indexState) {
    return FieldIndex.create(indexId, collectionGroup, emptyList(), indexState);
  }

  public static FieldIndex fieldIndex(String collectionGroup) {
    return fieldIndex(collectionGroup, -1, FieldIndex.INITIAL_STATE);
  }

  private static Map<String, Object> fromJsonString(String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<String, Object> fromSingleQuotedString(String json) {
    return fromJsonString(json.replace("'", "\""));
  }

  /** Returns an iterable that iterates over the keys in a map. */
  public static <K, V> Iterable<K> keys(Iterable<Map.Entry<K, V>> map) {
    return () -> {
      Iterator<Entry<K, V>> iterator = map.iterator();
      return new Iterator<K>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public K next() {
          return iterator.next().getKey();
        }
      };
    };
  }
  /**
   * Asserts that the actual set is equal to the expected one.
   *
   * @param expected A list of the expected contents of the set, in order.
   * @param actual The set to compare against.
   * @param <T> The type of the values of in common between the expected list and actual set.
   */
  // PORTING NOTE: JUnit and XCTest use reversed conventions on expected and actual values :-(.
  public static <T> void assertSetEquals(List<T> expected, ImmutableSortedSet<T> actual) {
    List<T> actualList = Lists.newArrayList(actual);
    assertEquals(expected, actualList);
  }

  /**
   * Asserts that the actual set is equal to the expected one.
   *
   * @param expected A list of the expected contents of the set, in order.
   * @param actual The set to compare against.
   * @param <T> The type of the values of in common between the expected list and actual set.
   */
  // PORTING NOTE: JUnit and XCTest use reversed conventions on expected and actual values :-(.
  public static <T> void assertSetEquals(List<T> expected, Set<T> actual) {
    Set<T> expectedSet = Sets.newHashSet(expected);
    assertEquals(expectedSet, actual);
  }

  /** Asserts that the given runnable block fails with an internal error. */
  public static void assertFails(Runnable block) {
    try {
      block.run();
    } catch (AssertionError e) {
      assertThat(e).hasMessageThat().startsWith("INTERNAL ASSERTION FAILED:");
      // Otherwise success
      return;
    }
    fail("Should have failed");
  }

  public static void assertDoesNotThrow(Runnable block) {
    try {
      block.run();
    } catch (Exception e) {
      fail("Should not have thrown " + e);
    }
  }

  // TODO: We could probably do some de-duplication between assertFails / expectError.
  /** Expects runnable to throw an exception with a specific error message. */
  public static void expectError(Runnable runnable, String exceptionMessage) {
    expectError(runnable, exceptionMessage, /*context=*/ null);
  }

  /**
   * Expects runnable to throw an exception with a specific error message. An optional context (e.g.
   * "for bad_data") can be provided which will be displayed in any resulting failure message.
   */
  public static void expectError(Runnable runnable, String exceptionMessage, String context) {
    boolean exceptionThrown = false;
    try {
      runnable.run();
    } catch (Throwable throwable) {
      exceptionThrown = true;
      String contextMessage = "Expected exception message was incorrect";
      if (context != null) {
        contextMessage += " (" + context + ")";
      }
      assertEquals(contextMessage, exceptionMessage, throwable.getMessage());
    }
    if (!exceptionThrown) {
      context = (context == null) ? "" : context;
      fail(
          "Expected exception with message '"
              + exceptionMessage
              + "' but no exception was thrown"
              + context);
    }
  }
}
