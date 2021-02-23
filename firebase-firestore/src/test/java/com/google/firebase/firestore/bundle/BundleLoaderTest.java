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

import static com.google.firebase.firestore.model.DocumentCollections.emptyDocumentMap;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.keySet;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.LoadBundleTaskProgress;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BundleLoaderTest {
  private static final SnapshotVersion CREATE_TIME = new SnapshotVersion(Timestamp.now());

  private final BundleCallback bundleCallback;

  private final Set<DocumentKey> lastDocuments;
  private final Map<String, ImmutableSortedSet<DocumentKey>> lastQueries;
  private final Map<String, BundleMetadata> lastBundles;

  public BundleLoaderTest() {
    lastDocuments = new HashSet<>();
    lastQueries = new HashMap<>();
    lastBundles = new HashMap<>();

    bundleCallback =
        new BundleCallback() {

          @Override
          public ImmutableSortedMap<DocumentKey, Document> applyBundledDocuments(
              ImmutableSortedMap<DocumentKey, MutableDocument> documents, String bundleId) {
            documents.forEach(entry -> lastDocuments.add(entry.getKey()));
            return emptyDocumentMap();
          }

          @Override
          public void saveNamedQuery(
              NamedQuery namedQuery, ImmutableSortedSet<DocumentKey> documentKeys) {
            lastQueries.put(namedQuery.getName(), documentKeys);
          }

          @Override
          public void saveBundle(BundleMetadata bundleMetadata) {
            lastBundles.put(bundleMetadata.getBundleId(), bundleMetadata);
          }
        };
  }

  @Before
  public void before() {
    lastDocuments.clear();
    lastQueries.clear();
    lastBundles.clear();
  }

  @Test
  public void testLoadsDocuments() {
    BundleLoader bundleLoader =
        new BundleLoader(bundleCallback, createMetadata(/* documents= */ 2));

    LoadBundleTaskProgress progress =
        bundleLoader.addElement(
            new BundledDocumentMetadata(
                key("coll/doc1"), CREATE_TIME, /* exists= */ true, Collections.emptyList()),
            1);
    assertNull(progress);

    progress =
        bundleLoader.addElement(new BundleDocument(doc("coll/doc1", 1, map())), /* byteSize= */ 4);
    assertProgress(
        progress,
        /* documentsLoaded= */ 1,
        /* totalDocuments= */ 2,
        /* bytesLoaded= */ 5,
        /* totalBytes= */ 10);

    progress =
        bundleLoader.addElement(
            new BundledDocumentMetadata(
                key("coll/doc2"), CREATE_TIME, /* exists= */ true, Collections.emptyList()),
            1);
    assertNull(progress);

    progress =
        bundleLoader.addElement(new BundleDocument(doc("coll/doc2", 1, map())), /* byteSize= */ 4);
    assertProgress(
        progress,
        /* documentsLoaded= */ 2,
        /* totalDocuments= */ 2,
        /* bytesLoaded= */ 10,
        /* totalBytes= */ 10);
  }

  @Test
  public void testLoadsDeletedDocuments() {
    BundleLoader bundleLoader =
        new BundleLoader(bundleCallback, createMetadata(/* documents= */ 1));

    LoadBundleTaskProgress progress =
        bundleLoader.addElement(
            new BundledDocumentMetadata(
                key("coll/doc1"), CREATE_TIME, /* exists= */ false, Collections.emptyList()),
            10);
    assertProgress(
        progress,
        /* documentsLoaded= */ 1,
        /* totalDocuments= */ 1,
        /* bytesLoaded= */ 10,
        /* totalBytes= */ 10);
  }

  @Test
  public void testAppliesDocumentChanges() {
    BundleLoader bundleLoader =
        new BundleLoader(bundleCallback, createMetadata(/* documents= */ 1));

    bundleLoader.addElement(
        new BundledDocumentMetadata(
            key("coll/doc1"), CREATE_TIME, /* exists= */ true, Collections.emptyList()),
        1);
    bundleLoader.addElement(new BundleDocument(doc("coll/doc1", 1, map())), /* byteSize= */ 9);

    bundleLoader.applyChanges();

    assertEquals(lastDocuments, Collections.singleton(key("coll/doc1")));
    assertEquals(lastBundles.get("bundle-1"), createMetadata(/* documents= */ 1));
  }

  @Test
  public void testAppliesNamedQueries() {
    BundleLoader bundleLoader =
        new BundleLoader(bundleCallback, createMetadata(/* documents= */ 2));

    bundleLoader.addElement(
        new BundledDocumentMetadata(
            key("coll/doc1"),
            CREATE_TIME,
            /* exists= */ false,
            Collections.singletonList("query-1")),
        2);
    bundleLoader.addElement(
        new BundledDocumentMetadata(
            key("coll/doc2"),
            CREATE_TIME,
            /* exists= */ false,
            Collections.singletonList("query-2")),
        2);
    bundleLoader.addElement(
        new NamedQuery(
            "query-1",
            new BundledQuery(query("foo").toTarget(), Query.LimitType.LIMIT_TO_FIRST),
            CREATE_TIME),
        2);
    bundleLoader.addElement(
        new NamedQuery(
            "query-2",
            new BundledQuery(query("foo").toTarget(), Query.LimitType.LIMIT_TO_FIRST),
            CREATE_TIME),
        4);

    bundleLoader.applyChanges();

    assertEquals(lastQueries.get("query-1"), keySet(key("coll/doc1")));
    assertEquals(lastQueries.get("query-2"), keySet(key("coll/doc2")));
  }

  @Test
  public void testVerifiesBundledDocumentMetadataSent() {
    BundleLoader bundleLoader =
        new BundleLoader(bundleCallback, createMetadata(/* documents= */ 1));

    try {
      bundleLoader.addElement(new BundleDocument(doc("coll/doc1", 1, map())), /* byteSize= */ 10);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("The document being added does not match the stored metadata.", e.getMessage());
    }
  }

  @Test
  public void testVerifiesBundledDocumentMetadataMatches() {
    BundleLoader bundleLoader =
        new BundleLoader(bundleCallback, createMetadata(/* documents= */ 1));
    bundleLoader.addElement(
        new BundledDocumentMetadata(
            key("coll/doc1"), CREATE_TIME, /* exists= */ true, Collections.emptyList()),
        1);

    try {
      bundleLoader.addElement(new BundleDocument(doc("coll/do2", 1, map())), /* byteSize= */ 9);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("The document being added does not match the stored metadata.", e.getMessage());
    }
  }

  @Test
  public void testVerifiesDocumentFollowsMetadata() {
    BundleLoader bundleLoader =
        new BundleLoader(bundleCallback, createMetadata(/* documents= */ 0));

    bundleLoader.addElement(
        new BundledDocumentMetadata(
            key("coll/doc1"), CREATE_TIME, /* exists= */ true, Collections.emptyList()),
        10);

    try {
      bundleLoader.applyChanges();
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Bundled documents end with a document metadata element instead of a document.",
          e.getMessage());
    }
  }

  @Test
  public void testVerifiesDocumentCount() {
    BundleLoader bundleLoader =
        new BundleLoader(bundleCallback, createMetadata(/* documents= */ 2));

    bundleLoader.addElement(
        new BundledDocumentMetadata(
            key("coll/doc1"), CREATE_TIME, /* exists= */ false, Collections.emptyList()),
        10);

    try {
      bundleLoader.applyChanges();
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("Expected 2 documents, but loaded 1.", e.getMessage());
    }
  }

  private BundleMetadata createMetadata(int documents) {
    return new BundleMetadata(
        "bundle-1", /* schemaVersion= */ 1, CREATE_TIME, documents, /* totalBytes= */ 10);
  }

  private void assertProgress(
      LoadBundleTaskProgress loadBundleTaskProgress,
      int documentsLoaded,
      int totalDocuments,
      int bytesLoaded,
      int totalBytes) {
    assertEquals(documentsLoaded, loadBundleTaskProgress.getDocumentsLoaded());
    assertEquals(totalDocuments, loadBundleTaskProgress.getTotalDocuments());
    assertEquals(bytesLoaded, loadBundleTaskProgress.getBytesLoaded());
    assertEquals(totalBytes, loadBundleTaskProgress.getTotalBytes());
  }
}
