// Copyright 2025 Google LLC
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
import static com.google.firebase.firestore.pipeline.Expression.constant;
import static com.google.firebase.firestore.pipeline.Expression.equal;
import static com.google.firebase.firestore.pipeline.Expression.field;
import static com.google.firebase.firestore.pipeline.Expression.isType;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PipelineBsonTypeTest {

  private CollectionReference collection;
  private FirebaseFirestore firestore;

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  static <T> Map.Entry<String, T> entry(String key, T value) {
    return new Map.Entry<String, T>() {
      private String k = key;
      private T v = value;

      @Override
      public String getKey() {
        return k;
      }

      @Override
      public T getValue() {
        return v;
      }

      @Override
      public T setValue(T value) {
        T old = v;
        v = value;
        return old;
      }

      @Override
      public boolean equals(Object o) {
        if (!(o instanceof Map.Entry)) {
          return false;
        }
        Map.Entry<?, ?> that = (Map.Entry<?, ?>) o;
        return com.google.common.base.Objects.equal(k, that.getKey())
            && com.google.common.base.Objects.equal(v, that.getValue());
      }

      @Override
      public int hashCode() {
        return com.google.common.base.Objects.hashCode(k, v);
      }
    };
  }

  @SafeVarargs
  static <T> Map<String, T> mapOfEntries(Map.Entry<String, T>... entries) {
    Map<String, T> res = new LinkedHashMap<>();
    for (Map.Entry<String, T> entry : entries) {
      res.put(entry.getKey(), entry.getValue());
    }
    return Collections.unmodifiableMap(res);
  }

  static final Map<String, Map<String, Object>> bsonDocs =
      mapOfEntries(
          entry(
              "doc1",
              mapOfEntries(
                  entry("bsonObjectId", new BsonObjectId("507f191e810c19729de860ea")),
                  entry("bsonTimestamp", new BsonTimestamp(1000, 1)),
                  entry("bsonBinary", Blob.createBsonBinary(127, new byte[] {1, 2, 3})),
                  entry("int32", new Int32Value(100)),
                  entry("decimal128", new Decimal128Value("1.2e3")),
                  entry("regex", new RegexValue("^foo", "i")))),
          entry(
              "doc2",
              mapOfEntries(
                  entry("bsonObjectId", new BsonObjectId("507f191e810c19729de860eb")),
                  entry("bsonTimestamp", new BsonTimestamp(2000, 2)),
                  entry("bsonBinary", Blob.createBsonBinary(127, new byte[] {1, 2, 4})),
                  entry("int32", new Int32Value(200)),
                  entry("decimal128", new Decimal128Value("2.4e3")),
                  entry("regex", new RegexValue("^bar", "i")))));

  @Before
  public void setup() {
    assumeTrue(
        "BSON types require Enterprise backend edition",
        IntegrationTestUtil.getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE
            && IntegrationTestUtil.testEnvDatabaseId().getDatabaseId().startsWith("enterprise"));
    collection = IntegrationTestUtil.testCollectionWithDocs(bsonDocs);
    firestore = collection.firestore;
  }

  @Test
  public void testSelectBsonTypes() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(collection.getPath())
            .sort(field("int32").ascending())
            .select(
                field("bsonObjectId").alias("oid"),
                field("bsonTimestamp").alias("ts"),
                field("bsonBinary").alias("bin"),
                field("int32").alias("i32"),
                field("decimal128").alias("dec"),
                field("regex").alias("reg"))
            .execute();

    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(2);
    Map<String, Object> data1 = results.get(0).getData();
    assertThat(data1.get("oid")).isEqualTo(new BsonObjectId("507f191e810c19729de860ea"));
    assertThat(data1.get("ts")).isEqualTo(new BsonTimestamp(1000, 1));
    assertThat(data1.get("bin")).isEqualTo(Blob.createBsonBinary(127, new byte[] {1, 2, 3}));
    assertThat(data1.get("i32")).isEqualTo(new Int32Value(100));
    assertThat(data1.get("dec")).isEqualTo(new Decimal128Value("1.2e3"));
    assertThat(data1.get("reg")).isEqualTo(new RegexValue("^foo", "i"));
  }

  @Test
  public void testFilterBsonTypes() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(collection.getPath())
            .where(equal(field("int32"), constant(100)))
            .execute();

    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getData().get("int32")).isEqualTo(new Int32Value(100));
  }

  @Test
  public void testIsTypeBsonTypes() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(collection.getPath())
            .limit(1)
            .select(isType(field("decimal128"), "decimal128").alias("isDec"))
            .execute();

    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(1);
    Map<String, Object> data = results.get(0).getData();
    assertThat((Boolean) data.get("isDec")).isTrue();
  }

  @Test
  public void testSortMixedTypes() {
    Map<String, Map<String, Object>> mixDocs =
        mapOfEntries(
            entry("2_null", mapOfEntries(entry("id", "null"), entry("mix", null))),
            entry("3_bool", mapOfEntries(entry("id", "bool"), entry("mix", true))),
            entry("4_num", mapOfEntries(entry("id", "num"), entry("mix", new Int32Value(42)))),
            entry("5_ts", mapOfEntries(entry("id", "ts"), entry("mix", new BsonTimestamp(1, 1)))),
            entry("6_str", mapOfEntries(entry("id", "str"), entry("mix", "abc"))),
            entry(
                "7_blob",
                mapOfEntries(
                    entry("id", "blob"),
                    entry("mix", Blob.createBsonBinary(1, new byte[] {1, 2})))),
            entry(
                "8_oid",
                mapOfEntries(
                    entry("id", "oid"),
                    entry("mix", new BsonObjectId("507f191e810c19729de860ea")))),
            entry(
                "9_reg",
                mapOfEntries(entry("id", "reg"), entry("mix", new RegexValue("^foo", "i")))));

    CollectionReference mixColl = IntegrationTestUtil.testCollectionWithDocs(mixDocs);

    Task<Pipeline.Snapshot> execute =
        mixColl
            .firestore
            .pipeline()
            .collection(mixColl.getPath())
            .sort(field("mix").ascending())
            .select(field("id").alias("id"))
            .execute();

    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(8);
    assertThat(results.get(0).getData().get("id")).isEqualTo("null");
    assertThat(results.get(1).getData().get("id")).isEqualTo("bool");
    assertThat(results.get(2).getData().get("id")).isEqualTo("num");
    assertThat(results.get(3).getData().get("id")).isEqualTo("ts");
    assertThat(results.get(4).getData().get("id")).isEqualTo("str");
    assertThat(results.get(5).getData().get("id")).isEqualTo("blob");
    assertThat(results.get(6).getData().get("id")).isEqualTo("oid");
    assertThat(results.get(7).getData().get("id")).isEqualTo("reg");
  }
}
