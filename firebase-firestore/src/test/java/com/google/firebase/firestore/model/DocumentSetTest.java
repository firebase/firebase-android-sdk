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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docSet;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firestore.v1.Value;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DocumentSetTest {

  private static final Comparator<Document> TEST_COMPARATOR =
      (left, right) -> {
        Value leftValue = left.getField(field("sort"));
        Value rightValue = right.getField(field("sort"));
        return Values.compare(leftValue, rightValue);
      };

  private static final Document DOC1 = doc("docs/1", 0, map("sort", 2));
  private static final Document DOC2 = doc("docs/2", 0, map("sort", 3));
  private static final Document DOC3 = doc("docs/3", 0, map("sort", 1));

  @Test
  public void testCount() {
    assertEquals(0, docSet(TEST_COMPARATOR).size());
    assertEquals(3, docSet(TEST_COMPARATOR, DOC1, DOC2, DOC3).size());
  }

  @Test
  public void testHasKey() {
    DocumentSet set = docSet(TEST_COMPARATOR, DOC1, DOC2);

    assertTrue(set.contains(DOC1.getKey()));
    assertTrue(set.contains(DOC2.getKey()));
    assertFalse(set.contains(DOC3.getKey()));
  }

  @Test
  public void testDocumentForKey() {
    DocumentSet set = docSet(TEST_COMPARATOR, DOC1, DOC2);

    assertEquals(DOC1, set.getDocument(DOC1.getKey()));
    assertEquals(DOC2, set.getDocument(DOC2.getKey()));
    assertNull(set.getDocument(DOC3.getKey()));
  }

  @Test
  public void testFirstAndLastDocument() {
    DocumentSet emptySet = docSet(TEST_COMPARATOR);

    assertNull(emptySet.getFirstDocument());
    assertNull(emptySet.getLastDocument());

    DocumentSet set = docSet(TEST_COMPARATOR, DOC1, DOC2, DOC3);
    assertEquals(DOC3, set.getFirstDocument());
    assertEquals(DOC2, set.getLastDocument());
  }

  @Test
  public void testKeepsDocumentsInTheRightOrder() {
    DocumentSet set = docSet(TEST_COMPARATOR, DOC1, DOC2, DOC3);
    assertEquals(Arrays.asList(DOC3, DOC1, DOC2), set.toList());
  }

  @Test
  public void testPredecessorDocumentForKey() {
    DocumentSet set = docSet(TEST_COMPARATOR, DOC1, DOC2, DOC3);

    assertNull(set.getPredecessor(DOC3.getKey()));
    assertEquals(DOC3, set.getPredecessor(DOC1.getKey()));
    assertEquals(DOC1, set.getPredecessor(DOC2.getKey()));
  }

  @Test
  public void testDeletes() {
    DocumentSet set = docSet(TEST_COMPARATOR, DOC1, DOC2, DOC3);

    DocumentSet withoutDoc1 = set.remove(DOC1.getKey());
    assertEquals(Arrays.asList(DOC3, DOC2), withoutDoc1.toList());
    assertEquals(2, withoutDoc1.size());

    // Original remains unchanged
    assertEquals(Arrays.asList(DOC3, DOC1, DOC2), set.toList());

    DocumentSet withoutDoc3 = withoutDoc1.remove(DOC3.getKey());
    assertEquals(Collections.singletonList(DOC2), withoutDoc3.toList());
    assertEquals(1, withoutDoc3.size());
  }

  @Test
  public void testUpdates() {
    DocumentSet set = docSet(TEST_COMPARATOR, DOC1, DOC2, DOC3);
    Document doc2Prime = doc("docs/2", 0, map("sort", 9));

    set = set.add(doc2Prime);
    assertEquals(3, set.size());
    assertEquals(doc2Prime, set.getDocument(doc2Prime.getKey()));
    assertEquals(Arrays.asList(DOC3, DOC1, doc2Prime), set.toList());
  }

  @Test
  public void testAddsDocsWithEqualComparisonValues() {
    Document doc1 = doc("docs/1", 0, map("sort", 2));
    Document doc2 = doc("docs/2", 0, map("sort", 2));

    DocumentSet set = docSet(TEST_COMPARATOR, doc1, doc2);
    assertEquals(Arrays.asList(doc1, doc2), set.toList());
  }

  @Test
  public void testIsEqual() {
    DocumentSet set1 = docSet(Document.keyComparator(), DOC1, DOC2, DOC3);
    DocumentSet set2 = docSet(Document.keyComparator(), DOC1, DOC2, DOC3);

    assertEquals(set1, set1);
    assertEquals(set1, set2);
    assertFalse(set1.equals(null));

    DocumentSet sortedSet1 = docSet(TEST_COMPARATOR, DOC1, DOC2, DOC3);
    DocumentSet sortedSet2 = docSet(TEST_COMPARATOR, DOC1, DOC2, DOC3);
    assertEquals(sortedSet1, sortedSet1);
    assertEquals(sortedSet1, sortedSet2);
    assertFalse(sortedSet1.equals(null));

    DocumentSet shortSet = docSet(Document.keyComparator(), DOC1, DOC2);
    assertNotEquals(set1, shortSet);
    assertNotEquals(set1, sortedSet1);
  }
}
