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

package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.model.mutation.Mutation.calculateOverlayMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.fieldMask;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.mergeMutation;
import static com.google.firebase.firestore.testutil.TestUtil.mutationResult;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.unknownDoc;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.annotation.Nullable;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Decimal128Value;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Int32Value;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ServerTimestamps;
import com.google.firestore.v1.Value;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests Mutations */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MutationTest {
  @Test
  public void testAppliesSetsToDocuments() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    MutableDocument setDoc = doc("collection/key", 1, data);

    Mutation set = setMutation("collection/key", map("bar", "bar-value"));
    set.applyToLocalView(setDoc, /* previousMask= */ null, Timestamp.now());
    assertEquals(doc("collection/key", 1, map("bar", "bar-value")).setHasLocalMutations(), setDoc);
  }

  @Test
  public void testAppliesPatchToDocuments() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    MutableDocument patchDoc = doc("collection/key", 1, data);

    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));
    patch.applyToLocalView(patchDoc, /* previousMask= */ null, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("bar", "new-bar-value"), "baz", "baz-value");
    assertEquals(doc("collection/key", 1, expectedData).setHasLocalMutations(), patchDoc);
  }

  @Test
  public void testAppliesPatchWithMergeToDocuments() {
    MutableDocument mergeDoc = deletedDoc("collection/key", 2);
    Mutation upsert =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    upsert.applyToLocalView(mergeDoc, /* previousMask= */ null, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("bar", "new-bar-value"));
    assertEquals(doc("collection/key", 2, expectedData).setHasLocalMutations(), mergeDoc);
  }

  @Test
  public void testAppliesPatchToNullDocWithMergeToDocuments() {
    MutableDocument mergeDoc = MutableDocument.newInvalidDocument(key("collection/key"));
    Mutation upsert =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    upsert.applyToLocalView(mergeDoc, /* previousMask= */ null, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("bar", "new-bar-value"));
    assertEquals(doc("collection/key", 0, expectedData).setHasLocalMutations(), mergeDoc);
  }

  @Test
  public void testDeletesValuesFromTheFieldMask() {
    Map<String, Object> data = map("foo", map("bar", "bar-value", "baz", "baz-value"));
    MutableDocument patchDoc = doc("collection/key", 1, data);

    DocumentKey key = key("collection/key");
    FieldMask mask = fieldMask("foo.bar");
    Mutation patch = new PatchMutation(key, new ObjectValue(), mask, Precondition.NONE);

    patch.applyToLocalView(patchDoc, /* previousMask= */ null, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("baz", "baz-value"));
    assertEquals(doc("collection/key", 1, expectedData).setHasLocalMutations(), patchDoc);
  }

  @Test
  public void testPatchesPrimitiveValue() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    MutableDocument patchDoc = doc("collection/key", 1, data);

    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));
    patch.applyToLocalView(patchDoc, /* previousMask= */ null, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("bar", "new-bar-value"), "baz", "baz-value");
    assertEquals(doc("collection/key", 1, expectedData).setHasLocalMutations(), patchDoc);
  }

  @Test
  public void testPatchingDeletedDocumentsDoesNothing() {
    MutableDocument patchDoc = deletedDoc("collection/key", 1);
    Mutation patch = patchMutation("collection/key", map("foo", "bar"));
    patch.applyToLocalView(patchDoc, /* previousMask= */ null, Timestamp.now());
    assertEquals(deletedDoc("collection/key", 1), patchDoc);
  }

  @Test
  public void testAppliesLocalServerTimestampTransformsToDocuments() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    MutableDocument transformedDoc = doc("collection/key", 1, data);

    Timestamp timestamp = Timestamp.now();
    Mutation transform =
        patchMutation("collection/key", map("foo.bar", FieldValue.serverTimestamp()));
    transform.applyToLocalView(transformedDoc, /* previousMask= */ null, timestamp);

    // Server timestamps aren't parsed, so we manually insert it.
    ObjectValue expectedData =
        wrapObject(map("foo", map("bar", "<server-timestamp>"), "baz", "baz-value"));
    Value fieldValue = ServerTimestamps.valueOf(timestamp, wrap("bar-value"));
    expectedData.set(field("foo.bar"), fieldValue);

    MutableDocument expectedDoc = doc("collection/key", 1, expectedData).setHasLocalMutations();
    assertEquals(expectedDoc, transformedDoc);
  }

  @Test
  public void testAppliesIncrementTransformToDocument() {
    Map<String, Object> baseDoc =
        map(
            "longPlusLong",
            1,
            "longPlusDouble",
            2,
            "doublePlusLong",
            3.3,
            "doublePlusDouble",
            4.0,
            "longPlusNan",
            5,
            "doublePlusNan",
            6.6,
            "longPlusInfinity",
            7,
            "doublePlusInfinity",
            8.8);
    Map<String, Object> transform =
        map(
            "longPlusLong",
            FieldValue.increment(1),
            "longPlusDouble",
            FieldValue.increment(2.2),
            "doublePlusLong",
            FieldValue.increment(3),
            "doublePlusDouble",
            FieldValue.increment(4.4),
            "longPlusNan",
            FieldValue.increment(Double.NaN),
            "doublePlusNan",
            FieldValue.increment(Double.NaN),
            "longPlusInfinity",
            FieldValue.increment(Double.POSITIVE_INFINITY),
            "doublePlusInfinity",
            FieldValue.increment(Double.POSITIVE_INFINITY));
    Map<String, Object> expected =
        map(
            "longPlusLong",
            2L,
            "longPlusDouble",
            4.2D,
            "doublePlusLong",
            6.3D,
            "doublePlusDouble",
            8.4D,
            "longPlusNan",
            Double.NaN,
            "doublePlusNan",
            Double.NaN,
            "longPlusInfinity",
            Double.POSITIVE_INFINITY,
            "doublePlusInfinity",
            Double.POSITIVE_INFINITY);

    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesIncrementTransformToUnexpectedType() {
    Map<String, Object> baseDoc = map("string", "zero");
    Map<String, Object> transform = map("string", FieldValue.increment(1));
    Map<String, Object> expected = map("string", 1);
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesIncrementTransformToMissingField() {
    Map<String, Object> baseDoc = map();
    Map<String, Object> transform = map("missing", FieldValue.increment(1));
    Map<String, Object> expected = map("missing", 1);
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesMinimumTransformToDocument() {
    Map<String, Object> baseDoc =
        map(
            "longMinLong",
            5,
            "longMinDouble",
            5,
            "doubleMinLong",
            5.5,
            "doubleMinDouble",
            5.5,
            "longMinNan",
            5,
            "doubleMinNan",
            5.5);
    Map<String, Object> transform =
        map(
            "longMinLong",
            FieldValue.minimum(2),
            "longMinDouble",
            FieldValue.minimum(2.2),
            "doubleMinLong",
            FieldValue.minimum(2),
            "doubleMinDouble",
            FieldValue.minimum(2.2),
            "longMinNan",
            FieldValue.minimum(Double.NaN),
            "doubleMinNan",
            FieldValue.minimum(Double.NaN));
    Map<String, Object> expected =
        map(
            "longMinLong",
            2L,
            "longMinDouble",
            2.2D,
            "doubleMinLong",
            2.0D,
            "doubleMinDouble",
            2.2D,
            "longMinNan",
            Double.NaN,
            "doubleMinNan",
            Double.NaN);

    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesMaximumTransformToDocument() {
    Map<String, Object> baseDoc =
        map(
            "longMaxLong",
            5,
            "longMaxDouble",
            5,
            "doubleMaxLong",
            5.5,
            "doubleMaxDouble",
            5.5,
            "longMaxNan",
            5,
            "doubleMaxNan",
            5.5);
    Map<String, Object> transform =
        map(
            "longMaxLong",
            FieldValue.maximum(8),
            "longMaxDouble",
            FieldValue.maximum(8.8),
            "doubleMaxLong",
            FieldValue.maximum(8),
            "doubleMaxDouble",
            FieldValue.maximum(8.8),
            "longMaxNan",
            FieldValue.maximum(Double.NaN),
            "doubleMaxNan",
            FieldValue.maximum(Double.NaN));
    Map<String, Object> expected =
        map(
            "longMaxLong",
            8L,
            "longMaxDouble",
            8.8D,
            "doubleMaxLong",
            8.0D,
            "doubleMaxDouble",
            8.8D,
            "longMaxNan",
            Double.NaN,
            "doubleMaxNan",
            Double.NaN);

    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesMinimumTransformToUnexpectedType() {
    Map<String, Object> baseDoc = map("string", "value");
    Map<String, Object> transform = map("string", FieldValue.minimum(1));
    Map<String, Object> expected = map("string", 1);
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesMaximumTransformToUnexpectedType() {
    Map<String, Object> baseDoc = map("string", "value");
    Map<String, Object> transform = map("string", FieldValue.maximum(1));
    Map<String, Object> expected = map("string", 1);
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesMinimumTransformToMissingField() {
    Map<String, Object> baseDoc = map();
    Map<String, Object> transform = map("missing", FieldValue.minimum(1));
    Map<String, Object> expected = map("missing", 1);
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesMaximumTransformToMissingField() {
    Map<String, Object> baseDoc = map();
    Map<String, Object> transform = map("missing", FieldValue.maximum(1));
    Map<String, Object> expected = map("missing", 1);
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesIncrementTransformsConsecutively() {
    Map<String, Object> baseDoc = map("number", 1);
    Map<String, Object> transform1 = map("number", FieldValue.increment(2));
    Map<String, Object> transform2 = map("number", FieldValue.increment(3));
    Map<String, Object> transform3 = map("number", FieldValue.increment(4));
    Map<String, Object> expected = map("number", 10);
    verifyTransform(baseDoc, Arrays.asList(transform1, transform2, transform3), expected);
  }

  @Test
  public void testAppliesIncrementWithoutOverflow() {
    Map<String, Object> baseDoc =
        map(
            "a",
            Long.MAX_VALUE - 1,
            "b",
            Long.MAX_VALUE - 1,
            "c",
            Long.MAX_VALUE,
            "d",
            Long.MAX_VALUE);
    Map<String, Object> transform =
        map(
            "a", FieldValue.increment(1),
            "b", FieldValue.increment(Long.MAX_VALUE),
            "c", FieldValue.increment(1),
            "d", FieldValue.increment(Long.MAX_VALUE));
    Map<String, Object> expected =
        map("a", Long.MAX_VALUE, "b", Long.MAX_VALUE, "c", Long.MAX_VALUE, "d", Long.MAX_VALUE);
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesIncrementWithoutUnderflow() {
    Map<String, Object> baseDoc =
        map(
            "a",
            Long.MIN_VALUE + 1,
            "b",
            Long.MIN_VALUE + 1,
            "c",
            Long.MIN_VALUE,
            "d",
            Long.MIN_VALUE);
    Map<String, Object> transform =
        map(
            "a", FieldValue.increment(-1),
            "b", FieldValue.increment(Long.MIN_VALUE),
            "c", FieldValue.increment(-1),
            "d", FieldValue.increment(Long.MIN_VALUE));
    Map<String, Object> expected =
        map("a", Long.MIN_VALUE, "b", Long.MIN_VALUE, "c", Long.MIN_VALUE, "d", Long.MIN_VALUE);
    verifyTransform(baseDoc, transform, expected);
  }

  // NOTE: This is more a test of UserDataReader code than Mutation code but we don't have unit
  // tests for it currently. We could consider removing this test once we have integration tests.
  @Test
  public void testCreateArrayUnionTransform() {
    PatchMutation transform =
        patchMutation(
            "collection/key",
            map(
                "a",
                FieldValue.arrayUnion("tag"),
                "bar.baz",
                FieldValue.arrayUnion(true, map("nested", map("a", Arrays.asList(1, 2))))));
    assertEquals(2, transform.getFieldTransforms().size());

    FieldTransform first = transform.getFieldTransforms().get(0);
    assertEquals(field("a"), first.getFieldPath());
    assertEquals(
        new ArrayTransformOperation.Union(Collections.singletonList(wrap("tag"))),
        first.getOperation());

    FieldTransform second = transform.getFieldTransforms().get(1);
    assertEquals(field("bar.baz"), second.getFieldPath());
    assertEquals(
        new ArrayTransformOperation.Union(
            Arrays.asList(wrap(true), wrap(map("nested", map("a", Arrays.asList(1, 2)))))),
        second.getOperation());
  }

  // NOTE: This is more a test of UserDataReader code than Mutation code but
  // we don't have unit tests for it currently. We could consider removing this
  // test once we have integration tests.
  @Test
  public void testCreateArrayRemoveTransform() {
    PatchMutation transform =
        patchMutation("collection/key", map("foo", FieldValue.arrayRemove("tag")));
    assertEquals(1, transform.getFieldTransforms().size());

    FieldTransform first = transform.getFieldTransforms().get(0);
    assertEquals(field("foo"), first.getFieldPath());
    assertEquals(
        new ArrayTransformOperation.Remove(Collections.singletonList(wrap("tag"))),
        first.getOperation());
  }

  @Test
  public void testAppliesLocalArrayUnionTransformToMissingField() {
    Map<String, Object> baseDoc = map();
    Map<String, Object> transform = map("missing", FieldValue.arrayUnion(1, 2));
    Map<String, Object> expected = map("missing", Arrays.asList(1, 2));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformToNonArrayField() {
    Map<String, Object> baseDoc = map("nonArray", 42);
    Map<String, Object> transform = map("nonArray", FieldValue.arrayUnion(1, 2));
    Map<String, Object> expected = map("nonArray", Arrays.asList(1, 2));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithNonExistingElements() {
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayUnion(2, 4));
    Map<String, Object> expected = map("array", Arrays.asList(1, 3, 2, 4));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithExistingElements() {
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayUnion(1, 3));
    Map<String, Object> expected = map("array", Arrays.asList(1, 3));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithDuplicateExistingElements() {
    // Duplicate entries in your existing array should be preserved.
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 2, 2, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayUnion(2));
    Map<String, Object> expected = map("array", Arrays.asList(1, 2, 2, 3));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithDuplicateUnionElements() {
    // Duplicate entries in your union array should only be added once.
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayUnion(2, 2));
    Map<String, Object> expected = map("array", Arrays.asList(1, 3, 2));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithNonPrimitiveElements() {
    // Union nested object values (one existing, one not).
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, map("a", "b")));
    Map<String, Object> transform =
        map("array", FieldValue.arrayUnion(map("a", "b"), map("c", "d")));
    Map<String, Object> expected = map("array", Arrays.asList(1, map("a", "b"), map("c", "d")));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithPartiallyOverlappingElements() {
    // Union objects that partially overlap an existing object.
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, map("a", "b", "c", "d")));
    Map<String, Object> transform =
        map("array", FieldValue.arrayUnion(map("a", "b"), map("c", "d")));
    Map<String, Object> expected =
        map("array", Arrays.asList(1, map("a", "b", "c", "d"), map("a", "b"), map("c", "d")));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformToMissingField() {
    Map<String, Object> baseDoc = map();
    Map<String, Object> transform = map("missing", FieldValue.arrayRemove(1, 2));
    Map<String, Object> expected = map("missing", Collections.emptyList());
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformToNonArrayField() {
    Map<String, Object> baseDoc = map("nonArray", 42);
    Map<String, Object> transform = map("nonArray", FieldValue.arrayRemove(1, 2));
    Map<String, Object> expected = map("nonArray", Collections.emptyList());
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformWithNonExistingElements() {
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayRemove(2, 4));
    Map<String, Object> expected = map("array", Arrays.asList(1, 3));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformWithExistingElements() {
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 2, 3, 4));
    Map<String, Object> transform = map("array", FieldValue.arrayRemove(1, 3));
    Map<String, Object> expected = map("array", Arrays.asList(2, 4));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformWithNonPrimitiveElements() {
    // Remove nested object values (one existing, one not).
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, map("a", "b")));
    Map<String, Object> transform =
        map("array", FieldValue.arrayRemove(map("a", "b"), map("c", "d")));
    Map<String, Object> expected = map("array", Arrays.asList(1));
    verifyTransform(baseDoc, transform, expected);
  }

  private void verifyTransform(
      Map<String, Object> baseData,
      List<Map<String, Object>> transforms,
      Map<String, Object> expectedData) {
    MutableDocument transformedDoc = doc("collection/key", 1, baseData);

    for (Map<String, Object> transformData : transforms) {
      PatchMutation transform = patchMutation("collection/key", transformData);
      transform.applyToLocalView(transformedDoc, /* previousMask= */ null, Timestamp.now());
    }

    MutableDocument expectedDoc = doc("collection/key", 1, expectedData).setHasLocalMutations();
    assertEquals(expectedDoc, transformedDoc);
  }

  private void verifyTransform(
      Map<String, Object> baseData,
      Map<String, Object> transformData,
      Map<String, Object> expectedData) {
    verifyTransform(baseData, Collections.singletonList(transformData), expectedData);
  }

  private void verifyDocTransform(
      @Nullable Object baseVal, FieldValue transformOp, Object expectedVal) {
    if (baseVal == null) {
      verifyTransform(map(), map("val", transformOp), map("val", expectedVal));
    } else {
      verifyTransform(map("val", baseVal), map("val", transformOp), map("val", expectedVal));
    }
  }

  private void verifyModelTransform(
      TransformOperation op, @Nullable Object baseVal, Object expectedVal) {
    Value baseProto = baseVal != null ? wrap(baseVal) : null;
    Value expectedProto = wrap(expectedVal);
    Value result = op.applyToLocalView(baseProto, Timestamp.now());
    assertEquals(expectedProto, result);
  }

  // Model-level increment evaluation
  @Test
  public void testModelIncrementInt32AndInt32() {
    verifyModelTransform(
        new NumericIncrementTransformOperation(wrap(new Int32Value(5))),
        new Int32Value(10),
        new Int32Value(15));
  }

  @Test
  public void testModelIncrementIntAndInt32() {
    verifyModelTransform(new NumericIncrementTransformOperation(wrap(new Int32Value(5))), 10L, 15L);
  }

  @Test
  public void testModelIncrementDecimal128AndInt() {
    verifyModelTransform(
        new NumericIncrementTransformOperation(wrap(5L)),
        new Decimal128Value("10"),
        new Decimal128Value("15"));
  }

  @Test
  public void testModelIncrementIntAndDecimal128() {
    verifyModelTransform(
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("5"))),
        10L,
        new Decimal128Value("15"));
  }

  @Test
  public void testModelIncrementInt32AndDecimal128() {
    verifyModelTransform(
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("5"))),
        new Int32Value(10),
        new Decimal128Value("15"));
  }

  @Test
  public void testModelIncrementDoubleAndInt32() {
    verifyModelTransform(
        new NumericIncrementTransformOperation(wrap(new Int32Value(5))), 10.5D, 15.5D);
  }

  @Test
  public void testModelIncrementDecimal128AndDecimal128() {
    verifyModelTransform(
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("5"))),
        new Decimal128Value("10"),
        new Decimal128Value("15"));
  }

  @Test
  public void testModelIncrementDoubleAndDecimal128() {
    verifyModelTransform(
        new NumericIncrementTransformOperation(wrap(new Decimal128Value("5"))),
        10.5D,
        new Decimal128Value("15.5"));
  }

  // Standalone document-level increment tests
  @Test
  public void testAppliesIncrementWithIntegerToInt32Base() {
    verifyTransform(
        map("val", new Int32Value(10)), map("val", FieldValue.increment(5)), map("val", 15L));
  }

  @Test
  public void testAppliesIncrementWithDoubleToInt32Base() {
    verifyTransform(
        map("val", new Int32Value(10)), map("val", FieldValue.increment(1.5)), map("val", 11.5D));
  }

  @Test
  public void testAppliesIncrementWithIntegerToDecimal128Base() {
    verifyTransform(
        map("val", new Decimal128Value("10.5")),
        map("val", FieldValue.increment(5)),
        map("val", new Decimal128Value("15.5")));
  }

  // Model-level minimum evaluation
  @Test
  public void testModelMinimumInt32AndInt32_Smaller() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Int32Value(5))),
        new Int32Value(10),
        new Int32Value(5));
  }

  @Test
  public void testModelMinimumInt32AndInt32_Larger() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Int32Value(15))),
        new Int32Value(10),
        new Int32Value(10));
  }

  @Test
  public void testModelMinimumIntAndInt32() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Int32Value(5))), 10L, new Int32Value(5));
  }

  @Test
  public void testModelMinimumInt32AndInt() {
    verifyModelTransform(new NumericMinimumTransformOperation(wrap(5L)), new Int32Value(10), 5L);
  }

  @Test
  public void testModelMinimumDecimal128AndInt() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(5L)), new Decimal128Value("10"), 5L);
  }

  @Test
  public void testModelMinimumIntAndDecimal128() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Decimal128Value("5"))),
        10L,
        new Decimal128Value("5"));
  }

  @Test
  public void testModelMinimumInt32AndDecimal128() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Decimal128Value("5"))),
        new Int32Value(10),
        new Decimal128Value("5"));
  }

  @Test
  public void testModelMinimumDoubleAndInt32() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Int32Value(5))), 10.5D, new Int32Value(5));
  }

  @Test
  public void testModelMinimumDecimal128AndDecimal128() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Decimal128Value("5"))),
        new Decimal128Value("10"),
        new Decimal128Value("5"));
  }

  @Test
  public void testModelMinimumDoubleAndDecimal128() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Decimal128Value("5"))),
        10.5D,
        new Decimal128Value("5"));
  }

  @Test
  public void testModelMinimumNonNumericBaseSelectsOperand() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Int32Value(5))), "hello", new Int32Value(5));
  }

  @Test
  public void testModelMinimumNullBaseSelectsOperand() {
    verifyModelTransform(
        new NumericMinimumTransformOperation(wrap(new Int32Value(5))), null, new Int32Value(5));
  }

  // Model-level maximum evaluation
  @Test
  public void testModelMaximumInt32AndInt32_Smaller() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Int32Value(5))),
        new Int32Value(10),
        new Int32Value(10));
  }

  @Test
  public void testModelMaximumInt32AndInt32_Larger() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Int32Value(15))),
        new Int32Value(10),
        new Int32Value(15));
  }

  @Test
  public void testModelMaximumIntAndInt32() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Int32Value(15))), 10L, new Int32Value(15));
  }

  @Test
  public void testModelMaximumInt32AndInt() {
    verifyModelTransform(new NumericMaximumTransformOperation(wrap(15L)), new Int32Value(10), 15L);
  }

  @Test
  public void testModelMaximumDecimal128AndInt() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(15L)), new Decimal128Value("10"), 15L);
  }

  @Test
  public void testModelMaximumIntAndDecimal128() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("15"))),
        10L,
        new Decimal128Value("15"));
  }

  @Test
  public void testModelMaximumInt32AndDecimal128() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("15"))),
        new Int32Value(10),
        new Decimal128Value("15"));
  }

  @Test
  public void testModelMaximumDoubleAndInt32() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Int32Value(15))), 10.5D, new Int32Value(15));
  }

  @Test
  public void testModelMaximumDecimal128AndDecimal128() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("15"))),
        new Decimal128Value("10"),
        new Decimal128Value("15"));
  }

  @Test
  public void testModelMaximumDoubleAndDecimal128() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("15"))),
        10.5D,
        new Decimal128Value("15"));
  }

  @Test
  public void testModelMaximumNonNumericBaseSelectsOperand() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("10.5"))),
        "hello",
        new Decimal128Value("10.5"));
  }

  @Test
  public void testModelMaximumNullBaseSelectsOperand() {
    verifyModelTransform(
        new NumericMaximumTransformOperation(wrap(new Decimal128Value("10.5"))),
        null,
        new Decimal128Value("10.5"));
  }

  // Document-level numeric transform evaluation
  @Test
  public void testAppliesNumericTransformsToInt32Fields() {
    Int32Value base = new Int32Value(10);
    verifyDocTransform(base, FieldValue.increment(5), 15L);
    verifyDocTransform(base, FieldValue.increment(1.5), 11.5D);
    verifyDocTransform(base, FieldValue.minimum(5), 5L);
    verifyDocTransform(base, FieldValue.minimum(15), new Int32Value(10));
    verifyDocTransform(base, FieldValue.minimum(5.5), 5.5D);
    verifyDocTransform(base, FieldValue.minimum(-5), -5L);
    verifyDocTransform(base, FieldValue.maximum(5), new Int32Value(10));
    verifyDocTransform(base, FieldValue.maximum(15), 15L);
    verifyDocTransform(base, FieldValue.maximum(5.5), new Int32Value(10));
    verifyDocTransform(base, FieldValue.maximum(-5), new Int32Value(10));
  }

  @Test
  public void testAppliesNumericTransformsToDecimal128Fields() {
    Decimal128Value base = new Decimal128Value("10.5");
    verifyDocTransform(base, FieldValue.increment(5), new Decimal128Value("15.5"));
    verifyDocTransform(base, FieldValue.minimum(5), 5L);
    verifyDocTransform(base, FieldValue.minimum(15), new Decimal128Value("10.5"));
    verifyDocTransform(base, FieldValue.minimum(5.5), 5.5D);
    verifyDocTransform(base, FieldValue.minimum(-5), -5L);
    verifyDocTransform(base, FieldValue.maximum(5), new Decimal128Value("10.5"));
    verifyDocTransform(base, FieldValue.maximum(15), 15L);
    verifyDocTransform(base, FieldValue.maximum(5.5), new Decimal128Value("10.5"));
    verifyDocTransform(base, FieldValue.maximum(-5), new Decimal128Value("10.5"));
  }

  @Test
  public void testAppliesNumericTransformsToStandardIntegerAndDoubleFields() {
    long intBase = 10L;
    verifyDocTransform(intBase, FieldValue.minimum(5), 5L);
    verifyDocTransform(intBase, FieldValue.minimum(5.5), 5.5D);
    verifyDocTransform(intBase, FieldValue.maximum(15), 15L);
    verifyDocTransform(intBase, FieldValue.maximum(15.5), 15.5D);

    double doubleBase = 10.5D;
    verifyDocTransform(doubleBase, FieldValue.minimum(5), 5.0D);
    verifyDocTransform(doubleBase, FieldValue.minimum(5.5), 5.5D);
    verifyDocTransform(doubleBase, FieldValue.maximum(15), 15.0D);
    verifyDocTransform(doubleBase, FieldValue.maximum(15.5), 15.5D);
  }

  @Test
  public void testAppliesNumericTransformsToMissingAndNonNumericFields() {
    verifyDocTransform(null, FieldValue.increment(1), 1L);
    verifyDocTransform(null, FieldValue.minimum(10), 10L);
    verifyDocTransform(null, FieldValue.maximum(10.5), 10.5D);

    String stringBase = "hello";
    verifyDocTransform(stringBase, FieldValue.minimum(5), 5L);
    verifyDocTransform(stringBase, FieldValue.maximum(15), 15L);
  }

  @Test
  public void testAppliesConsecutiveNumericTransformsOnDocument() {
    Map<String, Object> baseDoc = map("val", new Int32Value(10));
    Map<String, Object> t1 = map("val", FieldValue.minimum(15));
    Map<String, Object> t2 = map("val", FieldValue.minimum(5));
    Map<String, Object> t3 = map("val", FieldValue.maximum(20));
    verifyTransform(baseDoc, Arrays.asList(t1, t2, t3), map("val", 20L));

    Map<String, Object> numDoc = map("numberVal", 1);
    verifyTransform(
        numDoc,
        Arrays.asList(
            map("numberVal", FieldValue.increment(2)),
            map("numberVal", FieldValue.increment(3)),
            map("numberVal", FieldValue.increment(4))),
        map("numberVal", 10L));
  }

  @Test
  public void testAppliesServerAckedIncrementTransformToDocuments() {
    Map<String, Object> data = map("sum", 1);
    MutableDocument transformedDoc = doc("collection/key", 1, data);

    Mutation transform = setMutation("collection/key", map("sum", FieldValue.increment(2)));
    MutationResult mutationResult =
        new MutationResult(version(1), Collections.singletonList(wrap(3L)));

    transform.applyToRemoteDocument(transformedDoc, mutationResult);

    Map<String, Object> expectedData = map("sum", 3L);
    assertEquals(doc("collection/key", 1, expectedData).setHasCommittedMutations(), transformedDoc);
  }

  @Test
  public void testAppliesServerAckedServerTimestampTransformsToDocuments() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    MutableDocument transformedDoc = doc("collection/key", 1, data);

    Mutation transform =
        patchMutation("collection/key", map("foo.bar", FieldValue.serverTimestamp()));

    Timestamp serverTimestamp = new Timestamp(2, 0);

    MutationResult mutationResult =
        new MutationResult(version(1), Collections.singletonList(wrap(serverTimestamp)));

    transform.applyToRemoteDocument(transformedDoc, mutationResult);

    Map<String, Object> expectedData =
        map("foo", map("bar", serverTimestamp.toDate()), "baz", "baz-value");
    assertEquals(doc("collection/key", 1, expectedData).setHasCommittedMutations(), transformedDoc);
  }

  @Test
  public void testAppliesServerAckedArrayTransformsToDocuments() {
    Map<String, Object> data =
        map("array1", Arrays.asList(1, 2), "array2", Arrays.asList("a", "b"));
    MutableDocument transformedDoc = doc("collection/key", 1, data);
    Mutation transform =
        setMutation(
            "collection/key",
            map("array1", FieldValue.arrayUnion(2, 3), "array2", FieldValue.arrayRemove("a", "c")));

    // Server just sends null transform results for array operations.
    MutationResult mutationResult =
        new MutationResult(version(1), Arrays.asList(wrap(null), wrap(null)));
    transform.applyToRemoteDocument(transformedDoc, mutationResult);

    Map<String, Object> expectedData =
        map("array1", Arrays.asList(1, 2, 3), "array2", Arrays.asList("b"));
    assertEquals(doc("collection/key", 1, expectedData).setHasCommittedMutations(), transformedDoc);
  }

  @Test
  public void testDeleteDeletes() {
    Map<String, Object> data = map("foo", "bar");
    MutableDocument deletedDoc = doc("collection/key", 1, data);

    Mutation delete = deleteMutation("collection/key");
    delete.applyToLocalView(deletedDoc, /* previousMask= */ null, Timestamp.now());
    assertEquals(deletedDoc("collection/key", 1).setHasLocalMutations(), deletedDoc);
  }

  @Test
  public void testSetWithMutationResult() {
    Map<String, Object> data = map("foo", "bar");
    MutableDocument setDoc = doc("collection/key", 1, data);

    Mutation set = setMutation("collection/key", map("foo", "new-bar"));
    set.applyToRemoteDocument(setDoc, mutationResult(4));

    assertEquals(
        doc("collection/key", 4, map("foo", "new-bar")).setHasCommittedMutations(), setDoc);
  }

  @Test
  public void testPatchWithMutationResult() {
    Map<String, Object> data = map("foo", "bar");
    MutableDocument patchDoc = doc("collection/key", 1, data);

    Mutation patch = patchMutation("collection/key", map("foo", "new-bar"));
    patch.applyToRemoteDocument(patchDoc, mutationResult(4));

    assertEquals(
        doc("collection/key", 4, map("foo", "new-bar")).setHasCommittedMutations(), patchDoc);
  }

  private void assertVersionTransitions(
      Mutation mutation,
      MutableDocument base,
      MutationResult mutationResult,
      MutableDocument expected) {
    MutableDocument clone = base.mutableCopy();
    mutation.applyToRemoteDocument(clone, mutationResult);
    assertEquals(expected, clone);
  }

  @Test
  public void testTransitions() {
    MutableDocument docV3 = doc("collection/key", 3, map());
    MutableDocument deletedV3 = deletedDoc("collection/key", 3);

    Mutation set = setMutation("collection/key", map());
    Mutation patch = patchMutation("collection/key", map());
    Mutation delete = deleteMutation("collection/key");

    MutableDocument docV7Deleted = deletedDoc("collection/key", 7).setHasCommittedMutations();
    MutableDocument docV7Committed = doc("collection/key", 7, map()).setHasCommittedMutations();
    MutableDocument docV7Unknown = unknownDoc("collection/key", 7);

    MutationResult mutationResult =
        new MutationResult(version(7), /* transformResults= */ Collections.emptyList());

    assertVersionTransitions(set, docV3, mutationResult, docV7Committed);
    assertVersionTransitions(set, deletedV3, mutationResult, docV7Committed);
    assertVersionTransitions(
        set,
        MutableDocument.newInvalidDocument(key("collection/key")),
        mutationResult,
        docV7Committed);

    assertVersionTransitions(patch, docV3, mutationResult, docV7Committed);
    assertVersionTransitions(patch, deletedV3, mutationResult, docV7Unknown);
    assertVersionTransitions(
        patch,
        MutableDocument.newInvalidDocument(key("collection/key")),
        mutationResult,
        docV7Unknown);

    assertVersionTransitions(delete, docV3, mutationResult, docV7Deleted);
    assertVersionTransitions(delete, deletedV3, mutationResult, docV7Deleted);
    assertVersionTransitions(
        delete,
        MutableDocument.newInvalidDocument(key("collection/key")),
        mutationResult,
        docV7Deleted);
  }

  @Test
  public void testNonTransformMutationBaseValue() {
    Map<String, Object> data = map("foo", "foo");
    MutableDocument baseDoc = doc("collection/key", 1, data);

    Mutation set = setMutation("collection/key", map("foo", "bar"));
    assertNull(set.extractTransformBaseValue(baseDoc));

    Mutation patch = patchMutation("collection/key", map("foo", "bar"));
    assertNull(patch.extractTransformBaseValue(baseDoc));

    Mutation delete = deleteMutation("collection/key");
    assertNull(delete.extractTransformBaseValue(baseDoc));
  }

  @Test
  public void testServerTimestampBaseValue() {
    Map<String, Object> allValues = map("time", "foo");
    allValues.put("nested", new HashMap<>(allValues));
    MutableDocument baseDoc = doc("collection/key", 1, allValues);

    Map<String, Object> allTransforms = map("time", FieldValue.serverTimestamp());
    allTransforms.put("nested", new HashMap<>(allTransforms));

    // Server timestamps are idempotent and don't have base values.
    Mutation mutation = patchMutation("collection/key", allTransforms);
    assertNull(mutation.extractTransformBaseValue(baseDoc));
  }

  @Test
  public void testNumericIncrementBaseValue() {
    Map<String, Object> allValues =
        map("ignore", "foo", "double", 42.0, "long", 42, "string", "foo", "map", map());
    allValues.put("nested", new HashMap<>(allValues));
    MutableDocument baseDoc = doc("collection/key", 1, allValues);

    Map<String, Object> allTransforms =
        map(
            "double",
            FieldValue.increment(1),
            "long",
            FieldValue.increment(1),
            "string",
            FieldValue.increment(1),
            "map",
            FieldValue.increment(1),
            "missing",
            FieldValue.increment(1));
    allTransforms.put("nested", new HashMap<>(allTransforms));

    Mutation mutation = patchMutation("collection/key", allTransforms);
    ObjectValue baseValue = mutation.extractTransformBaseValue(baseDoc);

    Value expected =
        wrap(
            map(
                "double",
                42.0,
                "long",
                42,
                "string",
                0,
                "map",
                0,
                "missing",
                0,
                "nested",
                map("double", 42.0, "long", 42, "string", 0, "map", 0, "missing", 0)));
    assertEquals(expected, baseValue.get(FieldPath.EMPTY_PATH));
  }

  @Test
  public void testNumericMinimumBaseValue() {
    Map<String, Object> allValues =
        map("ignore", "foo", "double", 42.0, "long", 42, "string", "foo", "map", map());
    allValues.put("nested", new HashMap<>(allValues));
    MutableDocument baseDoc = doc("collection/key", 1, allValues);

    Map<String, Object> allTransforms =
        map(
            "double",
            FieldValue.minimum(1),
            "long",
            FieldValue.minimum(1),
            "string",
            FieldValue.minimum(1),
            "map",
            FieldValue.minimum(1),
            "missing",
            FieldValue.minimum(1));
    allTransforms.put("nested", new HashMap<>(allTransforms));

    Mutation mutation = patchMutation("collection/key", allTransforms);
    assertNull(mutation.extractTransformBaseValue(baseDoc));
  }

  @Test
  public void testNumericMaximumBaseValue() {
    Map<String, Object> allValues =
        map("ignore", "foo", "double", 42.0, "long", 42, "string", "foo", "map", map());
    allValues.put("nested", new HashMap<>(allValues));
    MutableDocument baseDoc = doc("collection/key", 1, allValues);

    Map<String, Object> allTransforms =
        map(
            "double",
            FieldValue.maximum(1),
            "long",
            FieldValue.maximum(1),
            "string",
            FieldValue.maximum(1),
            "map",
            FieldValue.maximum(1),
            "missing",
            FieldValue.maximum(1));
    allTransforms.put("nested", new HashMap<>(allTransforms));

    Mutation mutation = patchMutation("collection/key", allTransforms);
    assertNull(mutation.extractTransformBaseValue(baseDoc));
  }

  @Test
  public void testIncrementTwice() {
    MutableDocument patchDoc = doc("collection/key", 1, map("sum", "0"));

    Map<String, Object> increment = map("sum", FieldValue.increment(1));
    Mutation mutation = patchMutation("collection/key", increment);

    mutation.applyToLocalView(patchDoc, /* previousMask= */ null, Timestamp.now());
    mutation.applyToLocalView(patchDoc, /* previousMask= */ null, Timestamp.now());

    assertEquals(wrap(2L), patchDoc.getField(field("sum")));
  }

  // Mutation Overlay tests

  @Test
  public void testOverlayWithNoMutation() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    verifyOverlayRoundTrips(doc("collection/key", 1, data));
  }

  @Test
  public void testOverlayWithMutationsFailByPreconditions() {
    verifyOverlayRoundTrips(
        deletedDoc("collection/key", 1),
        patchMutation("collection/key", map("foo", "bar")),
        patchMutation("collection/key", map("a", 1)));
  }

  @Test
  public void testOverlayWithPatchOnInvalidDocument() {
    verifyOverlayRoundTrips(
        MutableDocument.newInvalidDocument(key("collection/key")),
        patchMutation("collection/key", map("a", 1)));
  }

  @Test
  public void testOverlayWithOneSetMutation() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    verifyOverlayRoundTrips(
        doc("collection/key", 1, data), setMutation("collection/key", map("bar", "bar-value")));
  }

  @Test
  public void testOverlayWithOnePatchMutation() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    verifyOverlayRoundTrips(
        doc("collection/key", 1, data),
        patchMutation("collection/key", map("foo.bar", "new-bar-value")));
  }

  @Test
  public void testOverlayWithPatchThenMerge() {
    Mutation upsert =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    verifyOverlayRoundTrips(deletedDoc("collection/key", 1), upsert);
  }

  @Test
  public void testOverlayWithDeleteThenPatch() {
    MutableDocument doc = doc("collection/key", 1, map("foo", 1));
    Mutation delete = new DeleteMutation(key("collection/key"), Precondition.NONE);
    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));

    verifyOverlayRoundTrips(doc, delete, patch);
  }

  @Test
  public void testOverlayWithDeleteThenMerge() {
    MutableDocument doc = doc("collection/key", 1, map("foo", 1));
    Mutation delete = new DeleteMutation(key("collection/key"), Precondition.NONE);
    Mutation patch =
        mergeMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));

    verifyOverlayRoundTrips(doc, delete, patch);
  }

  @Test
  public void testOverlayWithPatchThenPatchToDeleteField() {
    MutableDocument doc = doc("collection/key", 1, map("foo", 1));
    Mutation patch =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1)));
    Mutation patchToDeleteField =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.delete()));

    verifyOverlayRoundTrips(doc, patch, patchToDeleteField);
  }

  @Test
  public void testOverlayWithPatchThenMergeWithArrayUnion() {
    MutableDocument doc = doc("collection/key", 1, map("foo", 1));
    Mutation patch =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1)));
    Mutation merge =
        mergeMutation(
            "collection/key",
            map("arrays", FieldValue.arrayUnion(1, 2, 3)),
            Arrays.asList(field("arrays")));

    verifyOverlayRoundTrips(doc, patch, merge);
  }

  @Test
  public void testOverlayWithArrayUnionThenRemove() {
    MutableDocument doc = doc("collection/key", 1, map("foo", 1));
    Mutation union =
        mergeMutation(
            "collection/key", map("arrays", FieldValue.arrayUnion(1, 2, 3)), Arrays.asList());
    Mutation remove =
        mergeMutation(
            "collection/key",
            map("foo", "xxx", "arrays", FieldValue.arrayRemove(2)),
            Arrays.asList(field("foo")));

    verifyOverlayRoundTrips(doc, union, remove);
  }

  @Test
  public void testOverlayWithSetThenIncrement() {
    MutableDocument doc = doc("collection/key", 1, map("foo", 1));
    Mutation set = setMutation("collection/key", map("foo", 2));
    Mutation update = patchMutation("collection/key", map("foo", FieldValue.increment(2)));

    verifyOverlayRoundTrips(doc, set, update);
  }

  @Test
  public void testOverlayWithSetThenPatchOnDeletedDoc() {
    MutableDocument doc = deletedDoc("collection/key", 1);
    Mutation set = setMutation("collection/key", map("bar", "bar-value"));
    Mutation patch =
        patchMutation(
            "collection/key",
            map("foo", "foo-patched-value", "bar.baz", FieldValue.serverTimestamp()));

    verifyOverlayRoundTrips(doc, set, patch);
  }

  @Test
  public void testOverlayWithFieldDeletionOfNestedField() {
    MutableDocument doc = doc("collection/key", 1, map("foo", 1));
    Mutation patch1 =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.increment(1)));
    Mutation patch2 =
        patchMutation(
            "collection/key",
            map("foo", "foo-patched-value", "bar.baz", FieldValue.serverTimestamp()));
    Mutation patch3 =
        patchMutation(
            "collection/key", map("foo", "foo-patched-value", "bar.baz", FieldValue.delete()));

    verifyOverlayRoundTrips(doc, patch1, patch2, patch3);
  }

  @Test
  public void testOverlayCreatedFromSetToEmptyWithMerge() {
    MutableDocument doc = deletedDoc("collection/key", 1);
    Mutation merge = mergeMutation("collection/key", map(), Arrays.asList());
    verifyOverlayRoundTrips(doc, merge);

    doc = doc("collection/key", 1, map("foo", "foo-value"));
    verifyOverlayRoundTrips(doc, merge);
  }

  // Below tests run on automatically generated mutation list, they are deterministic, but hard to
  // debug when they fail. They will print the failure case, and the best way to debug is recreate
  // the case manually in a separate test.

  @Test
  public void testOverlayWithMutationWithMultipleDeletes() {
    List<MutableDocument> docs =
        Lists.newArrayList(
            doc("collection/key", 1, map("foo", "foo-value", "bar.baz", 1)),
            deletedDoc("collection/key", 1),
            unknownDoc("collection/key", 1));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value", "bar.baz", FieldValue.serverTimestamp())));

    int testCases = runPermutationTests(docs, Lists.newArrayList(mutations));

    // There are 4! * 3 cases
    assertEquals(72, testCases);
  }

  @Test
  public void testOverlayByCombinationsAndPermutations() {
    List<MutableDocument> docs =
        Lists.newArrayList(
            doc("collection/key", 1, map("foo", "foo-value", "bar", 1)),
            deletedDoc("collection/key", 1),
            unknownDoc("collection/key", 1));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            setMutation("collection/key", map("bar.rab", "bar.rab-value")),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-incr", "bar", FieldValue.increment(1))),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-delete", "bar", FieldValue.delete())),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-st", "bar", FieldValue.serverTimestamp())),
            mergeMutation(
                "collection/key",
                map("arrays", FieldValue.arrayUnion(1, 2, 3)),
                Arrays.asList(field("arrays"))));

    // Take all possible combinations of the subsets of the mutation list, run each combination for
    // all possible permutation, for all 3 different type of documents.
    int testCases = 0;
    for (int subsetSize = 0; subsetSize <= mutations.size(); ++subsetSize) {
      Set<Set<Mutation>> combinations = Sets.combinations(Sets.newHashSet(mutations), subsetSize);
      for (Set<Mutation> combination : combinations) {
        testCases += runPermutationTests(docs, Lists.newArrayList(combination));
      }
    }

    // There are (0! + 7*1! + 21*2! + 35*3! + 35*4! + 21*5! + 7*6! + 7!) * 3 = 41100 cases.
    assertEquals(41100, testCases);
  }

  @Test
  public void testOverlayByCombinationsAndPermutations_ArrayTransforms() {
    List<MutableDocument> docs =
        Lists.newArrayList(
            doc("collection/key", 1, map("foo", "foo-value", "bar.baz", 1)),
            deletedDoc("collection/key", 1),
            unknownDoc("collection/key", 1));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            mergeMutation(
                "collection/key",
                map("foo", "xxx", "arrays", FieldValue.arrayRemove(2)),
                Arrays.asList(field("foo"))),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-1", "arrays", FieldValue.arrayUnion(4, 5))),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-2", "arrays", FieldValue.arrayRemove(5, 6))),
            mergeMutation(
                "collection/key",
                map("foo", "yyy", "arrays", FieldValue.arrayUnion(1, 2, 3, 999)),
                Arrays.asList(field("foo"))));

    int testCases = 0;
    for (int subsetSize = 0; subsetSize <= mutations.size(); ++subsetSize) {
      Set<Set<Mutation>> combinations = Sets.combinations(Sets.newHashSet(mutations), subsetSize);
      for (Set<Mutation> combination : combinations) {
        testCases += runPermutationTests(docs, Lists.newArrayList(combination));
      }
    }

    // There are (0! + 6*1! + 15*2! + 20*3! + 15*4! + 6*5! + 6!) * 3 = 5871 cases.
    assertEquals(5871, testCases);
  }

  @Test
  public void testOverlayByCombinationsAndPermutations_Increments() {
    List<MutableDocument> docs =
        Lists.newArrayList(
            doc("collection/key", 1, map("foo", "foo-value", "bar", 1)),
            deletedDoc("collection/key", 1),
            unknownDoc("collection/key", 1));
    List<Mutation> mutations =
        Lists.newArrayList(
            setMutation("collection/key", map("bar", "bar-value")),
            mergeMutation(
                "collection/key",
                map("foo", "foo-merge", "bar", FieldValue.increment(2)),
                Arrays.asList(field("foo"))),
            new DeleteMutation(key("collection/key"), Precondition.NONE),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-1", "bar", FieldValue.increment(-1.4))),
            patchMutation(
                "collection/key",
                map("foo", "foo-patched-value-2", "bar", FieldValue.increment(3.3))),
            mergeMutation(
                "collection/key",
                map("foo", "yyy", "bar", FieldValue.increment(-41)),
                Arrays.asList(field("foo"))));

    int testCases = 0;
    for (int subsetSize = 0; subsetSize <= mutations.size(); ++subsetSize) {
      Set<Set<Mutation>> combinations = Sets.combinations(Sets.newHashSet(mutations), subsetSize);
      for (Set<Mutation> combination : combinations) {
        testCases += runPermutationTests(docs, Lists.newArrayList(combination));
      }
    }

    // There are (0! + 6*1! + 15*2! + 20*3! + 15*4! + 6*5! + 6!) * 3 = 5871 cases.
    assertEquals(5871, testCases);
  }

  /**
   * For each document in {@code docs}, calculate the overlay mutations of each possible
   * permutation, check whether this holds: document + overlay_mutation = document + mutation_list
   * Returns how many cases it has run.
   */
  private int runPermutationTests(List<MutableDocument> docs, List<Mutation> mutations) {
    int testCases = 0;
    Collection<List<Mutation>> permutations =
        Collections2.permutations(Lists.newArrayList(mutations));
    for (MutableDocument doc : docs) {
      for (List<Mutation> permutation : permutations) {
        verifyOverlayRoundTrips(doc, permutation.toArray(new Mutation[] {}));
        testCases += 1;
      }
    }
    return testCases;
  }

  private String getDescription(
      MutableDocument document, List<Mutation> mutations, @Nullable Mutation overlay) {
    StringBuilder builder = new StringBuilder();
    builder.append("Overlay Mutation failed with:\n");
    builder.append("document:\n");
    builder.append(document + "\n");
    builder.append("\n");

    builder.append("mutations:\n");
    for (Mutation mutation : mutations) {
      builder.append(mutation.toString() + "\n");
    }
    builder.append("\n");

    builder.append("overlay:\n");
    builder.append(overlay == null ? "null" : overlay.toString());
    builder.append("\n\n");

    return builder.toString();
  }

  private void verifyOverlayRoundTrips(MutableDocument doc, Mutation... mutations) {
    MutableDocument docForMutations = doc.mutableCopy();
    MutableDocument docForOverlay = doc.mutableCopy();
    Timestamp now = Timestamp.now();

    FieldMask mask = null;
    for (Mutation m : mutations) {
      mask = m.applyToLocalView(docForMutations, mask, now);
    }

    Mutation overlay = calculateOverlayMutation(docForMutations, mask);
    if (overlay != null) {
      overlay.applyToLocalView(docForOverlay, /* previousMask= */ null, now);
    }

    assertEquals(
        getDescription(doc, Arrays.asList(mutations), overlay), docForOverlay, docForMutations);
  }
}
