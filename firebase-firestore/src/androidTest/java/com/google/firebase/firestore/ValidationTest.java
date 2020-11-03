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

package com.google.firebase.firestore;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.Assert.assertThrows;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testAlternateFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.assertDoesNotThrow;
import static com.google.firebase.firestore.testutil.TestUtil.expectError;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.firestore.Transaction.Function;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import com.google.firebase.firestore.util.Consumer;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

// NOTE: The SDK has exhaustive nullability checks, but we don't exhaustively test them. :-)
@SuppressWarnings("ConstantConditions")
@RunWith(AndroidJUnit4.class)
public class ValidationTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void firestoreSettingsNullHostFails() {
    expectError(
        () -> new FirebaseFirestoreSettings.Builder().setHost(null).build(),
        "Provided host must not be null.");
  }

  @Test
  public void changingSettingsAfterUseFails() {
    DocumentReference ref = testDocument();
    // Force initialization of the underlying client
    waitFor(ref.set(map("key", "value")));
    expectError(
        () ->
            ref.getFirestore()
                .setFirestoreSettings(
                    new FirebaseFirestoreSettings.Builder().setHost("foo").build()),
        "FirebaseFirestore has already been started and its settings "
            + "can no longer be changed. You can only call setFirestoreSettings() before "
            + "calling any other methods on a FirebaseFirestore object.");
  }

  @Test
  public void disableSslWithoutSettingHostFails() {
    expectError(
        () -> new FirebaseFirestoreSettings.Builder().setSslEnabled(false).build(),
        "You can't set the 'sslEnabled' setting unless you also set a non-default 'host'.");
  }

  @Test
  public void firestoreGetInstanceWithNullAppFails() {
    expectError(
        () -> FirebaseFirestore.getInstance(null), "Provided FirebaseApp must not be null.");
  }

  @Test
  public void firestoreGetInstanceWithNonNullAppReturnsNonNullInstance() {
    withApp("firestoreTestApp", app -> assertNotNull(FirebaseFirestore.getInstance(app)));
  }

  private static void withApp(String name, Consumer<FirebaseApp> toRun) {
    FirebaseApp app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApiKey("key")
                .setApplicationId("appId")
                .setProjectId("projectId")
                .build(),
            name);
    try {
      toRun.accept(app);
    } finally {
      app.delete();
    }
  }

  @Test
  public void collectionPathsMustBeOddLength() {
    FirebaseFirestore db = testFirestore();
    DocumentReference baseDocRef = db.document("foo/bar");
    List<String> badAbsolutePaths = asList("foo/bar", "foo/bar/baz/quu");
    List<String> badRelativePaths = asList("/", "baz/quu");
    List<Integer> badPathLengths = asList(2, 4);

    for (int i = 0; i < badAbsolutePaths.size(); i++) {
      String path = badAbsolutePaths.get(i);
      String relativePath = badRelativePaths.get(i);
      String error =
          "Invalid collection reference. Collection references must have an odd number "
              + "of segments, but "
              + path
              + " has "
              + badPathLengths.get(i);
      expectError(() -> db.collection(path), error);
      expectError(() -> baseDocRef.collection(relativePath), error);
    }
  }

  @Test
  public void pathsMustNotHaveEmptySegments() {
    FirebaseFirestore db = testFirestore();
    // NOTE: leading / trailing slashes are okay.
    db.collection("/foo/");
    db.collection("/foo");
    db.collection("foo/");

    List<String> badPaths = asList("foo//bar//baz", "//foo", "foo//");
    CollectionReference collection = db.collection("test-collection");
    DocumentReference doc = collection.document("test-document");
    for (String path : badPaths) {
      String reason = "Invalid path (" + path + "). Paths must not contain // in them.";
      expectError(() -> db.collection(path), reason);
      expectError(() -> db.document(path), reason);
      expectError(() -> collection.document(path), reason);
      expectError(() -> doc.collection(path), reason);
    }
  }

  @Test
  public void documentPathsMustBeEvenLength() {
    FirebaseFirestore db = testFirestore();
    CollectionReference baseCollectionRef = db.collection("foo");
    List<String> badAbsolutePaths = asList("foo", "foo/bar/baz");
    List<String> badRelativePaths = asList("/", "bar/baz");
    List<Integer> badPathLengths = asList(1, 3);

    for (int i = 0; i < badAbsolutePaths.size(); i++) {
      String path = badAbsolutePaths.get(i);
      String relativePath = badRelativePaths.get(i);
      String error =
          "Invalid document reference. Document references must have an even number "
              + "of segments, but "
              + path
              + " has "
              + badPathLengths.get(i);
      expectError(() -> db.document(path), error);
      expectError(() -> baseCollectionRef.document(relativePath), error);
    }
  }

  @Test
  public void writesMustBeMapsOrPOJOs() {
    expectSetError(null, "Provided data must not be null.");

    String reason =
        "Invalid data. Data must be a Map<String, Object> or a suitable POJO "
            + "object, but it was ";

    expectSetError(new int[] {1}, reason + "an array");

    List<Object> badData =
        asList(42, "test", new Date(), new GeoPoint(0, 0), Blob.fromBytes(new byte[] {0}));
    for (Object data : badData) {
      expectSetError(data, reason + "of type: " + data.getClass().getName());
    }
  }

  @Test
  public void writesMustNotContainDirectlyNestedLists() {
    expectWriteError(
        map("nested-array", asList(1, asList(2))), "Invalid data. Nested arrays are not supported");
  }

  @Test
  public void writesMayContainIndirectlyNestedLists() {
    Map<String, Object> data = map("nested-array", asList(1, map("foo", asList(2))));

    CollectionReference collection = testCollection();
    DocumentReference ref = collection.document();
    DocumentReference ref2 = collection.document();

    waitFor(ref.set(data));
    waitFor(ref.getFirestore().batch().set(ref, data).commit());

    waitFor(ref.update(data));
    waitFor(ref.getFirestore().batch().update(ref, data).commit());

    waitFor(
        ref.getFirestore()
            .runTransaction(
                transaction -> {
                  // Note ref2 does not exist at this point so set that and update ref.
                  transaction.update(ref, data);
                  transaction.set(ref2, data);
                  return null;
                }));
  }

  @Test
  public void writesMustNotContainReferencesToADifferentDatabase() {
    FirebaseFirestore db2 = testAlternateFirestore();
    DocumentReference ref = db2.document("baz/quu");
    Map<String, Object> data = map("foo", ref);
    expectWriteError(
        data,
        String.format(
            "Invalid data. Document reference is for database %s/(default) but should be for "
                + "database %s/(default) (found in field foo)",
            IntegrationTestUtil.BAD_PROJECT_ID, IntegrationTestUtil.provider().projectId()));
  }

  @Test
  public void writesMustNotContainReservedFieldNames() {
    expectWriteSuccess(map("__bar", 1));
    expectWriteSuccess(map("bar__", 1));

    expectWriteError(
        map("__baz__", 1),
        "Invalid data. Document fields cannot begin and end with \"__\" (found in field __baz__)");
    expectWriteError(
        map("foo", map("__baz__", 1)),
        "Invalid data. Document fields cannot begin and end with \"__\" (found in field foo.__baz__)");
    expectWriteError(
        map("__baz__", map("foo", 1)),
        "Invalid data. Document fields cannot begin and end with \"__\" (found in field __baz__)");

    expectUpdateError(
        map("__baz__", 1),
        "Invalid data. Document fields cannot begin and end with \"__\" (found in field __baz__)");
    expectUpdateError(
        map("baz.__foo__", 1),
        "Invalid data. Document fields cannot begin and end with \"__\" (found in field baz.__foo__)");
  }

  @Test
  public void writesMustNotContainEmptyFieldNames() {
    expectSetError(
        map("", "foo"), "Invalid data. Document fields must not be empty (found in field ``)");
  }

  @Test
  public void setsMustNotContainFieldValueDelete() {
    expectSetError(
        map("foo", FieldValue.delete()),
        "Invalid data. FieldValue.delete() can only be used with update() and set() with "
            + "SetOptions.merge() (found in field foo)");
  }

  @Test
  public void updatesMustNotContainNestedFieldValueDeletes() {
    expectUpdateError(
        map("foo", map("bar", FieldValue.delete())),
        "Invalid data. FieldValue.delete() can only appear at the top level of your update data "
            + "(found in field foo.bar)");
  }

  @Test
  public void batchWritesRequireCorrectDocumentReferences() {
    DocumentReference badRef = testAlternateFirestore().document("foo/bar");
    String reason = "Provided document reference is from a different Cloud Firestore instance.";
    Map<String, Object> data = map("foo", 1);
    WriteBatch batch = testFirestore().batch();
    expectError(() -> batch.set(badRef, data), reason);
    expectError(() -> batch.update(badRef, data), reason);
    expectError(() -> batch.delete(badRef), reason);
  }

  @Test
  public void transactionsRequireCorrectDocumentReferences() {
    DocumentReference badRef = testAlternateFirestore().document("foo/bar");
    String reason = "Provided document reference is from a different Cloud Firestore instance.";
    Map<String, Object> data = map("foo", 1);
    waitFor(
        testFirestore()
            .runTransaction(
                (Function<Void>)
                    transaction -> {
                      expectError(
                          () -> {
                            // Because .get() throws a checked exception for missing docs, we have
                            // to try/catch it.
                            try {
                              transaction.get(badRef);
                            } catch (FirebaseFirestoreException e) {
                              fail("transaction.get() triggered wrong exception: " + e);
                            }
                          },
                          reason);

                      expectError(() -> transaction.set(badRef, data), reason);
                      expectError(() -> transaction.update(badRef, data), reason);
                      expectError(() -> transaction.delete(badRef), reason);
                      return null;
                    }));
  }

  @Test
  public void fieldPathsMustNotHaveEmptySegments() {
    List<String> badFieldPaths = asList("", "foo..baz", ".foo", "foo.");
    for (String fieldPath : badFieldPaths) {
      String reason =
          "Invalid field path ("
              + fieldPath
              + "). Paths must not be empty, begin with '.', end with '.', or contain '..'";
      verifyFieldPathThrows(fieldPath, reason);
    }
  }

  @Test
  public void fieldPathsMustNotHaveInvalidSegments() {
    List<String> badFieldPaths =
        asList("foo~bar", "foo*bar", "foo/bar", "foo[1", "foo]1", "foo[1]");
    for (String fieldPath : badFieldPaths) {
      verifyFieldPathThrows(fieldPath, "Use FieldPath.of() for field names containing '~*/[]'.");
    }
  }

  @Test
  public void fieldNamesMustNotBeEmpty() {
    String reason = "Invalid field path. Provided path must not be empty.";
    expectError(FieldPath::of, reason);

    reason = "Invalid field name at argument 1. Field names must not be null or empty.";
    expectError(() -> FieldPath.of((String) null), reason);
    expectError(() -> FieldPath.of(""), reason);

    reason = "Invalid field name at argument 2. Field names must not be null or empty.";
    expectError(() -> FieldPath.of("foo", ""), reason);
    expectError(() -> FieldPath.of("foo", null), reason);
  }

  @Test
  public void arrayTransformsFailInQueries() {
    CollectionReference collection = testCollection();
    String reason =
        "Invalid data. FieldValue.arrayUnion() can only be used with set() and update() "
            + "(found in field test)";
    expectError(
        () -> collection.whereEqualTo("test", map("test", FieldValue.arrayUnion(1))), reason);

    reason =
        "Invalid data. FieldValue.arrayRemove() can only be used with set() and update() "
            + "(found in field test)";
    expectError(
        () -> collection.whereEqualTo("test", map("test", FieldValue.arrayRemove(1))), reason);
  }

  @Test
  public void arrayTransformsRejectInvalidElements() {
    DocumentReference doc = testDocument();
    String reason =
        "No properties to serialize found on class com.google.firebase.firestore.ValidationTest";
    expectError(() -> doc.set(map("x", FieldValue.arrayUnion(1, this))), reason);
    expectError(() -> doc.set(map("x", FieldValue.arrayRemove(1, this))), reason);
  }

  @Test
  public void arrayTransformsRejectArrays() {
    DocumentReference doc = testDocument();
    // This would result in a directly nested array which is not supported.
    String reason = "Invalid data. Nested arrays are not supported";
    expectError(() -> doc.set(map("x", FieldValue.arrayUnion(1, asList("nested")))), reason);
    expectError(() -> doc.set(map("x", FieldValue.arrayRemove(1, asList("nested")))), reason);
  }

  @Test
  public void queriesWithNonPositiveLimitFail() {
    CollectionReference collection = testCollection();
    expectError(
        () -> collection.limit(0),
        "Invalid Query. Query limit (0) is invalid. Limit must be positive.");
    expectError(
        () -> collection.limit(-1),
        "Invalid Query. Query limit (-1) is invalid. Limit must be positive.");
    expectError(
        () -> collection.limitToLast(0),
        "Invalid Query. Query limitToLast (0) is invalid. Limit must be positive.");
    expectError(
        () -> collection.limitToLast(-1),
        "Invalid Query. Query limitToLast (-1) is invalid. Limit must be positive.");
  }

  @Test
  public void queriesCannotBeCreatedFromDocumentsMissingSortValues() {
    CollectionReference collection = testCollectionWithDocs(map("f", map("k", "f", "nosort", 1.0)));

    Query query = collection.orderBy("sort");
    DocumentSnapshot snapshot = waitFor(collection.document("f").get());

    assertEquals(map("k", "f", "nosort", 1.0), snapshot.getData());

    String reason =
        "Invalid query. You are trying to start or end a query using a document "
            + "for which the field 'sort' (used as the orderBy) does not exist.";
    expectError(() -> query.startAt(snapshot), reason);
    expectError(() -> query.startAfter(snapshot), reason);
    expectError(() -> query.endBefore(snapshot), reason);
    expectError(() -> query.endAt(snapshot), reason);
  }

  @Test
  public void queriesCannotBeSortedByAnUncommittedServerTimestamp() {
    CollectionReference collection = testCollection();

    // Ensure the server timestamp stays uncommitted for the first half of the test
    waitFor(collection.firestore.getClient().disableNetwork());

    TaskCompletionSource<Void> offlineCallbackDone = new TaskCompletionSource<>();
    TaskCompletionSource<Void> onlineCallbackDone = new TaskCompletionSource<>();

    ListenerRegistration listenerRegistration =
        collection.addSnapshotListener(
            (snapshot, error) -> {
              assertNotNull(snapshot);

              // Skip the initial empty snapshot.
              if (snapshot.isEmpty()) return;

              assertThat(snapshot.getDocuments()).hasSize(1);
              DocumentSnapshot docSnap = snapshot.getDocuments().get(0);

              if (snapshot.getMetadata().hasPendingWrites()) {
                // Offline snapshot. Since the server timestamp is uncommitted, we shouldn't be able
                // to query by it.
                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        collection
                            .orderBy("timestamp")
                            .endAt(docSnap)
                            .addSnapshotListener((snapshot2, error2) -> {}));
                // Use `trySetResult` since the callbacks fires twice if the WatchStream
                // acknowledges the Write before the WriteStream.
                offlineCallbackDone.trySetResult(null);
              } else {
                // Online snapshot. Since the server timestamp is committed, we should be able to
                // query by it.
                collection
                    .orderBy("timestamp")
                    .endAt(docSnap)
                    .addSnapshotListener((snapshot2, error2) -> {});
                onlineCallbackDone.trySetResult(null);
              }
            });

    DocumentReference document = collection.document();
    document.set(map("timestamp", FieldValue.serverTimestamp()));
    waitFor(offlineCallbackDone.getTask());

    waitFor(collection.firestore.getClient().enableNetwork());
    waitFor(onlineCallbackDone.getTask());

    listenerRegistration.remove();
  }

  @Test
  public void queriesMustNotHaveMoreComponentsThanOrderBy() {
    CollectionReference collection = testCollection();
    Query query = collection.orderBy("foo");

    String reason =
        "Too many arguments provided to startAt(). The number of arguments "
            + "must be less than or equal to the number of orderBy() clauses.";
    expectError(() -> query.startAt(1, 2), reason);
    expectError(() -> query.orderBy("bar").startAt(1, 2, 3), reason);
  }

  @Test
  public void queryOrderByKeyBoundsMustBeStringsWithoutSlashes() {
    Query query = testFirestore().collection("collection").orderBy(FieldPath.documentId());
    Query cgQuery = testFirestore().collectionGroup("collection").orderBy(FieldPath.documentId());
    expectError(
        () -> query.startAt(1),
        "Invalid query. Expected a string for document ID in startAt(), but got 1.");
    expectError(
        () -> query.startAt("foo/bar"),
        "Invalid query. When querying a collection and ordering by "
            + "FieldPath.documentId(), the value passed to startAt() must be a plain "
            + "document ID, but 'foo/bar' contains a slash.");
    expectError(
        () -> cgQuery.startAt("foo"),
        "Invalid query. When querying a collection group and ordering by "
            + "FieldPath.documentId(), the value passed to startAt() must result in a valid "
            + "document path, but 'foo' is not because it contains an odd number of segments.");
  }

  @Test
  public void queriesWithDifferentInequalityFieldsFail() {
    expectError(
        () -> testCollection().whereGreaterThan("x", 32).whereLessThan("y", "cat"),
        "All where filters with an inequality (notEqualTo, notIn, lessThan, "
            + "lessThanOrEqualTo, greaterThan, or greaterThanOrEqualTo) must be on the "
            + "same field. But you have filters on 'x' and 'y'");
  }

  @Test
  public void queriesWithInequalityDifferentThanFirstOrderByFail() {
    CollectionReference collection = testCollection();
    String reason =
        "Invalid query. You have an inequality where filter (whereLessThan(), "
            + "whereGreaterThan(), etc.) on field 'x' and so you must also have 'x' as "
            + "your first orderBy() field, but your first orderBy() is currently on field 'y' "
            + "instead.";
    expectError(() -> collection.whereGreaterThan("x", 32).orderBy("y"), reason);
    expectError(() -> collection.orderBy("y").whereGreaterThan("x", 32), reason);
    expectError(() -> collection.whereGreaterThan("x", 32).orderBy("y").orderBy("x"), reason);
    expectError(() -> collection.orderBy("y").orderBy("x").whereGreaterThan("x", 32), reason);
    expectError(() -> collection.orderBy("y").orderBy("x").whereNotEqualTo("x", 32), reason);
  }

  @Test
  public void queriesWithMultipleNotEqualAndInequalitiesFail() {
    expectError(
        () -> testCollection().whereNotEqualTo("x", 32).whereNotEqualTo("x", 33),
        "Invalid Query. You cannot use more than one '!=' filter.");

    expectError(
        () -> testCollection().whereNotEqualTo("x", 32).whereGreaterThan("y", 33),
        "All where filters with an inequality (notEqualTo, notIn, lessThan, "
            + "lessThanOrEqualTo, greaterThan, or greaterThanOrEqualTo) must be on the "
            + "same field. But you have filters on 'x' and 'y'");
  }

  @Test
  public void queriesWithMultipleArrayFiltersFail() {
    expectError(
        () -> testCollection().whereArrayContains("foo", 1).whereArrayContains("foo", 2),
        "Invalid Query. You cannot use more than one 'array_contains' filter.");

    expectError(
        () ->
            testCollection()
                .whereArrayContains("foo", 1)
                .whereArrayContainsAny("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'array_contains_any' filters with 'array_contains' filters.");

    expectError(
        () ->
            testCollection()
                .whereArrayContainsAny("foo", asList(1, 2))
                .whereArrayContains("foo", 1),
        "Invalid Query. You cannot use 'array_contains' filters with 'array_contains_any' filters.");

    expectError(
        () -> testCollection().whereNotIn("foo", asList(1, 2)).whereArrayContains("foo", 1),
        "Invalid Query. You cannot use 'array_contains' filters with 'not_in' filters.");
  }

  @Test
  public void queriesWithNotEqualAndNotInFiltersFail() {
    expectError(
        () -> testCollection().whereNotIn("foo", asList(1, 2)).whereNotEqualTo("foo", 1),
        "Invalid Query. You cannot use '!=' filters with 'not_in' filters.");

    expectError(
        () -> testCollection().whereNotEqualTo("foo", 1).whereNotIn("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'not_in' filters with '!=' filters.");
  }

  @Test
  public void queriesWithMultipleDisjunctiveFiltersFail() {
    expectError(
        () -> testCollection().whereIn("foo", asList(1, 2)).whereIn("bar", asList(1, 2)),
        "Invalid Query. You cannot use more than one 'in' filter.");

    expectError(
        () -> testCollection().whereNotIn("foo", asList(1, 2)).whereNotIn("bar", asList(1, 2)),
        "All where filters with an inequality (notEqualTo, notIn, lessThan, "
            + "lessThanOrEqualTo, greaterThan, or greaterThanOrEqualTo) must be on the "
            + "same field. But you have filters on 'foo' and 'bar'");

    expectError(
        () ->
            testCollection()
                .whereArrayContainsAny("foo", asList(1, 2))
                .whereArrayContainsAny("bar", asList(1, 2)),
        "Invalid Query. You cannot use more than one 'array_contains_any' filter.");

    expectError(
        () ->
            testCollection()
                .whereArrayContainsAny("foo", asList(1, 2))
                .whereIn("bar", asList(1, 2)),
        "Invalid Query. You cannot use 'in' filters with 'array_contains_any' filters.");

    expectError(
        () ->
            testCollection()
                .whereIn("bar", asList(1, 2))
                .whereArrayContainsAny("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'array_contains_any' filters with 'in' filters.");

    expectError(
        () ->
            testCollection()
                .whereArrayContainsAny("foo", asList(1, 2))
                .whereNotIn("bar", asList(1, 2)),
        "Invalid Query. You cannot use 'not_in' filters with 'array_contains_any' filters.");

    expectError(
        () ->
            testCollection()
                .whereNotIn("bar", asList(1, 2))
                .whereArrayContainsAny("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'array_contains_any' filters with 'not_in' filters.");

    expectError(
        () -> testCollection().whereNotIn("bar", asList(1, 2)).whereIn("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'in' filters with 'not_in' filters.");

    expectError(
        () -> testCollection().whereIn("bar", asList(1, 2)).whereNotIn("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'not_in' filters with 'in' filters.");

    // This is redundant with the above tests, but makes sure our validation doesn't get confused.
    expectError(
        () ->
            testCollection()
                .whereIn("bar", asList(1, 2))
                .whereArrayContains("foo", 1)
                .whereArrayContainsAny("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'array_contains_any' filters with 'in' filters.");

    expectError(
        () ->
            testCollection()
                .whereArrayContains("foo", 1)
                .whereIn("bar", asList(1, 2))
                .whereArrayContainsAny("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'array_contains_any' filters with 'array_contains' filters.");

    expectError(
        () ->
            testCollection()
                .whereNotIn("bar", asList(1, 2))
                .whereArrayContains("foo", 1)
                .whereArrayContainsAny("foo", asList(1, 2)),
        "Invalid Query. You cannot use 'array_contains' filters with 'not_in' filters.");

    expectError(
        () ->
            testCollection()
                .whereArrayContains("foo", 1)
                .whereIn("foo", asList(1, 2))
                .whereNotIn("bar", asList(1, 2)),
        "Invalid Query. You cannot use 'not_in' filters with 'array_contains' filters.");
  }

  @Test
  public void queriesCanUseInWithArrayContains() {
    testCollection().whereArrayContains("foo", 1).whereIn("bar", asList(1, 2));
    testCollection().whereIn("bar", asList(1, 2)).whereArrayContains("foo", 1);

    expectError(
        () ->
            testCollection()
                .whereIn("bar", asList(1, 2))
                .whereArrayContains("foo", 1)
                .whereArrayContains("foo", 1),
        "Invalid Query. You cannot use more than one 'array_contains' filter.");

    expectError(
        () ->
            testCollection()
                .whereArrayContains("foo", 1)
                .whereIn("bar", asList(1, 2))
                .whereIn("bar", asList(1, 2)),
        "Invalid Query. You cannot use more than one 'in' filter.");
  }

  @Test
  public void queriesInAndArrayContainsAnyArrayRules() {
    expectError(
        () -> testCollection().whereIn("bar", asList()),
        "Invalid Query. A non-empty array is required for 'in' filters.");

    expectError(
        () -> testCollection().whereNotIn("bar", asList()),
        "Invalid Query. A non-empty array is required for 'not_in' filters.");

    expectError(
        () -> testCollection().whereArrayContainsAny("bar", asList()),
        "Invalid Query. A non-empty array is required for 'array_contains_any' filters.");

    expectError(
        // The 10 element max includes duplicates.
        () -> testCollection().whereIn("bar", asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9)),
        "Invalid Query. 'in' filters support a maximum of 10 elements in the value array.");

    expectError(
        // The 10 element max includes duplicates.
        () -> testCollection().whereNotIn("bar", asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9)),
        "Invalid Query. 'not_in' filters support a maximum of 10 elements in the value array.");

    expectError(
        // The 10 element max includes duplicates.
        () ->
            testCollection().whereArrayContainsAny("bar", asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 9)),
        "Invalid Query. 'array_contains_any' filters support a maximum of 10 elements in the value array.");
  }

  @Test
  public void queriesMustNotSpecifyStartingOrEndingPointAfterOrderBy() {
    CollectionReference collection = testCollection();
    Query query = collection.orderBy("foo");
    String reason =
        "Invalid query. You must not call Query.startAt() or Query.startAfter() "
            + "before calling Query.orderBy().";
    expectError(() -> query.startAt(1).orderBy("bar"), reason);
    expectError(() -> query.startAfter(1).orderBy("bar"), reason);
    reason =
        "Invalid query. You must not call Query.endAt() or Query.endBefore() "
            + "before calling Query.orderBy().";
    expectError(() -> query.endAt(1).orderBy("bar"), reason);
    expectError(() -> query.endBefore(1).orderBy("bar"), reason);
  }

  @Test
  public void queriesFilteredByDocumentIDMustUseStringsOrDocumentReferences() {
    CollectionReference collection = testCollection();
    String reason =
        "Invalid query. When querying with FieldPath.documentId() you must provide "
            + "a valid document ID, but it was an empty string.";
    expectError(() -> collection.whereGreaterThanOrEqualTo(FieldPath.documentId(), ""), reason);

    reason =
        "Invalid query. When querying a collection by FieldPath.documentId() you must provide "
            + "a plain document ID, but 'foo/bar/baz' contains a '/' character.";
    expectError(
        () -> collection.whereGreaterThanOrEqualTo(FieldPath.documentId(), "foo/bar/baz"), reason);

    reason =
        "Invalid query. When querying with FieldPath.documentId() you must provide "
            + "a valid String or DocumentReference, but it was of type: java.lang.Integer";
    expectError(() -> collection.whereGreaterThanOrEqualTo(FieldPath.documentId(), 1), reason);

    reason =
        "Invalid query. When querying a collection group by FieldPath.documentId(), the value "
            + "provided must result in a valid document path, but 'foo' is not because it has "
            + "an odd number of segments (1).";
    expectError(
        () ->
            testFirestore()
                .collectionGroup("collection")
                .whereGreaterThanOrEqualTo(FieldPath.documentId(), "foo"),
        reason);

    reason = "Invalid query. You can't perform 'array_contains' queries on FieldPath.documentId().";
    expectError(() -> collection.whereArrayContains(FieldPath.documentId(), 1), reason);

    reason =
        "Invalid query. You can't perform 'array_contains_any' queries on FieldPath.documentId().";
    expectError(
        () -> collection.whereArrayContainsAny(FieldPath.documentId(), asList(1, 2)), reason);
  }

  @Test
  public void queriesUsingInAndDocumentIdMustHaveProperDocumentReferencesInArray() {
    CollectionReference collection = testCollection();
    collection.whereIn(FieldPath.documentId(), asList(collection.getPath()));

    String reason =
        "Invalid query. When querying with FieldPath.documentId() you must provide "
            + "a valid document ID, but it was an empty string.";
    expectError(() -> collection.whereIn(FieldPath.documentId(), asList("")), reason);

    reason =
        "Invalid query. When querying a collection by FieldPath.documentId() you must provide "
            + "a plain document ID, but 'foo/bar/baz' contains a '/' character.";
    expectError(() -> collection.whereIn(FieldPath.documentId(), asList("foo/bar/baz")), reason);

    reason =
        "Invalid query. When querying with FieldPath.documentId() you must provide "
            + "a valid String or DocumentReference, but it was of type: java.lang.Integer";
    expectError(() -> collection.whereIn(FieldPath.documentId(), asList(1, 2)), reason);

    reason =
        "Invalid query. When querying a collection group by FieldPath.documentId(), the value "
            + "provided must result in a valid document path, but 'foo' is not because it has "
            + "an odd number of segments (1).";
    expectError(
        () ->
            testFirestore()
                .collectionGroup("collection")
                .whereIn(FieldPath.documentId(), asList("foo")),
        reason);
  }

  // Helpers

  /** Performs a write using each write API and makes sure it succeeds. */
  private static void expectWriteSuccess(Object data) {
    expectWriteSuccess(data, /*includeSets=*/ true, /*includeUpdates=*/ true);
  }

  /**
   * Performs a write using each set and/or update API and makes sure it fails with the expected
   * reason.
   */
  private static void expectWriteSuccess(Object data, boolean includeSets, boolean includeUpdates) {
    DocumentReference ref = testDocument();

    if (includeSets) {
      assertDoesNotThrow(() -> ref.set(data));
      assertDoesNotThrow(() -> ref.getFirestore().batch().set(ref, data));
    }

    if (includeUpdates) {
      assertTrue("update() only support Maps.", data instanceof Map);
      assertDoesNotThrow(
          () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> updateMap = (Map<String, Object>) data;
            ref.update(updateMap);
          });
      assertDoesNotThrow(
          () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> updateMap = (Map<String, Object>) data;
            ref.getFirestore().batch().update(ref, updateMap);
          });
    }

    waitFor(
        ref.getFirestore()
            .runTransaction(
                (Function<Void>)
                    transaction -> {
                      if (includeSets) {
                        assertDoesNotThrow(() -> transaction.set(ref, data));
                      }
                      if (includeUpdates) {
                        assertTrue("update() only support Maps.", data instanceof Map);
                        assertDoesNotThrow(
                            () -> {
                              @SuppressWarnings("unchecked")
                              Map<String, Object> updateMap = (Map<String, Object>) data;
                              transaction.update(ref, updateMap);
                            });
                      }

                      return null;
                    }));
  }

  /** Performs a write using each write API and makes sure it fails with the expected reason. */
  private static void expectWriteError(Object data, String reason) {
    expectWriteError(data, reason, /*includeSets=*/ true, /*includeUpdates=*/ true);
  }

  /** Performs a write using each update API and makes sure it fails with the expected reason. */
  private static void expectUpdateError(Map<String, Object> data, String reason) {
    expectWriteError(data, reason, /*includeSets=*/ false, /*includeUpdates=*/ true);
  }

  /** Performs a write using each set API and makes sure it fails with the expected reason. */
  private static void expectSetError(Object data, String reason) {
    expectWriteError(data, reason, /*includeSets=*/ true, /*includeUpdates=*/ false);
  }

  /**
   * Performs a write using each set and/or update API and makes sure it fails with the expected
   * reason.
   */
  private static void expectWriteError(
      Object data, String reason, boolean includeSets, boolean includeUpdates) {
    DocumentReference ref = testDocument();

    if (includeSets) {
      expectError(() -> ref.set(data), reason);
      expectError(() -> ref.getFirestore().batch().set(ref, data), reason);
    }

    if (includeUpdates) {
      assertTrue("update() only support Maps.", data instanceof Map);
      expectError(
          () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> updateMap = (Map<String, Object>) data;
            ref.update(updateMap);
          },
          reason);
      expectError(
          () -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> updateMap = (Map<String, Object>) data;
            ref.getFirestore().batch().update(ref, updateMap);
          },
          reason);
    }

    waitFor(
        ref.getFirestore()
            .runTransaction(
                (Function<Void>)
                    transaction -> {
                      if (includeSets) {
                        expectError(() -> transaction.set(ref, data), reason);
                      }
                      if (includeUpdates) {
                        assertTrue("update() only support Maps.", data instanceof Map);
                        expectError(
                            () -> {
                              @SuppressWarnings("unchecked")
                              Map<String, Object> updateMap = (Map<String, Object>) data;
                              transaction.update(ref, updateMap);
                            },
                            reason);
                      }

                      return null;
                    }));
  }

  /**
   * Tests a field path with all of our APIs that accept field paths and ensures they fail with the
   * specified reason.
   */
  private static void verifyFieldPathThrows(String path, String reason) {
    // Get an arbitrary snapshot we can use for testing.
    DocumentReference docRef = testDocument();
    waitFor(docRef.set(map("test", 1)));
    DocumentSnapshot snapshot = waitFor(docRef.get());

    // snapshot paths
    expectError(() -> snapshot.get(path), reason);

    // Query filter / order fields
    CollectionReference coll = testCollection();
    // whereLessThan(), etc. omitted for brevity since the code path is trivially shared.
    expectError(() -> coll.whereEqualTo(path, 1), reason);
    expectError(() -> coll.orderBy(path), reason);

    // update() paths.
    expectError(() -> docRef.update(path, 1), reason);
  }
}
