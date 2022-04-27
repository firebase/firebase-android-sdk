// Copyright 2021 Google LLC
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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.TestUtil.decodeValue;
import static org.junit.Assert.fail;

import android.content.res.AssetManager;
import com.google.android.gms.tasks.Tasks;
import com.google.apphosting.datastore.testing.DatastoreTestTrace.TestTrace;
import com.google.firebase.firestore.conformance.ConformanceRuntime;
import com.google.firebase.firestore.conformance.TestCaseConverter;
import com.google.firebase.firestore.conformance.TestCaseIgnoreList;
import com.google.firebase.firestore.conformance.TestCollection;
import com.google.firebase.firestore.conformance.TestDocument;
import com.google.firebase.firestore.conformance.model.Collection;
import com.google.firebase.firestore.conformance.model.Order;
import com.google.firebase.firestore.conformance.model.QueryFilter;
import com.google.firebase.firestore.conformance.model.Result;
import com.google.firebase.firestore.conformance.model.TestCase;
import com.google.firebase.firestore.conformance.model.Where;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.Value;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Runs the client conformance test.
 *
 * <p>These tests rely on test traces that were generated to validate query logic on the backend,
 * which the SDKs should also match. The test cases themselves are directly copied from the internal
 * backend test suite, while the test runners (including all code in {@link
 * com.google.firebase.firestore.conformance}) were modified to support the Android SDK.
 */
@RunWith(Parameterized.class)
public class ConformanceTest {
  private static final FirebaseFirestore firestore = testFirestore();
  private static TestCaseIgnoreList testCaseIgnoreList;

