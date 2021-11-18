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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;

import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.Arrays;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class IndexingEnabledSQLiteLocalStoreTest extends SQLiteLocalStoreTest {
  /** Current state of indexing support. Used for restoring after test run. */
  private static final boolean supportsIndexing = Persistence.INDEXING_SUPPORT_ENABLED;

  @BeforeClass
  public static void beforeClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = true;
  }

  @BeforeClass
  public static void afterClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = supportsIndexing;
  }

  @Test
  public void testConfiguresIndexes() {
    FieldIndex indexA =
        fieldIndex("coll", 0, SnapshotVersion.NONE, "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexB =
        fieldIndex("coll", 1, SnapshotVersion.NONE, "b", FieldIndex.Segment.Kind.DESCENDING);
    FieldIndex indexC =
        fieldIndex(
            "coll",
            2,
            SnapshotVersion.NONE,
            "c1",
            FieldIndex.Segment.Kind.ASCENDING,
            "c2",
            FieldIndex.Segment.Kind.CONTAINS);

    configureFieldIndexes(Arrays.asList(indexA, indexB));
    Collection<FieldIndex> fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA, indexB);

    configureFieldIndexes(Arrays.asList(indexA, indexC));
    fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA, indexC);
  }
}
