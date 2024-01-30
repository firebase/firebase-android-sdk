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

package com.google.firebase.firestore.bundle;

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.remote.RemoteSerializer;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.MapValue;
import com.google.firestore.v1.Value;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BundleReaderTest {

  private static final DatabaseId TEST_PROJECT = DatabaseId.forProject("test-project");
  private static final BundleSerializer SERIALIZER =
      new BundleSerializer(new RemoteSerializer(TEST_PROJECT));
  public static final BundleMetadata BUNDLE_METADATA =
      new BundleMetadata(
          "bundle-1", 1, version(6000000L), /* totalDocuments= */ 1, /* totalBytes= */ 1);
  public static final NamedQuery LIMIT_QUERY =
      new NamedQuery(
          "limitQuery",
          new BundledQuery(
              new Query(
                      ResourcePath.fromString("foo"),
                      null,
                      Collections.emptyList(),
                      Collections.singletonList(orderBy("sort")),
                      1,
                      Query.LimitType.LIMIT_TO_FIRST,
                      null,
                      null)
                  .toTarget(),
              Query.LimitType.LIMIT_TO_FIRST),
          version(1590011379000001L));
  public static final NamedQuery LIMIT_TO_LAST_QUERY =
      new NamedQuery(
          "limitToLastQuery",
          new BundledQuery(
              new Query(
                      ResourcePath.fromString("foo"),
                      null,
                      Collections.emptyList(),
                      Collections.singletonList(orderBy("sort")),
                      1,
                      // Note this is LIMIT_TO_FIRST because it is the expected
                      // limit type in bundle files.
                      Query.LimitType.LIMIT_TO_FIRST,
                      null,
                      null)
                  .toTarget(),
              Query.LimitType.LIMIT_TO_LAST),
          version(1590011379000002L));
  public static final BundledDocumentMetadata DELETED_DOC_METADATA =
      new BundledDocumentMetadata(
          key("coll/nodoc"), version(5000600L), /* exists= */ false, Collections.emptyList());
  public static final BundledDocumentMetadata DOC1_METADATA =
      new BundledDocumentMetadata(
          key("coll/doc1"), version(5600000L), /* exists= */ true, Collections.emptyList());
  public static final BundleDocument DOC1 =
      new BundleDocument(
          doc(
              "coll/doc1",
              30004000L,
              ObjectValue.fromMap(
                  map(
                      "foo",
                      Value.newBuilder().setStringValue("value1").build(),
                      "bar",
                      Value.newBuilder().setIntegerValue(-42).build()))));
  public static final BundledDocumentMetadata DOC2_METADATA =
      new BundledDocumentMetadata(
          key("coll/doc1"), version(5600001L), /* exists= */ true, Collections.emptyList());
  public static final BundleDocument DOC2 =
      new BundleDocument(
          doc(
              "coll/doc2",
              30004001L,
              ObjectValue.fromMap(
                  map(
                      "foo",
                      Value.newBuilder().setStringValue("value2").build(),
                      "bar",
                      Value.newBuilder().setIntegerValue(42).build(),
                      "emptyArray",
                      Value.newBuilder().setArrayValue(ArrayValue.getDefaultInstance()).build(),
                      "emptyMap",
                      Value.newBuilder().setMapValue(MapValue.getDefaultInstance()).build()))));
  public static final BundledDocumentMetadata DOC3_METADATA =
      new BundledDocumentMetadata(
          key("coll/doc3"), version(5600002L), /* exists= */ true, Collections.emptyList());
  public static final BundleDocument DOC3 =
      new BundleDocument(
          doc(
              "coll/doc3",
              30004002L,
              ObjectValue.fromMap(
                  map("unicodeValue", Value.newBuilder().setStringValue("\uD83D\uDE0A").build()))));
  private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

  @Test
  public void testReadsQueryAndDocument() throws IOException, JSONException {
    TestBundleBuilder bundleBuilder = new TestBundleBuilder(TEST_PROJECT);
    String limitQuery = addLimitQuery(bundleBuilder);
    String limitToLastQuery = addLimitToLastQuery(bundleBuilder);
    String documentMetadata = addDoc1Metadata(bundleBuilder);
    String document = addDoc1(bundleBuilder);
    String bundle =
        bundleBuilder.build("bundle-1", /* createTimeMicros= */ 6000000L, /* version= */ 1);

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    List<BundleElement> bundleElements =
        verifyAllElements(bundleReader, limitQuery, limitToLastQuery, documentMetadata, document);

    assertEquals(LIMIT_QUERY, bundleElements.get(0));
    assertEquals(LIMIT_TO_LAST_QUERY, bundleElements.get(1));
    assertEquals(DOC1_METADATA, bundleElements.get(2));
    assertEquals(DOC1, bundleElements.get(3));
  }

  @Test
  public void testReadsQueryAndDocumentWithUnexpectedOrder() throws IOException, JSONException {
    TestBundleBuilder bundleBuilder = new TestBundleBuilder(TEST_PROJECT);
    String doc1Metadata = addDoc1Metadata(bundleBuilder);
    String doc1 = addDoc1(bundleBuilder);
    String limitQuery = addLimitQuery(bundleBuilder);
    String doc2Metadata = addDoc2Metadata(bundleBuilder);
    String doc2 = addDoc2(bundleBuilder);
    String bundle =
        bundleBuilder.build("bundle-1", /* createTimeMicros= */ 6000000L, /* version= */ 1);

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    List<BundleElement> bundleElements =
        verifyAllElements(bundleReader, doc1Metadata, doc1, limitQuery, doc2Metadata, doc2);

    assertEquals(DOC1_METADATA, bundleElements.get(0));
    assertEquals(DOC1, bundleElements.get(1));
    assertEquals(LIMIT_QUERY, bundleElements.get(2));
    assertEquals(DOC2_METADATA, bundleElements.get(3));
    assertEquals(DOC2, bundleElements.get(4));
  }

  @Test
  public void testReadsWithoutNamedQuery() throws IOException, JSONException {
    TestBundleBuilder bundleBuilder = new TestBundleBuilder(TEST_PROJECT);
    String documentMetadata = addDoc1Metadata(bundleBuilder);
    String document = addDoc1(bundleBuilder);
    String bundle =
        bundleBuilder.build("bundle-1", /* createTimeMicros= */ 6000000L, /* version= */ 1);

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    List<BundleElement> bundleElements =
        verifyAllElements(bundleReader, documentMetadata, document);

    assertEquals(DOC1_METADATA, bundleElements.get(0));
    assertEquals(DOC1, bundleElements.get(1));
  }

  @Test
  public void testReadsWithDeletedDocument() throws IOException, JSONException {
    TestBundleBuilder bundleBuilder = new TestBundleBuilder(TEST_PROJECT);
    String deletedDocumentMetadata = addDeletedDocMetadata(bundleBuilder);
    String documentMetadata = addDoc1Metadata(bundleBuilder);
    String document = addDoc1(bundleBuilder);
    String bundle =
        bundleBuilder.build("bundle-1", /* createTimeMicros= */ 6000000L, /* version= */ 1);

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    List<BundleElement> bundleElements =
        verifyAllElements(bundleReader, deletedDocumentMetadata, documentMetadata, document);

    assertEquals(DELETED_DOC_METADATA, bundleElements.get(0));
    assertEquals(DOC1_METADATA, bundleElements.get(1));
    assertEquals(DOC1, bundleElements.get(2));
  }

  @Test
  public void testReadsWithoutDocumentOrQuery() throws IOException, JSONException {
    TestBundleBuilder bundleBuilder = new TestBundleBuilder(TEST_PROJECT);
    String bundle =
        bundleBuilder.build("bundle-1", /* createTimeMicros= */ 6000000L, /* version= */ 1);

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    verifyAllElements(bundleReader);
  }

  @Test
  public void testThrowsWithoutLengthPrefix() throws IOException, JSONException {
    String bundle = "{metadata: 'no length prefix' }";

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    assertThrows(IllegalArgumentException.class, () -> bundleReader.getBundleMetadata());
  }

  @Test
  public void testThrowsWithMissingBrackets() throws IOException, JSONException {
    String bundle = "3abc";

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    assertThrows(IllegalArgumentException.class, () -> bundleReader.getBundleMetadata());
  }

  @Test
  public void testThrowsWithInvalidJSON() throws IOException, JSONException {
    String bundle = "3{abc}";

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    assertThrows(JSONException.class, () -> bundleReader.getBundleMetadata());
  }

  @Test
  public void testThrowsWhenSecondElementIsMissing() throws IOException, JSONException {
    TestBundleBuilder bundleBuilder = new TestBundleBuilder(TEST_PROJECT);
    String bundle =
        bundleBuilder.build("bundle-1", /* createTimeMicros= */ 6000000L, /* version= */ 1) + "foo";

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    assertThrows(IllegalArgumentException.class, () -> bundleReader.getNextElement());
  }

  @Test
  public void testThrowsWhenBundleDoesNotContainEnoughData() throws IOException, JSONException {
    String bundle = "3{}";

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    assertThrows(IllegalArgumentException.class, () -> bundleReader.getBundleMetadata());
  }

  @Test
  public void testWhenFirstElementIsNotBundleMetadata() throws IOException, JSONException {
    String json =
        String.format(
            "{ document: {\n"
                + "name: 'projects/%s/databases/%s/documents/%s',\n"
                + "createTime: { seconds: 1, nanos: 2 },\n"
                + "updateTime: { seconds: 3, nanos: 4 },\n"
                + "fields: {}\n"
                + "} }",
            TEST_PROJECT.getProjectId(), TEST_PROJECT.getDatabaseId(), "coll/doc");
    String bundle = json.length() + json;

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(bundle.getBytes(UTF8_CHARSET)));

    assertThrows(IllegalArgumentException.class, () -> bundleReader.getBundleMetadata());
  }

  @Test
  public void testCombinesMultipleBufferReads() throws IOException, JSONException {
    // Create a BundleMetadata element that exceeds the length of the internal buffer.
    StringBuilder longString = new StringBuilder();
    for (int i = 0; i < BundleReader.BUFFER_CAPACITY; ++i) {
      longString.append('a');
    }
    TestBundleBuilder bundleBuilder = new TestBundleBuilder(TEST_PROJECT);
    String json =
        bundleBuilder.build(
            "bundle-" + longString, /* createTimeMicros= */ 6000000L, /* version= */ 1);

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(json.getBytes(UTF8_CHARSET)));

    BundleMetadata expectedMetadata =
        new BundleMetadata("bundle-" + longString, 1, version(6000000L), 0, 0);
    BundleMetadata actualMetadata = bundleReader.getBundleMetadata();
    assertEquals(expectedMetadata, actualMetadata);
  }

  @Test
  public void testReadsUnicodeData() throws IOException, JSONException {
    TestBundleBuilder bundleBuilder = new TestBundleBuilder(TEST_PROJECT);
    String docMetadata = addDoc3Metadata(bundleBuilder);
    String doc = addDoc3(bundleBuilder); // DOC3 contains Unicode data
    String json =
        bundleBuilder.build("bundle-1", /* createTimeMicros= */ 6000000L, /* version= */ 1);

    BundleReader bundleReader =
        new BundleReader(SERIALIZER, new ByteArrayInputStream(json.getBytes(UTF8_CHARSET)));

    List<BundleElement> bundleElements = verifyAllElements(bundleReader, docMetadata, doc);

    assertEquals(DOC3_METADATA, bundleElements.get(0));
    assertEquals(DOC3, bundleElements.get(1));
  }

  private String addDeletedDocMetadata(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addDocumentMetadata(
        "coll/nodoc", /* readTimeMicros= */ 5000600L, /* exists= */ false);
  }

  private String addDoc1(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addDocument(
        "coll/doc1",
        1200000L,
        30004000L,
        "{ foo: { stringValue: 'value1' }, bar: { integerValue: -42 } }");
  }

  private String addDoc1Metadata(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addDocumentMetadata(
        "coll/doc1", /* readTimeMicros= */ 5600000L, /* exists= */ true);
  }

  private String addDoc2(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addDocument(
        "coll/doc2",
        /* createTimeMicros= */ 1200001L,
        /* updateTimeMicros= */ 30004001L,
        "{ foo: { stringValue: 'value2' }, bar: { integerValue: 42 }, "
            + "emptyArray: { arrayValue: {} }, emptyMap: { mapValue: {} } }");
  }

  private String addDoc3Metadata(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addDocumentMetadata(
        "coll/doc3", /* readTimeMicros= */ 5600002L, /* exists= */ true);
  }

  private String addDoc3(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addDocument(
        "coll/doc3",
        /* createTimeMicros= */ 1200002L,
        /* updateTimeMicros= */ 30004002L,
        "{ unicodeValue: { stringValue: '\uD83D\uDE0A' } }");
  }

  private String addDoc2Metadata(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addDocumentMetadata("coll/doc1", 5600001L, true);
  }

  private String addLimitToLastQuery(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addNamedQuery(
        "limitToLastQuery",
        1590011379000002L,
        Query.LimitType.LIMIT_TO_LAST,
        "",
        " {\n"
            + "  from: [{ collectionId: 'foo' }],\n"
            + "  orderBy: [{ field: { fieldPath: 'sort' } }],\n"
            + "  limit: { value: 1 }\n"
            + "}");
  }

  private String addLimitQuery(TestBundleBuilder bundleBuilder) {
    return bundleBuilder.addNamedQuery(
        "limitQuery",
        1590011379000001L,
        Query.LimitType.LIMIT_TO_FIRST,
        "",
        "{\n"
            + "  from: [{ collectionId: 'foo' }],\n"
            + "  orderBy: [{ field: { fieldPath: 'sort' } }],\n"
            + "  limit: { value: 1 }\n"
            + "}");
  }

  private List<BundleElement> verifyAllElements(
      BundleReader bundleReader, String... expectedElements) throws IOException, JSONException {
    List<BundleElement> elements = new ArrayList<>();

    BundleMetadata bundleMetadata = bundleReader.getBundleMetadata();
    assertEquals(BUNDLE_METADATA.getBundleId(), bundleMetadata.getBundleId());
    assertEquals(BUNDLE_METADATA.getSchemaVersion(), bundleMetadata.getSchemaVersion());
    assertEquals(BUNDLE_METADATA.getCreateTime(), bundleMetadata.getCreateTime());

    // The bundle metadata is not considered part of the bytes read. Instead, it encodes the
    // expected size of all elements.
    assertEquals(0, bundleReader.getBytesRead());

    long actualBytesRead = 0;
    long actualDocumentsRead = 0;

    for (String expectedElement : expectedElements) {
      BundleElement nextElement = bundleReader.getNextElement();
      assertNotNull(nextElement);

      if (nextElement instanceof BundleDocument
          || (nextElement instanceof BundledDocumentMetadata
              && !((BundledDocumentMetadata) nextElement).exists())) {
        ++actualDocumentsRead;
      }
      elements.add(nextElement);

      int elementLength = expectedElement.getBytes(UTF8_CHARSET).length;
      actualBytesRead += (int) (Math.log10(elementLength) + 1) + elementLength;
      assertEquals(actualBytesRead, bundleReader.getBytesRead());
    }

    assertEquals(actualBytesRead, bundleMetadata.getTotalBytes());
    assertEquals(actualDocumentsRead, bundleMetadata.getTotalDocuments());

    return elements;
  }

  private static class TestBundleBuilder {
    private final List<String> elements = new ArrayList<>();
    private final DatabaseId databaseId;
    private final RemoteSerializer serializer;

    int totalDocuments = 0;
    int totalBytes = 0;

    TestBundleBuilder(DatabaseId databaseId) {
      this.databaseId = databaseId;
      this.serializer = new RemoteSerializer(databaseId);
    }

    String addDocumentMetadata(String path, long readTimeMicros, boolean exists) {
      SnapshotVersion readTime = version(readTimeMicros);
      String json =
          String.format(
              "{ documentMetadata: {\n"
                  + "name: '%s', readTime: { seconds: %s, nanos: %s }, exists: %s\n"
                  + "} }",
              serializer.encodeKey(key(path)),
              readTime.getTimestamp().getSeconds(),
              readTime.getTimestamp().getNanoseconds(),
              exists);
      this.elements.add(json);
      if (!exists) ++totalDocuments;
      totalBytes += getUTF8BytesCountWithPrefix(json);
      return json;
    }

    String addDocument(
        String path, long createTimeMicros, long updateTimeMicros, String fieldsJson) {
      SnapshotVersion createTime = version(createTimeMicros);
      SnapshotVersion updateTime = version(updateTimeMicros);
      String json =
          String.format(
              "{ document: {\n"
                  + "name: '%s',\n"
                  + "createTime: { seconds: %s, nanos: %s },\n"
                  + "updateTime: { seconds: %s, nanos: %s },\n"
                  + "fields: %s\n"
                  + "} }",
              serializer.encodeKey(key(path)),
              createTime.getTimestamp().getSeconds(),
              createTime.getTimestamp().getNanoseconds(),
              updateTime.getTimestamp().getSeconds(),
              updateTime.getTimestamp().getNanoseconds(),
              fieldsJson);
      elements.add(json);
      ++totalDocuments;
      totalBytes += getUTF8BytesCountWithPrefix(json);
      return json;
    }

    String addNamedQuery(
        String name,
        long readTimeMicros,
        Query.LimitType limitType,
        String parent,
        String structuredQueryJson) {
      SnapshotVersion readTime = version(readTimeMicros);
      String json =
          String.format(
              "{ namedQuery: {\n"
                  + "name: '%s',\n"
                  + "readTime: { seconds: %s, nanos: %s },\n"
                  + "bundledQuery: { parent: '%s', structuredQuery: %s,  limitType: '%s' }\n"
                  + "} }",
              name,
              readTime.getTimestamp().getSeconds(),
              readTime.getTimestamp().getNanoseconds(),
              String.format(
                  "projects/%s/databases/%s/documents/%s",
                  databaseId.getProjectId(), databaseId.getDatabaseId(), parent),
              structuredQueryJson,
              limitType.equals(Query.LimitType.LIMIT_TO_FIRST) ? "FIRST" : "LAST");
      elements.add(json);
      totalBytes += getUTF8BytesCountWithPrefix(json);
      return json;
    }

    private int getUTF8BytesCountWithPrefix(String json) {
      int elementLength = getUTF8BytesCount(json);
      int prefixLength = (int) (Math.log10(elementLength) + 1);
      return prefixLength + elementLength;
    }

    private int getUTF8BytesCount(String json) {
      return json.getBytes(UTF8_CHARSET).length;
    }

    String getMetadataElement(String id, long createTimeMicros, int version) {
      SnapshotVersion createTime = version(createTimeMicros);
      return String.format(
          "{ metadata: {\n"
              + "id: '%s',\n"
              + "createTime: { seconds: %s, nanos: %s },\n"
              + "version: %s,\n"
              + "totalDocuments: %s,\n"
              + "totalBytes: %s\n"
              + "} }",
          id,
          createTime.getTimestamp().getSeconds(),
          createTime.getTimestamp().getNanoseconds(),
          version,
          totalDocuments,
          totalBytes);
    }

    String build(String id, long createTimeMicros, int version) {
      StringBuilder builder = new StringBuilder();

      String metadataElement = getMetadataElement(id, createTimeMicros, version);
      builder.append(metadataElement.length());
      builder.append(metadataElement);

      for (String element : this.elements) {
        builder.append(getUTF8BytesCount(element));
        builder.append(element);
      }

      return builder.toString();
    }
  }
}