  static {
    try {
      AssetManager assetManager = getInstrumentation().getTargetContext().getAssets();
      testCaseIgnoreList = new TestCaseIgnoreList(assetManager.open("conformance/ignorelist.txt"));
    } catch (IOException e) {
      fail("Failed to load ignorelist: " + e);
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static Iterable<String> data() throws IOException {
    // Enumerate the Protobuf files containing the spec tests.
    AssetManager assetManager = getInstrumentation().getTargetContext().getAssets();
    String[] files = assetManager.list("conformance");
    return Arrays.stream(files)
        .sorted()
        .filter(f -> f.endsWith(".pb"))
        .collect(Collectors.toList());
  }

  private final List<TestCase> testCases;

  public ConformanceTest(String testFileNames) throws IOException {
    testCases = loadTestCases(testFileNames);
  }

  /**
   * Loads all backend tests and parses them into {@link TestCase} models. Test cases that are not
   * supported on mobile are filtered.
   */
  private List<TestCase> loadTestCases(String testFileNames) throws IOException {
    AssetManager assetManager = getInstrumentation().getTargetContext().getAssets();
    TestCaseConverter testCaseConverter = new TestCaseConverter();
    try (InputStream inputStream = assetManager.open("conformance/" + testFileNames)) {
      TestTrace testTrace = TestTrace.parseFrom(inputStream);
      return testCaseConverter.convertTestCases(testTrace).stream()
          .filter(testCaseIgnoreList)
          .collect(Collectors.toList());
    }
  }

  @BeforeClass
  public static void beforeClass() throws ExecutionException, InterruptedException {
    // Disable logging to speed up test runs.
    FirebaseFirestore.setLoggingEnabled(false);
    // Disable network to reduce costs.
    Tasks.await(firestore.disableNetwork());
  }

  @AfterClass
  public static void afterClass() {
    FirebaseFirestore.setLoggingEnabled(true);
  }

  @Test
  public void run() throws Exception {
    for (TestCase testCase : testCases) {
      ConformanceRuntime runtime = new ConformanceRuntime(firestore, Source.CACHE);
      executeTestCases(testCase, runtime);
    }
  }

  private void executeTestCases(TestCase testCase, ConformanceRuntime runtime) throws Exception {
    // Note: This method is copied from Google3 and modified to match the Android API.

    TestCollection testCollection;
    TestDocument testDocument;

    for (Collection collection : testCase.getCollections()) {
      testCollection = runtime.addInitialCollectionWithPath(collection.getName());
      for (Document document : collection.getDocuments()) {
        testDocument = testCollection.addDocumentWithId(getId(document));
        for (Map.Entry<String, Value> field : document.getFieldsMap().entrySet()) {
          testDocument.putField(field.getKey(), decodeValue(firestore, field.getValue()));
        }
      }

      if (testCase.getException()) {
        runtime.expectException();
      } else {
        Result result = testCase.getResult();
        for (Document document : result.getDocuments()) {
          testDocument = runtime.addExpectedDocumentWithId(getId(document));
          for (Map.Entry<String, Value> field : document.getFieldsMap().entrySet()) {
            testDocument.putField(field.getKey(), decodeValue(firestore, field.getValue()));
          }
        }
      }

      try {
        runtime.setup();

        try {
          Query query = runtime.createQueryAtPath(testCase.getQuery().getCollection());
          for (QueryFilter filter : testCase.getQuery().getFilters()) {
            Where where = filter.getWhere();
            if (where != null) {
              switch (where.getOp()) {
                case LESS_THAN:
                  query =
                      query.whereLessThan(
                          formatFieldPath(where.getField()),
                          decodeValue(firestore, where.getValue()));
                  break;
                case LESS_THAN_OR_EQUAL:
                  query =
                      query.whereLessThanOrEqualTo(
                          formatFieldPath(where.getField()),
                          decodeValue(firestore, where.getValue()));
                  break;
                case GREATER_THAN:
                  query =
                      query.whereGreaterThan(
                          formatFieldPath(where.getField()),
                          decodeValue(firestore, where.getValue()));
                  break;
                case GREATER_THAN_OR_EQUAL:
                  query =
                      query.whereGreaterThanOrEqualTo(
                          formatFieldPath(where.getField()),
                          decodeValue(firestore, where.getValue()));
                  break;
                case EQUAL:
                  query =
                      query.whereEqualTo(
                          formatFieldPath(where.getField()),
                          decodeValue(firestore, where.getValue()));
                  break;
                case NOT_EQUAL:
                  query =
                      query.whereNotEqualTo(
                          formatFieldPath(where.getField()),
                          decodeValue(firestore, where.getValue()));
                  break;
                case ARRAY_CONTAINS:
                  query =
                      query.whereArrayContains(
                          formatFieldPath(where.getField()),
                          decodeValue(firestore, where.getValue()));
                  break;
                case IN:
                  query =
                      query.whereIn(
                          formatFieldPath(where.getField()),
                          Collections.singletonList(decodeValue(firestore, where.getValue())));
                  break;
                case ARRAY_CONTAINS_ANY:
                  query =
                      query.whereArrayContainsAny(
                          formatFieldPath(where.getField()),
                          Collections.singletonList(decodeValue(firestore, where.getValue())));
                  break;
                case NOT_IN:
                  query =
                      query.whereNotIn(
                          formatFieldPath(where.getField()),
                          Collections.singletonList(decodeValue(firestore, where.getValue())));
                  break;
                default:
                  throw new Exception("Unexpected operation: " + where.getOp());
              }
            }

            Order order = filter.getOrder();
            if (order != null) {
              query =
                  query.orderBy(
                      formatFieldPath(order.getField()),
                      order.getDirection().equals(StructuredQuery.Direction.ASCENDING)
                          ? Query.Direction.ASCENDING
                          : Query.Direction.DESCENDING);
            }

            Value startAt = filter.getStartAt();
            if (startAt != null) {
              query = query.startAt(decodeValue(firestore, startAt));
            }

            Value startAfter = filter.getStartAfter();
            if (startAfter != null) {
              query = query.startAfter(decodeValue(firestore, startAfter));
            }

            Value endBefore = filter.getEndBefore();
            if (endBefore != null) {
              query = query.endBefore(decodeValue(firestore, endBefore));
            }

            Value endAt = filter.getEndAt();
            if (endAt != null) {
              query = query.endAt(decodeValue(firestore, endAt));
            }

            Long limit = filter.getLimit();
            if (limit != null && limit > 0) {
              query = query.limit(limit);
            }
          }

          runtime.runQuery(query);
        } catch (RuntimeException | AssertionError x) {
          runtime.checkQueryError(x);
        }
      } finally {
        runtime.teardown();
      }
    }
  }

  private com.google.firebase.firestore.FieldPath formatFieldPath(String serverFormat) {
    FieldPath fieldPath = FieldPath.fromServerFormat(serverFormat);
    String[] segments = new String[fieldPath.length()];
    for (int i = 0; i < fieldPath.length(); ++i) {
      segments[i] = fieldPath.getSegment(i);
    }
    return com.google.firebase.firestore.FieldPath.of(segments);
  }

  private String getId(Document document) {
    return DocumentKey.fromName(document.getName()).getDocumentId();
  }
}
