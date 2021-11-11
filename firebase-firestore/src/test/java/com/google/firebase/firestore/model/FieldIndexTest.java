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

import static com.google.firebase.firestore.model.FieldIndex.SEMANTIC_COMPARATOR;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FieldIndexTest {

  @Test
  public void equalsIncludesCollectionGroup() {
    FieldIndex indexOriginal = new FieldIndex("collA");
    FieldIndex indexSame = new FieldIndex("collA");
    FieldIndex indexDifferent = new FieldIndex("collB");
    assertEquals(indexOriginal, indexSame);
    assertNotEquals(indexOriginal, indexDifferent);
  }

  @Test
  public void equalsIncludesIndexId() {
    FieldIndex indexOriginal = new FieldIndex("collA").withIndexId(1);
    FieldIndex indexSame = new FieldIndex("collA").withIndexId(1);
    FieldIndex indexDifferent = new FieldIndex("collA").withIndexId(2);
    assertEquals(indexOriginal, indexSame);
    assertNotEquals(indexOriginal, indexDifferent);
  }

  @Test
  public void equalsIncludesUpdateTime() {
    FieldIndex indexOriginal = new FieldIndex("collA").withUpdateTime(version(1));
    FieldIndex indexSame = new FieldIndex("collA").withUpdateTime(version(1));
    FieldIndex indexDifferent = new FieldIndex("collA").withUpdateTime(version(2));
    assertEquals(indexOriginal, indexSame);
    assertNotEquals(indexOriginal, indexDifferent);
  }

  @Test
  public void equalsIncludesFieldName() {
    FieldIndex indexOriginal =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent =
        new FieldIndex("collA").withAddedField(field("b"), FieldIndex.Segment.Kind.ASCENDING);
    assertEquals(indexOriginal, indexSame);
    assertNotEquals(indexOriginal, indexDifferent);
  }

  @Test
  public void equalsIncludesSegmentKind() {
    FieldIndex indexOriginal =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.DESCENDING);
    assertEquals(indexOriginal, indexSame);
    assertNotEquals(indexOriginal, indexDifferent);
  }

  @Test
  public void equalsIncludesFieldLength() {
    FieldIndex indexOriginal =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent =
        new FieldIndex("collA")
            .withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING)
            .withAddedField(field("b"), FieldIndex.Segment.Kind.ASCENDING);
    assertEquals(indexOriginal, indexSame);
    assertNotEquals(indexOriginal, indexDifferent);
  }

  @Test
  public void comparatorIncludesCollectionGroup() {
    FieldIndex indexOriginal = new FieldIndex("collA");
    FieldIndex indexSame = new FieldIndex("collA");
    FieldIndex indexDifferent = new FieldIndex("collB");
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(-1, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIgnoresIndexId() {
    FieldIndex indexOriginal = new FieldIndex("collA").withIndexId(1);
    FieldIndex indexSame = new FieldIndex("collA").withIndexId(1);
    FieldIndex indexDifferent = new FieldIndex("collA").withIndexId(2);
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIgnoreUpdateTime() {
    FieldIndex indexOriginal = new FieldIndex("collA").withUpdateTime(version(1));
    FieldIndex indexSame = new FieldIndex("collA").withUpdateTime(version(1));
    FieldIndex indexDifferent = new FieldIndex("collA").withUpdateTime(version(2));
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIncludesFieldName() {
    FieldIndex indexOriginal =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent =
        new FieldIndex("collA").withAddedField(field("b"), FieldIndex.Segment.Kind.ASCENDING);
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(-1, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIncludesSegmentKind() {
    FieldIndex indexOriginal =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.DESCENDING);
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(-1, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }

  @Test
  public void comparatorIncludesSegmentLength() {
    FieldIndex indexOriginal =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexSame =
        new FieldIndex("collA").withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexDifferent =
        new FieldIndex("collA")
            .withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING)
            .withAddedField(field("b"), FieldIndex.Segment.Kind.ASCENDING);
    assertEquals(0, SEMANTIC_COMPARATOR.compare(indexOriginal, indexSame));
    assertEquals(-1, SEMANTIC_COMPARATOR.compare(indexOriginal, indexDifferent));
  }
}
