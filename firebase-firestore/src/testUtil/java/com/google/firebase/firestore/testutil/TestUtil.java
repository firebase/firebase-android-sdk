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
import static java.util.Arrays.asList;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.TestAccessHelper;
import com.google.firebase.firestore.UserDataReader;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter.Operator;
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
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.UnknownDocument;
import com.google.firebase.firestore.model.Values;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.SetMutation;
import com.google.firebase.firestore.model.mutation.VerifyMutation;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;

/** A set of utilities for tests */
public class TestUtil {

  /** A string sentinel that can be used with patchMutation() to mark a field for deletion. */
  public static final String DELETE_SENTINEL = "<DELETE>";

  public static final long ARBITRARY_SEQUENCE_NUMBER = 2;

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
    DatabaseId databaseId = DatabaseId.forProject("project");
    UserDataReader dataReader = new UserDataReader(databaseId);
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
    return FieldPath.fromSegments(Arrays.asList(path.split("\\.")));
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

  public static Document doc(String key, long version, Map<String, Object> data) {
    return new Document(
        key(key), version(version), wrapObject(data), Document.DocumentState.SYNCED);
  }

  public static Document doc(DocumentKey key, long version, Map<String, Object> data) {
    return new Document(key, version(version), wrapObject(data), Document.DocumentState.SYNCED);
  }

  public static Document doc(
      String key, long version, ObjectValue data, Document.DocumentState documentState) {
    return new Document(key(key), version(version), data, documentState);
  }

  public static Document doc(
      String key, long version, Map<String, Object> data, Document.DocumentState documentState) {
    return new Document(key(key), version(version), wrapObject(data), documentState);
  }

  public static NoDocument deletedDoc(String key, long version) {
    return deletedDoc(key, version, /*hasCommittedMutations=*/ false);
  }

  public static NoDocument deletedDoc(String key, long version, boolean hasCommittedMutations) {
    return new NoDocument(key(key), version(version), hasCommittedMutations);
  }

  public static UnknownDocument unknownDoc(String key, long version) {
    return new UnknownDocument(key(key), version(version));
  }

