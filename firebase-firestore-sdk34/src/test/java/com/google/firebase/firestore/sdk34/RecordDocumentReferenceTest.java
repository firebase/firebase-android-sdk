/*
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

import static com.google.firebase.firestore.sdk34.LocalFirestoreHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import com.google.firebase.firestore.sdk34.LocalFirestoreHelper;
import com.google.firestore.v1.BatchGetDocumentsRequest;
import com.google.firestore.v1.CommitRequest;
import com.google.firestore.v1.CommitResponse;
import com.google.firestore.v1.Value;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;


@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RecordDocumentReferenceTest {
  @Test
  public void serializeBasicTypes() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference.set(ALL_SUPPORTED_TYPES_OBJECT).get();

    var expectedCommit = commit(set(ALL_SUPPORTED_TYPES_PROTO));
    assertCommitEquals(expectedCommit, commitCapture.getAllValues().get(0));
  }

  @Test
  public void doesNotSerializeAdvancedNumberTypes() {
    Map<InvalidRecord, String> expectedErrorMessages = new HashMap<>();

    var record = new InvalidRecord(new BigInteger("0"), null, null);
    expectedErrorMessages.put(
        record,
        "Could not serialize object. Numbers of type BigInteger are not supported, please use an int, long, float, double or BigDecimal (found in field 'bigIntegerValue')");

    record = new InvalidRecord(null, (byte) 0, null);
    expectedErrorMessages.put(
        record,
        "Could not serialize object. Numbers of type Byte are not supported, please use an int, long, float, double or BigDecimal (found in field 'byteValue')");

    record = new InvalidRecord(null, null, (short) 0);
    expectedErrorMessages.put(
        record,
        "Could not serialize object. Numbers of type Short are not supported, please use an int, long, float, double or BigDecimal (found in field 'shortValue')");

    for (var testCase : expectedErrorMessages.entrySet()) {
      try {
        documentReference.set(testCase.getKey());
        fail();
      } catch (IllegalArgumentException e) {
        assertEquals(testCase.getValue(), e.getMessage());
      }
    }
  }

  @Test
  public void doesNotDeserializeAdvancedNumberTypes() throws Exception {
    var fieldNamesToTypeNames =
        map("bigIntegerValue", "BigInteger", "shortValue", "Short", "byteValue", "Byte");

    for (var testCase : fieldNamesToTypeNames.entrySet()) {
      var fieldName = testCase.getKey();
      var typeName = testCase.getValue();
      var response = map(fieldName, Value.newBuilder().setIntegerValue(0).build());

      doAnswer(getAllResponse(response))
          .when(firestoreMock)
          .streamRequest(
              getAllCapture.capture(),
              streamObserverCapture.capture(),
              Matchers.<ServerStreamingCallable>any());

      var snapshot = documentReference.get().get();
      try {
        snapshot.toObject(InvalidRecord.class);
        fail();
      } catch (RuntimeException e) {
        assertEquals(
            String.format(
                "Could not deserialize object. Deserializing values to %s is not supported (found in field '%s')",
                typeName, fieldName),
            e.getMessage());
      }
    }
  }

  @Test
  public void createDocument() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference.create(SINGLE_COMPONENT_OBJECT).get();

    CommitRequest expectedCommit = commit(create(SINGLE_COMPONENT_PROTO));

    List<CommitRequest> commitRequests = commitCapture.getAllValues();
    assertCommitEquals(expectedCommit, commitRequests.get(0));
  }

  @Test
  public void createWithServerTimestamp() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference.create(LocalFirestoreHelper.SERVER_TIMESTAMP_OBJECT).get();

    var create =
        commit(
            create(Collections.emptyMap()),
            transform("foo", serverTimestamp(), "inner.bar", serverTimestamp()));

    var commitRequests = commitCapture.getAllValues();
    assertCommitEquals(create, commitRequests.get(0));
  }

  @Test
  public void setWithServerTimestamp() throws Exception {
    doReturn(FIELD_TRANSFORM_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference.set(LocalFirestoreHelper.SERVER_TIMESTAMP_OBJECT).get();

    var set =
        commit(
            set(SERVER_TIMESTAMP_PROTO),
            transform("foo", serverTimestamp(), "inner.bar", serverTimestamp()));

    var commitRequests = commitCapture.getAllValues();
    assertCommitEquals(set, commitRequests.get(0));
  }

  @Test
  public void mergeWithServerTimestamps() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference
        .set(LocalFirestoreHelper.SERVER_TIMESTAMP_OBJECT, SetOptions.mergeFields("inner.bar"))
        .get();

    var set =
        commit(
            set(SERVER_TIMESTAMP_PROTO, new ArrayList<>()),
            transform("inner.bar", serverTimestamp()));

    var commitRequests = commitCapture.getAllValues();
    assertCommitEquals(set, commitRequests.get(0));
  }

  @Test
  public void setDocumentWithMerge() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference.set(SINGLE_COMPONENT_OBJECT, SetOptions.merge()).get();
    documentReference.set(ALL_SUPPORTED_TYPES_OBJECT, SetOptions.mergeFields("foo")).get();
    documentReference
        .set(ALL_SUPPORTED_TYPES_OBJECT, SetOptions.mergeFields(Arrays.asList("foo")))
        .get();
    documentReference
        .set(
            ALL_SUPPORTED_TYPES_OBJECT,
            SetOptions.mergeFieldPaths(Arrays.asList(FieldPath.of("foo"))))
        .get();

    var expectedCommit = commit(set(SINGLE_COMPONENT_PROTO, Arrays.asList("foo")));

    for (var i = 0; i < 4; ++i) {
      assertCommitEquals(expectedCommit, commitCapture.getAllValues().get(i));
    }
  }

  @Test
  public void setDocumentWithNestedMerge() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference.set(NESTED_RECORD_OBJECT, SetOptions.mergeFields("first.foo")).get();
    documentReference
        .set(NESTED_RECORD_OBJECT, SetOptions.mergeFields(Arrays.asList("first.foo")))
        .get();
    documentReference
        .set(
            NESTED_RECORD_OBJECT,
            SetOptions.mergeFieldPaths(Arrays.asList(FieldPath.of("first", "foo"))))
        .get();

    Map<String, Value> nestedUpdate = new HashMap<>();
    var nestedProto = Value.newBuilder();
    nestedProto.getMapValueBuilder().putAllFields(SINGLE_COMPONENT_PROTO);
    nestedUpdate.put("first", nestedProto.build());

    var expectedCommit = commit(set(nestedUpdate, Arrays.asList("first.foo")));

    for (var i = 0; i < 3; ++i) {
      assertCommitEquals(expectedCommit, commitCapture.getAllValues().get(i));
    }
  }

  @Test
  public void setMultipleFieldsWithMerge() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference
        .set(
            NESTED_RECORD_OBJECT,
            SetOptions.mergeFields("first.foo", "second.foo", "second.trueValue"))
        .get();

    Map<String, Value> nestedUpdate = new HashMap<>();
    var nestedProto = Value.newBuilder();
    nestedProto.getMapValueBuilder().putAllFields(SINGLE_COMPONENT_PROTO);
    nestedUpdate.put("first", nestedProto.build());
    nestedProto
        .getMapValueBuilder()
        .putFields("trueValue", Value.newBuilder().setBooleanValue(true).build());
    nestedUpdate.put("second", nestedProto.build());

    var expectedCommit =
        commit(set(nestedUpdate, Arrays.asList("first.foo", "second.foo", "second.trueValue")));

    assertCommitEquals(expectedCommit, commitCapture.getValue());
  }

  @Test
  public void setNestedMapWithMerge() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference.set(NESTED_RECORD_OBJECT, SetOptions.mergeFields("first", "second")).get();

    Map<String, Value> nestedUpdate = new HashMap<>();
    var nestedProto = Value.newBuilder();
    nestedProto.getMapValueBuilder().putAllFields(SINGLE_COMPONENT_PROTO);
    nestedUpdate.put("first", nestedProto.build());
    nestedProto.getMapValueBuilder().putAllFields(ALL_SUPPORTED_TYPES_PROTO);
    nestedUpdate.put("second", nestedProto.build());

    var expectedCommit = commit(set(nestedUpdate, Arrays.asList("first", "second")));
    assertCommitEquals(expectedCommit, commitCapture.getValue());
  }

  @Test
  public void extractFieldMaskFromMerge() throws Exception {
    doReturn(SINGLE_WRITE_COMMIT_RESPONSE)
        .when(firestoreMock)
        .sendRequest(
            commitCapture.capture(), Matchers.<UnaryCallable<CommitRequest, CommitResponse>>any());

    documentReference.set(NESTED_RECORD_OBJECT, SetOptions.merge()).get();

    Map<String, Value> nestedUpdate = new HashMap<>();
    var nestedProto = Value.newBuilder();
    nestedProto.getMapValueBuilder().putAllFields(SINGLE_COMPONENT_PROTO);
    nestedUpdate.put("first", nestedProto.build());
    nestedProto.getMapValueBuilder().putAllFields(ALL_SUPPORTED_TYPES_PROTO);
    nestedUpdate.put("second", nestedProto.build());

    var updateMask =
        Arrays.asList(
            "first.foo",
            "second.arrayValue",
            "second.bytesValue",
            "second.dateValue",
            "second.doubleValue",
            "second.falseValue",
            "second.foo",
            "second.geoPointValue",
            "second.infValue",
            "second.longValue",
            "second.nanValue",
            "second.negInfValue",
            "second.nullValue",
            "second.objectValue.foo",
            "second.timestampValue",
            "second.trueValue",
            "second.model.foo");

    var expectedCommit = commit(set(nestedUpdate, updateMask));
    assertCommitEquals(expectedCommit, commitCapture.getValue());
  }
}
*/
