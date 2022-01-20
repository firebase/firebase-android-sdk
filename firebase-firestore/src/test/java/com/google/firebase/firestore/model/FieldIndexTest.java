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

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.model.FieldIndex.IndexState;
import static com.google.firebase.firestore.model.FieldIndex.SEMANTIC_COMPARATOR;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FieldIndexTest {

  @Test
  public void comparatorIncludesCollectionGroup() {
    FieldIndex indexOriginal = fieldIndex("collA");
    FieldIndex indexSame = fieldIndex("collA");
    FieldIndex indexDifferent = fieldIndex("collB");
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(-1, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIgnoresIndexId() {
    FieldIndex indexOriginal = fieldIndex("collA", 1, FieldIndex.INITIAL_STATE);
    FieldIndex indexSame = fieldIndex("collA", 1, FieldIndex.INITIAL_STATE);
    FieldIndex indexDifferent = fieldIndex("collA", 2, FieldIndex.INITIAL_STATE);
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIgnoresIndexState() {
    FieldIndex indexOriginal = fieldIndex("collA", 1, FieldIndex.INITIAL_STATE);
    FieldIndex indexSame = fieldIndex("collA", 1, FieldIndex.INITIAL_STATE);
    FieldIndex indexDifferent =
        fieldIndex("collA", 1, IndexState.create(1, version(2), DocumentKey.empty(), -1));
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIncludesFieldName() {
    FieldIndex indexOriginal = fieldIndex("collA", "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame = fieldIndex("collA", "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent = fieldIndex("collA", "b", FieldIndex.Segment.Kind.ASCENDING);
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(-1, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIncludesSegmentKind() {
    FieldIndex indexOriginal = fieldIndex("collA", "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame = fieldIndex("collA", "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent = fieldIndex("collA", "a", FieldIndex.Segment.Kind.DESCENDING);
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(-1, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIncludesSegmentsLength() {
    FieldIndex indexOriginal = fieldIndex("collA", "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame = fieldIndex("collA", "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent =
        fieldIndex(
            "collA",
            "a",
            FieldIndex.Segment.Kind.ASCENDING,
            "b",
            FieldIndex.Segment.Kind.ASCENDING);
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(-1, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void indexOffsetComparator() {
    IndexOffset docAOffset = IndexOffset.create(version(1), key("foo/a"), -1);
    IndexOffset docBOffset = IndexOffset.create(version(1), key("foo/b"), -1);
    IndexOffset version1Offset = IndexOffset.create(version(1));
    IndexOffset docCOffset = IndexOffset.create(version(2), key("foo/c"), -1);
    IndexOffset version2Offset = IndexOffset.create(version(2));

    assertEquals(-1, docAOffset.compareTo(docBOffset));
    assertEquals(-1, docAOffset.compareTo(version1Offset));
    assertEquals(-1, version1Offset.compareTo(docCOffset));
    assertEquals(-1, version1Offset.compareTo(version2Offset));
    assertEquals(-1, docCOffset.compareTo(version2Offset));
  }

  @Test
  public void indexOffsetAdvancesSeconds() {
    IndexOffset actualSuccessor = IndexOffset.create(version(1, (int) 1e9 - 1));
    IndexOffset expectedSuccessor = IndexOffset.create(version(2, 0), DocumentKey.empty(), -1);
    assertEquals(expectedSuccessor, actualSuccessor);
  }
}