  public static DocumentSet docSet(Comparator<Document> comparator, Document... documents) {
    DocumentSet set = DocumentSet.emptySet(comparator);
    for (Document document : documents) {
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

  public static FieldFilter filter(String key, String operator, Object value) {
    return FieldFilter.create(field(key), operatorFromString(operator), wrap(value));
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

  public static ImmutableSortedMap<DocumentKey, MaybeDocument> docUpdates(MaybeDocument... docs) {
    ImmutableSortedMap<DocumentKey, MaybeDocument> res =
        ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());
    for (MaybeDocument doc : docs) {
      res = res.insert(doc.getKey(), doc);
    }
    return res;
  }

  public static ImmutableSortedMap<DocumentKey, Document> docUpdates(Document... docs) {
    ImmutableSortedMap<DocumentKey, Document> res =
        ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());
    for (Document doc : docs) {
      res = res.insert(doc.getKey(), doc);
    }
    return res;
  }

  public static TargetChange targetChange(
      ByteString resumeToken,
      boolean current,
      @Nullable Collection<Document> addedDocuments,
      @Nullable Collection<Document> modifiedDocuments,
      @Nullable Collection<? extends MaybeDocument> removedDocuments) {
    ImmutableSortedSet<DocumentKey> addedDocumentKeys = DocumentKey.emptyKeySet();
    ImmutableSortedSet<DocumentKey> modifiedDocumentKeys = DocumentKey.emptyKeySet();
    ImmutableSortedSet<DocumentKey> removedDocumentKeys = DocumentKey.emptyKeySet();

    if (addedDocuments != null) {
      for (Document document : addedDocuments) {
        addedDocumentKeys = addedDocumentKeys.insert(document.getKey());
      }
    }

    if (modifiedDocuments != null) {
      for (Document document : modifiedDocuments) {
        modifiedDocumentKeys = modifiedDocumentKeys.insert(document.getKey());
      }
    }

    if (removedDocuments != null) {
      for (MaybeDocument document : removedDocuments) {
        removedDocumentKeys = removedDocumentKeys.insert(document.getKey());
      }
    }

    return new TargetChange(
        resumeToken, current, addedDocumentKeys, modifiedDocumentKeys, removedDocumentKeys);
  }

  public static TargetChange ackTarget(Document... docs) {
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
      MaybeDocument doc, List<Integer> updatedInTargets, List<Integer> removedFromTargets) {
    return addedRemoteEvent(Collections.singletonList(doc), updatedInTargets, removedFromTargets);
  }

  public static RemoteEvent addedRemoteEvent(
      List<MaybeDocument> docs, List<Integer> updatedInTargets, List<Integer> removedFromTargets) {
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
                ResourcePath collectionPath = docs.get(0).getKey().getPath().popLast();
                return targetData(targetId, QueryPurpose.LISTEN, collectionPath.toString());
              }
            });

    SnapshotVersion version = SnapshotVersion.NONE;

    for (MaybeDocument doc : docs) {
      DocumentChange change =
          new DocumentChange(updatedInTargets, removedFromTargets, doc.getKey(), doc);
      aggregator.handleDocumentChange(change);
      version = doc.getVersion().compareTo(version) > 0 ? doc.getVersion() : version;
    }

    return aggregator.createRemoteEvent(version);
  }

  public static RemoteEvent updateRemoteEvent(
      MaybeDocument doc, List<Integer> updatedInTargets, List<Integer> removedFromTargets) {
    List<Integer> activeTargets = new ArrayList<>();
    activeTargets.addAll(updatedInTargets);
    activeTargets.addAll(removedFromTargets);
    return updateRemoteEvent(doc, updatedInTargets, removedFromTargets, activeTargets);
  }

  public static RemoteEvent updateRemoteEvent(
      MaybeDocument doc,
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
            });
    aggregator.handleDocumentChange(change);
    return aggregator.createRemoteEvent(doc.getVersion());
  }

  public static SetMutation setMutation(String path, Map<String, Object> values) {
    UserDataReader dataReader = new UserDataReader(DatabaseId.forProject("project"));
    ParsedSetData parsed = dataReader.parseSetData(values);

    // The order of the transforms doesn't matter, but we sort them so tests can assume a particular
    // order.
    ArrayList<FieldTransform> fieldTransforms = new ArrayList<>(parsed.getFieldTransforms());
    Collections.sort(
        fieldTransforms, (ft1, ft2) -> ft1.getFieldPath().compareTo(ft2.getFieldPath()));

    return new SetMutation(key(path), parsed.getData(), Precondition.NONE, fieldTransforms);
  }

  public static PatchMutation patchMutation(String path, Map<String, Object> values) {
    return patchMutation(path, values, null);
  }

  public static PatchMutation patchMutation(
      String path, Map<String, Object> values, @Nullable List<FieldPath> updateMask) {
    // Replace '<DELETE>' from JSON with FieldValue
    for (Entry<String, Object> entry : values.entrySet()) {
      if (entry.getValue().equals(DELETE_SENTINEL)) {
        values.put(entry.getKey(), FieldValue.delete());
      }
    }

    UserDataReader dataReader = new UserDataReader(DatabaseId.forProject("project"));
    ParsedUpdateData parsed = dataReader.parseUpdateData(values);
    boolean merge = updateMask != null;

    // We sort the fieldMaskPaths to make the order deterministic in tests. (Otherwise, when we
    // flatten a Set to a proto repeated field, we'll end up comparing in iterator order and
    // possibly consider {foo,bar} != {bar,foo}.)
    SortedSet<FieldPath> fieldMaskPaths =
        new TreeSet<>(merge ? updateMask : parsed.getFieldMask().getMask());

    // The order of the transforms doesn't matter, but we sort them so tests can assume a particular
    // order.
    ArrayList<FieldTransform> fieldTransforms = new ArrayList<>(parsed.getFieldTransforms());
    Collections.sort(
        fieldTransforms, (ft1, ft2) -> ft1.getFieldPath().compareTo(ft2.getFieldPath()));

    return new PatchMutation(
        key(path),
        parsed.getData(),
        FieldMask.fromSet(fieldMaskPaths),
        merge ? Precondition.NONE : Precondition.exists(true),
        fieldTransforms);
  }

  public static DeleteMutation deleteMutation(String path) {
    return new DeleteMutation(key(path), Precondition.NONE);
  }

  public static VerifyMutation verifyMutation(String path, int micros) {
    return new VerifyMutation(key(path), Precondition.updateTime(version(micros)));
  }

  public static MutationResult mutationResult(long version) {
    return new MutationResult(version(version), null);
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

  /** Converts the values of an ImmutableSortedMap into a list, preserving key order. */
  public static <T> List<T> values(ImmutableSortedMap<?, T> map) {
    List<T> result = new ArrayList<>();
    for (Map.Entry<?, T> entry : map) {
      result.add(entry.getValue());
    }
    return result;
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
