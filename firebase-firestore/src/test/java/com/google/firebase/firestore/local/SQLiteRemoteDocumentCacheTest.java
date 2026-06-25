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

package com.google.firebase.firestore.local;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.version;

import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class SQLiteRemoteDocumentCacheTest extends RemoteDocumentCacheTestCase {

  private SQLitePersistence db;

  @Override
  Persistence getPersistence() {
    db = PersistenceTestHelpers.createSQLitePersistence();
    return db;
  }

  @Test
  public void testNextDocumentsFromCollectionGroup() {
    addTestDocumentAtPath("a/1");
    addTestDocumentAtPath("a/2");
    addTestDocumentAtPath("b/3");

    Map<DocumentKey, MutableDocument> results =
        remoteDocumentCache.getAll("a", FieldIndex.IndexOffset.NONE, Integer.MAX_VALUE);
    assertThat(results.keySet()).containsExactly(key("a/1"), key("a/2"));
  }

  @Test
  public void testNextDocumentsFromCollectionGroupWithLimit() {
    addTestDocumentAtPath("a/1", /* updateTime= */ 1, /* readTime= */ 11);
    addTestDocumentAtPath("b/2/a/2", /* updateTime= */ 1, /* readTime= */ 12);
    addTestDocumentAtPath("a/3", /* updateTime= */ 1, /* readTime= */ 13);

    Map<DocumentKey, MutableDocument> results =
        remoteDocumentCache.getAll("a", FieldIndex.IndexOffset.NONE, 2);
    assertThat(results.keySet()).containsExactly(key("a/1"), key("b/2/a/2"));
  }

  @Test
  public void testNextDocumentsFromCollectionGroupWithOffset() {
    addTestDocumentAtPath("a/1", /* updateTime= */ 1, /* readTime= */ 11);
    addTestDocumentAtPath("b/2/a/2", /* updateTime= */ 2, /*  readTime= = */ 12);
    addTestDocumentAtPath("a/3", /* updateTime= */ 3, /*  readTime= = */ 13);

    Map<DocumentKey, MutableDocument> results =
        remoteDocumentCache.getAll("a", FieldIndex.IndexOffset.createSuccessor(version(11), -1), 2);
    assertThat(results.keySet()).containsExactly(key("b/2/a/2"), key("a/3"));
  }

  @Test
  public void testNextDocumentsFromNonExistingCollectionGroup() {
    Map<DocumentKey, MutableDocument> results =
        remoteDocumentCache.getAll("a", FieldIndex.IndexOffset.createSuccessor(version(11), -1), 2);
    assertThat(results).isEmpty();
  }

  @Test
  public void testNextDocumentsForLargeCollectionGroup() {
    int size = 999 / SQLiteRemoteDocumentCache.BINDS_PER_STATEMENT + 1;
    for (int i = 0; i < size; ++i) {
      addTestDocumentAtPath("a/" + i + "/b/doc");
    }
    Map<DocumentKey, MutableDocument> results =
        remoteDocumentCache.getAll("b", FieldIndex.IndexOffset.NONE, size);
    assertThat(results).hasSize(size);
  }

  @Test
  public void testGetAllDocumentKeysWithMassiveDocument() {
    int oldLimit = SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT;
    SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT = 100;
    try {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 20; i++) {
        sb.append("1234567890");
      }
      String largeString = sb.toString(); // 200 bytes

      addTestDocumentAtPath("a/1", 1, 11, map("data", largeString));

      Map<DocumentKey, MutableDocument> results =
          remoteDocumentCache.getAll(Collections.singletonList(key("a/1")));

      assertThat(results.keySet()).containsExactly(key("a/1"));
      MutableDocument doc = results.get(key("a/1"));
      assertThat(doc).isNotNull();
      assertThat(
              doc.getData()
                  .get(com.google.firebase.firestore.model.FieldPath.fromSingleSegment("data"))
                  .getStringValue())
          .isEqualTo(largeString);
    } finally {
      SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT = oldLimit;
    }
  }

  @Test
  public void testNextDocumentsFromCollectionGroupWithMassiveDocument() {
    int oldLimit = SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT;
    SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT = 100;
    try {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 20; i++) {
        sb.append("1234567890");
      }
      String largeString = sb.toString(); // 200 bytes

      addTestDocumentAtPath("a/1", 1, 11, map("data", largeString));

      Map<DocumentKey, MutableDocument> results =
          remoteDocumentCache.getAll("a", FieldIndex.IndexOffset.NONE, Integer.MAX_VALUE);

      assertThat(results.keySet()).containsExactly(key("a/1"));
      MutableDocument doc = results.get(key("a/1"));
      assertThat(doc).isNotNull();
      assertThat(
              doc.getData()
                  .get(com.google.firebase.firestore.model.FieldPath.fromSingleSegment("data"))
                  .getStringValue())
          .isEqualTo(largeString);
    } finally {
      SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT = oldLimit;
    }
  }

  @Test
  public void testDocumentJustBelowLimitDoesNotChunk() {
    int oldLimit = SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT;
    SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT = 100;
    try {
      String data = "small_string"; // Definitely less than 100 bytes

      addTestDocumentAtPath("a/1", 1, 11, map("data", data));

      Map<DocumentKey, MutableDocument> results =
          remoteDocumentCache.getAll(Collections.singletonList(key("a/1")));

      assertThat(results.keySet()).containsExactly(key("a/1"));
      assertThat(
              results
                  .get(key("a/1"))
                  .getData()
                  .get(com.google.firebase.firestore.model.FieldPath.fromSingleSegment("data"))
                  .getStringValue())
          .isEqualTo(data);
    } finally {
      SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT = oldLimit;
    }
  }

  @Test
  public void testDocumentJustAboveLimitChunks() {
    int oldLimit = SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT;
    SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT = 100;
    try {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 15; i++) {
        sb.append("1234567890");
      }
      String data = sb.toString(); // 150 bytes, definitely > 100

      addTestDocumentAtPath("a/1", 1, 11, map("data", data));

      Map<DocumentKey, MutableDocument> results =
          remoteDocumentCache.getAll(Collections.singletonList(key("a/1")));

      assertThat(results.keySet()).containsExactly(key("a/1"));
      assertThat(
              results
                  .get(key("a/1"))
                  .getData()
                  .get(com.google.firebase.firestore.model.FieldPath.fromSingleSegment("data"))
                  .getStringValue())
          .isEqualTo(data);
    } finally {
      SQLiteRemoteDocumentCache.SAFE_CURSOR_LIMIT = oldLimit;
    }
  }

  @Test
  public void testFetchMassiveDocumentInChunksThrowsWhenChunkMissing() {
    SQLiteRemoteDocumentCache cache = (SQLiteRemoteDocumentCache) remoteDocumentCache;

    try {
      cache.fetchMassiveDocumentInChunks(db, "non-existent/path", 1000, 100);
      org.junit.Assert.fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("Failed to fetch chunk for massive document");
    }
  }
}
