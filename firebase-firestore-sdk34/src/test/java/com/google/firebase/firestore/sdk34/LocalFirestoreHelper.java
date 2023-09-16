/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.firestore.sdk34;

import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.mockito.stubbing.Answer;

import com.google.common.reflect.TypeToken;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.GeoPoint;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.BatchGetDocumentsResponse;
import com.google.firestore.v1.CommitRequest;
import com.google.firestore.v1.CommitResponse;
import com.google.firestore.v1.DocumentMask;
import com.google.firestore.v1.DocumentTransform.FieldTransform;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import com.google.firestore.v1.Write;
import com.google.gson.Gson;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;


public final class LocalFirestoreHelper {

  /*
  public static final String DATABASE_NAME;
  public static final String DOCUMENT_PATH;
  public static final String DOCUMENT_NAME;
  public static final String DOCUMENT_ROOT;

  public static final SingleComponent SINGLE_COMPONENT_OBJECT;
  public static final Map<String, Value> SINGLE_COMPONENT_PROTO;

  public static final NestedRecord NESTED_RECORD_OBJECT;

  public static final ServerTimestamp SERVER_TIMESTAMP_OBJECT;
  public static final Map<String, Value> SERVER_TIMESTAMP_PROTO;

  public static final AllSupportedTypes ALL_SUPPORTED_TYPES_OBJECT;
  public static final Map<String, Value> ALL_SUPPORTED_TYPES_PROTO;

  public static final Date DATE;
  public static final Timestamp TIMESTAMP;
  public static final GeoPoint GEO_POINT;
  public static final Blob BLOB;


  public record SingleComponent(

    String foo
  ){}

  public record NestedRecord(
    SingleComponent first,
    AllSupportedTypes second
  ){}
*/

  public record ServerTimestamp (

    @com.google.firebase.firestore.sdk34.ServerTimestamp Date foo,
    Inner inner

  ){
    record Inner (

      @com.google.firebase.firestore.sdk34.ServerTimestamp Date bar
    ){}
  }

  public record InvalidRecord (
    BigInteger bigIntegerValue,
    Byte byteValue,
    Short shortValue
  ){}

  public static <K, V> Map<K, V> map(K key, V value, Object... moreKeysAndValues) {
    Map<K, V> map = new HashMap<>();
    map.put(key, value);

    for (var i = 0; i < moreKeysAndValues.length; i += 2) {
      map.put((K) moreKeysAndValues[i], (V) moreKeysAndValues[i + 1]);
    }

    return map;
  }

