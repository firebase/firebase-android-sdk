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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * These are tests for any implementation of the BundleCache interface.
 *
 * <p>To test a specific implementation of BundleCache:
 *
 * <ol>
 *   <li>Subclass BundleCacheTestCase.
 *   <li>Override {@link #getPersistence}, creating a new implementation of Persistence.
 * </ol>
 */
public abstract class BundleCacheTestCase {
  @Rule public TestName name = new TestName();

  private Persistence persistence;
  private BundleCache bundleCache;

  @Before
  public void setUp() {
    persistence = getPersistence();
    bundleCache = persistence.getBundleCache();
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  @Test
  public void testReturnsNullWhenBundleIdNotFound() {
    assertNull(bundleCache.getBundleMetadata("bundle-1"));
  }

  @Test
  public void testReturnsSavedBundle() {
    BundleMetadata expectedBundleMetadata =
        new BundleMetadata("bundle-1", 1, new SnapshotVersion(Timestamp.now()));
    bundleCache.saveBundleMetadata(expectedBundleMetadata);
    BundleMetadata actualBundleMetadata = bundleCache.getBundleMetadata("bundle-1");

    assertEquals(expectedBundleMetadata, actualBundleMetadata);

    // Overwrite
    expectedBundleMetadata =
        new BundleMetadata("bundle-1", 2, new SnapshotVersion(Timestamp.now()));
    bundleCache.saveBundleMetadata(expectedBundleMetadata);
    actualBundleMetadata = bundleCache.getBundleMetadata("bundle-1");

    assertEquals(expectedBundleMetadata, actualBundleMetadata);
  }

  @Test
  public void testReturnsNullWhenQueryNameIsNotFound() {
    assertNull(bundleCache.getNamedQuery("query-1"));
  }

  @Test
  public void testReturnsSavedCollectionQueries() {
    NamedQuery expectedQuery =
        new NamedQuery("query-1", query("collection"), new SnapshotVersion(Timestamp.now()));
    bundleCache.saveNamedQuery(expectedQuery);

    NamedQuery actualQuery = bundleCache.getNamedQuery("query-1");
    assertEquals(expectedQuery, actualQuery);
  }

  @Test
  public void testReturnsSavedLimitToFirstQueries() {
    NamedQuery expectedQuery =
            new NamedQuery(
                    "query-1", query("collection").limitToFirst(1), new SnapshotVersion(Timestamp.now()));
    bundleCache.saveNamedQuery(expectedQuery);

    NamedQuery actualQuery = bundleCache.getNamedQuery("query-1");
    assertEquals(expectedQuery, actualQuery);
  }

  @Test
  public void testReturnsSavedLimitToLastQueries() {
    NamedQuery expectedQuery =
        new NamedQuery(
            "query-1", query("collection").limitToLast(1), new SnapshotVersion(Timestamp.now()));
    bundleCache.saveNamedQuery(expectedQuery);

    NamedQuery actualQuery = bundleCache.getNamedQuery("query-1");
    assertEquals(expectedQuery, actualQuery);
  }

  @Test
  public void testReturnsSavedCollectionGroupQueries() {
    Query collectionGroupQuery = new Query(ResourcePath.EMPTY, "collection");
    NamedQuery expectedQuery =
        new NamedQuery("query-1", collectionGroupQuery, new SnapshotVersion(Timestamp.now()));
    bundleCache.saveNamedQuery(expectedQuery);

    NamedQuery actualQuery = bundleCache.getNamedQuery("query-1");
    assertEquals(expectedQuery, actualQuery);
  }
}
