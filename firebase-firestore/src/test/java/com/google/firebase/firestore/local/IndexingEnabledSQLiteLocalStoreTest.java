package com.google.firebase.firestore.local;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.field;

import com.google.firebase.firestore.model.FieldIndex;
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
        new FieldIndex("coll")
            .withIndexId(0)
            .withAddedField(field("a"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexB =
        new FieldIndex("coll")
            .withIndexId(1)
            .withAddedField(field("b"), FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexC =
        new FieldIndex("coll")
            .withIndexId(2)
            .withAddedField(field("c"), FieldIndex.Segment.Kind.ASCENDING);

    configureFieldIndexes(Arrays.asList(indexA, indexB));
    Collection<FieldIndex> fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA, indexB);

    configureFieldIndexes(Arrays.asList(indexA, indexC));
    fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA, indexC);
  }
}