  /*
  public static Answer<BatchGetDocumentsResponse> getAllResponse(
      final Map<String, Value>... fields) {
    var responses = new BatchGetDocumentsResponse[fields.length];

    for (var i = 0; i < fields.length; ++i) {
      var name = DOCUMENT_NAME;
      if (fields.length > 1) {
        name += i + 1;
      }
      var response = BatchGetDocumentsResponse.newBuilder();
      response
          .getFoundBuilder()
          .setCreateTime(com.google.protobuf.Timestamp.newBuilder().setSeconds(1).setNanos(2));
      response
          .getFoundBuilder()
          .setUpdateTime(com.google.protobuf.Timestamp.newBuilder().setSeconds(3).setNanos(4));
      response.setReadTime(com.google.protobuf.Timestamp.newBuilder().setSeconds(5).setNanos(6));
      response.getFoundBuilder().setName(name).putAllFields(fields[i]);
      responses[i] = response.build();
    }

    return streamingResponse(responses, null);
  }

  /** Returns a stream of responses followed by an optional exception. * /
  public static <T> Answer<T> streamingResponse(
      final T[] response, @Nullable final Throwable throwable) {
    return invocation -> {
      var args = invocation.getArguments();
      var observer = (ResponseObserver<T>) args[1];
      observer.onStart(mock(StreamController.class));
      for (var resp : response) {
        observer.onResponse(resp);
      }
      if (throwable != null) {
        observer.onError(throwable);
      }
      observer.onComplete();
      return null;
    };
  }

  public static ApiFuture<CommitResponse> commitResponse(int adds, int deletes) {
    var commitResponse = CommitResponse.newBuilder();
    commitResponse.getCommitTimeBuilder().setSeconds(0).setNanos(0);
    for (var i = 0; i < adds; ++i) {
      commitResponse.addWriteResultsBuilder().getUpdateTimeBuilder().setSeconds(i).setNanos(i);
    }
    for (var i = 0; i < deletes; ++i) {
      commitResponse.addWriteResultsBuilder();
    }
    return ApiFutures.immediateFuture(commitResponse.build());
  }

  public static FieldTransform serverTimestamp() {
    return FieldTransform.newBuilder()
        .setSetToServerValue(FieldTransform.ServerValue.REQUEST_TIME)
        .build();
  }

  public static List<FieldTransform> transform(
      String fieldPath, FieldTransform fieldTransform, Object... fieldPathOrTransform) {

    List<FieldTransform> transforms = new ArrayList<>();
    var transformBuilder = FieldTransform.newBuilder();
    transformBuilder.setFieldPath(fieldPath).mergeFrom(fieldTransform);

    transforms.add(transformBuilder.build());

    for (var i = 0; i < fieldPathOrTransform.length; i += 2) {
      var path = (String) fieldPathOrTransform[i];
      var transform = (FieldTransform) fieldPathOrTransform[i + 1];
      transforms.add(FieldTransform.newBuilder().setFieldPath(path).mergeFrom(transform).build());
    }
    return transforms;
  }

  public static Write create(Map<String, Value> fields, String docPath) {
    var write = Write.newBuilder();
    var document = write.getUpdateBuilder();
    document.setName(DOCUMENT_ROOT + docPath);
    document.putAllFields(fields);
    write.getCurrentDocumentBuilder().setExists(false);
    return write.build();
  }

  public static Write create(Map<String, Value> fields) {
    return create(fields, DOCUMENT_PATH);
  }

  public static Write set(Map<String, Value> fields) {
    return set(fields, null, DOCUMENT_PATH);
  }

  public static Write set(Map<String, Value> fields, @Nullable List<String> fieldMap) {
    return set(fields, fieldMap, DOCUMENT_PATH);
  }

  public static Write set(
      Map<String, Value> fields, @Nullable List<String> fieldMap, String docPath) {
    var write = Write.newBuilder();
    var document = write.getUpdateBuilder();
    document.setName(DOCUMENT_ROOT + docPath);
    document.putAllFields(fields);

    if (fieldMap != null) {
      write.getUpdateMaskBuilder().addAllFieldPaths(fieldMap);
    }

    return write.build();
  }

  public static CommitRequest commit(@Nullable String transactionId, Write... writes) {
    var commitRequest = CommitRequest.newBuilder();
    commitRequest.setDatabase(DATABASE_NAME);
    commitRequest.addAllWrites(Arrays.asList(writes));

    if (transactionId != null) {
      commitRequest.setTransaction(ByteString.copyFromUtf8(transactionId));
    }

    return commitRequest.build();
  }

  public static CommitRequest commit(Write... writes) {
    return commit(null, writes);
  }

  public static CommitRequest commit(Write write, List<FieldTransform> transforms) {
    return commit((String) null, write.toBuilder().addAllUpdateTransforms(transforms).build());
  }

  public static void assertCommitEquals(CommitRequest expected, CommitRequest actual) {
    assertEquals(sortCommit(expected), sortCommit(actual));
  }

  private static CommitRequest sortCommit(CommitRequest commit) {
    var builder = commit.toBuilder();

    for (var writes : builder.getWritesBuilderList()) {
      if (writes.hasUpdateMask()) {
        var updateMask = new ArrayList<>(writes.getUpdateMask().getFieldPathsList());
        Collections.sort(updateMask);
        writes.setUpdateMask(DocumentMask.newBuilder().addAllFieldPaths(updateMask));
      }

      if (!writes.getUpdateTransformsList().isEmpty()) {
        var transformList = new ArrayList<>(writes.getUpdateTransformsList());
        transformList.sort(Comparator.comparing(FieldTransform::getFieldPath));
        writes.clearUpdateTransforms().addAllUpdateTransforms(transformList);
      }
    }

    return builder.build();
  }

  public record AllSupportedTypes (

    String foo,
    Double doubleValue,
    long longValue,
    double nanValue,
    double infValue,
    double negInfValue,
    boolean trueValue,
    boolean falseValue,
    SingleComponent objectValue,
    Date dateValue,
    Timestamp timestampValue,
    List<String> arrayValue,
    String nullValue,
    Blob bytesValue,
    GeoPoint geoPointValue,
    Map<String, Object> model
  ){}

  static {
    try {
      DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S z").parse("1985-03-18 08:20:00.123 CET");
    } catch (ParseException e) {
      throw new RuntimeException("Failed to parse date", e);
    }

    TIMESTAMP =
        Timestamp.ofTimeSecondsAndNanos(
            TimeUnit.MILLISECONDS.toSeconds(DATE.getTime()),
            123000); // Firestore truncates to microsecond precision.
    GEO_POINT = new GeoPoint(50.1430847, -122.9477780);
    BLOB = Blob.fromBytes(new byte[] {1, 2, 3});

    DATABASE_NAME = "projects/test-project/databases/(default)";
    DOCUMENT_PATH = "coll/doc";
    DOCUMENT_NAME = DATABASE_NAME + "/documents/" + DOCUMENT_PATH;
    DOCUMENT_ROOT = DATABASE_NAME + "/documents/";

    SINGLE_COMPONENT_OBJECT = new SingleComponent("bar");
    SINGLE_COMPONENT_PROTO = map("foo", Value.newBuilder().setStringValue("bar").build());

    SERVER_TIMESTAMP_PROTO = Collections.emptyMap();
    SERVER_TIMESTAMP_OBJECT = new ServerTimestamp(null, new ServerTimestamp.Inner(null));

    ALL_SUPPORTED_TYPES_OBJECT = new AllSupportedTypes("bar", 0.0, 0L, Double.NaN, Double.POSITIVE_INFINITY,
                                                       Double.NEGATIVE_INFINITY, true, false,
                                                       new SingleComponent("bar"), DATE,
                                                       TIMESTAMP, ImmutableList.of("foo"), null, BLOB, GEO_POINT,
                                                       ImmutableMap.of("foo", SINGLE_COMPONENT_OBJECT.foo()));
    ALL_SUPPORTED_TYPES_PROTO =
        ImmutableMap.<String, Value>builder()
            .put("foo", Value.newBuilder().setStringValue("bar").build())
            .put("doubleValue", Value.newBuilder().setDoubleValue(0.0).build())
            .put("longValue", Value.newBuilder().setIntegerValue(0L).build())
            .put("nanValue", Value.newBuilder().setDoubleValue(Double.NaN).build())
            .put("infValue", Value.newBuilder().setDoubleValue(Double.POSITIVE_INFINITY).build())
            .put("negInfValue", Value.newBuilder().setDoubleValue(Double.NEGATIVE_INFINITY).build())
            .put("trueValue", Value.newBuilder().setBooleanValue(true).build())
            .put("falseValue", Value.newBuilder().setBooleanValue(false).build())
            .put(
                "objectValue",
                Value.newBuilder()
                    .setMapValue(MapValue.newBuilder().putAllFields(SINGLE_COMPONENT_PROTO))
                    .build())
            .put(
                "dateValue",
                Value.newBuilder()
                    .setTimestampValue(
                        com.google.protobuf.Timestamp.newBuilder()
                            .setSeconds(479978400)
                            .setNanos(123000000)) // Dates only support millisecond precision.
                    .build())
            .put(
                "timestampValue",
                Value.newBuilder()
                    .setTimestampValue(
                        com.google.protobuf.Timestamp.newBuilder()
                            .setSeconds(479978400)
                            .setNanos(123000)) // Timestamps supports microsecond precision.
                    .build())
            .put(
                "arrayValue",
                Value.newBuilder()
                    .setArrayValue(
                                    ArrayValue.newBuilder().addValues(Value.newBuilder().setStringValue("foo")))
                    .build())
            .put("nullValue", Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build())
            .put("bytesValue", Value.newBuilder().setBytesValue(BLOB.toByteString()).build())
            .put(
                "geoPointValue",
                Value.newBuilder()
                    .setGeoPointValue(
                                    LatLng.newBuilder().setLatitude(50.1430847).setLongitude(-122.9477780))
                    .build())
            .put(
                "model",
                Value.newBuilder()
                    .setMapValue(MapValue.newBuilder().putAllFields(SINGLE_COMPONENT_PROTO))
                    .build())
            .build();
    SINGLE_WRITE_COMMIT_RESPONSE = commitResponse(/* adds= * / 1, /* deletes= * / 0);

    FIELD_TRANSFORM_COMMIT_RESPONSE = commitResponse(/* adds= * / 2, /* deletes= * / 0);

    NESTED_RECORD_OBJECT = new NestedRecord(SINGLE_COMPONENT_OBJECT, ALL_SUPPORTED_TYPES_OBJECT);
  }
  */

  @SuppressWarnings("unchecked")
  public static <T> Map<String, T> mapAnyType(Object... entries) {
    Map<String, T> res = new HashMap<>();
    for (var i = 0; i < entries.length; i += 2) {
      res.put((String) entries[i], (T) entries[i + 1]);
    }
    return res;
  }

  private static Map<String, Object> fromJsonString(String json) {
    var type = new TypeToken<Map<String, Object>>() {}.getType();
    var gson = new Gson();
    return gson.fromJson(json, type);
  }

  public static Map<String, Object> fromSingleQuotedString(String json) {
    return fromJsonString(json.replace("'", "\""));
  }
}
