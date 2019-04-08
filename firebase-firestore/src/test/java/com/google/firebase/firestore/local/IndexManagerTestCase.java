// Copyright 2019 Google LLC
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

import static com.google.firebase.firestore.testutil.TestUtil.path;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.model.ResourcePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * These are tests for any implementation of the IndexManager interface.
 *
 * <p>To test a specific implementation of IndexManager:
 *
 * <ol>
 *   <li>Subclass IndexManagerTestCase.
 *   <li>Override {@link #getPersistence}, creating a new implementation of Persistence.
 * </ol>
 */
public abstract class IndexManagerTestCase {
  @Rule public TestName name = new TestName();

  private Persistence persistence;
  private IndexManager indexManager;

  @Before
  public void setUp() {
    persistence = getPersistence();
    indexManager = persistence.getIndexManager();
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  abstract Persistence getPersistence();

  @Test
  public void testCanAddAndReadCollectionParentIndexEntries() {
    indexManager.addToCollectionParentIndex(path("messages"));
    indexManager.addToCollectionParentIndex(path("messages"));
    indexManager.addToCollectionParentIndex(path("rooms/foo/messages"));
    indexManager.addToCollectionParentIndex(path("rooms/bar/messages"));
    indexManager.addToCollectionParentIndex(path("rooms/foo/messages2"));

    assertParents(indexManager, "messages", Arrays.asList("", "rooms/bar", "rooms/foo"));
    assertParents(indexManager, "messages2", Arrays.asList("rooms/foo"));
    assertParents(indexManager, "messages3", Collections.emptyList());
  }

  private void assertParents(
      IndexManager indexManager, String collectionId, List<String> expected) {
    List<ResourcePath> actualPaths = indexManager.getCollectionParents(collectionId);
    List<String> actual = new ArrayList<String>();
    for (ResourcePath actualPath : actualPaths) {
      actual.add(actualPath.toString());
    }
    expected.sort(String::compareTo);
    actual.sort(String::compareTo);
    assertEquals(expected, actual);
  }
}
