// Copyright 2026 Google LLC
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

package com.google.firebase.firestore;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.PipelineTest.entry;
import static com.google.firebase.firestore.PipelineTest.mapOfEntries;
import static com.google.firebase.firestore.pipeline.Expression.constant;
import static com.google.firebase.firestore.pipeline.Expression.field;
import static com.google.firebase.firestore.pipeline.Expression.multiply;
import static com.google.firebase.firestore.pipeline.Ordering.ascending;
import static com.google.firebase.firestore.pipeline.Ordering.descending;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.pipeline.AggregateFunction;
import com.google.firebase.firestore.pipeline.Expression;
import com.google.firebase.firestore.pipeline.WindowBound;
import com.google.firebase.firestore.pipeline.WindowFunction;
import com.google.firebase.firestore.pipeline.WindowSpec;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PipelineWindowTest {

  private FirebaseFirestore firestore;

  @Before
  public void setup() {
    assumeTrue(
        "Skip PipelineWindowTest on standard backend",
        IntegrationTestUtil.getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE);
    firestore = IntegrationTestUtil.testFirestore();
  }

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  /**
   * Shared fixture for the window tests.
   *
   * <p>The sales are deliberately constructed so that:
   *
   * <ul>
   *   <li>{@code product} and {@code region} give overlapping, non-nested partitions,
   *   <li>{@code salesPrice} has ties (30 and 60 both appear twice), which exercises {@code range}
   *       framing and ranking peers, and
   *   <li>{@code date} is strictly increasing and has an 11 day gap before the final sale, which
   *       exercises time based {@code range} framing.
   * </ul>
   *
   * <p>Sorted by {@code date} the document order is always sale1, sale2, sale3, sale4, sale5.
   */
  private static Map<String, Map<String, Object>> windowTestDocs() {
    return mapOfEntries(
        // 2026-07-01T12:00:00Z
        entry(
            "sale1",
            mapOfEntries(
                entry("product", "phone"),
                entry("region", "east"),
                entry("salesPrice", 12),
                entry("quantity", 1),
                entry("date", new Timestamp(1782907200L, 0)))),
        // 2026-07-02T12:00:00Z
        entry(
            "sale2",
            mapOfEntries(
                entry("product", "phone"),
                entry("region", "west"),
                entry("salesPrice", 30),
                entry("quantity", 2),
                entry("date", new Timestamp(1782993600L, 0)))),
        // 2026-07-02T18:00:00Z
        entry(
            "sale3",
            mapOfEntries(
                entry("product", "tablet"),
                entry("region", "east"),
                entry("salesPrice", 30),
                entry("quantity", 3),
                entry("date", new Timestamp(1783015200L, 0)))),
        // 2026-07-04T12:00:00Z
        entry(
            "sale4",
            mapOfEntries(
                entry("product", "tablet"),
                entry("region", "west"),
                entry("salesPrice", 60),
                entry("quantity", 4),
                entry("date", new Timestamp(1783166400L, 0)))),
        // 2026-07-15T12:00:00Z
        entry(
            "sale5",
            mapOfEntries(
                entry("product", "tablet"),
                entry("region", "east"),
                entry("salesPrice", 60),
                entry("quantity", 5),
                entry("date", new Timestamp(1784116800L, 0)))));
  }

  private static CollectionReference windowTestCollection() {
    return IntegrationTestUtil.testCollectionWithDocs(windowTestDocs());
  }

  /**
   * Asserts that {@code results} contains exactly {@code expected}, in order, comparing whole
   * documents.
   */
  private static void expectResults(
      List<PipelineResult> results, List<Map<String, Object>> expected) {
    assertThat(results).hasSize(expected.size());
    for (int i = 0; i < expected.size(); i++) {
      assertThat(results.get(i).getData()).isEqualTo(expected.get(i));
    }
  }

  // Comprehensive window function functional tests are skipped by default because the backend
  // already tests window function semantics, and the default suite exercises the SDK's window
  // function code paths, server errors, and client-side validation. Pass -PrunComprehensiveTests
  // or set FIRESTORE_RUN_COMPREHENSIVE_TESTS=true to run the full matrix.
  private static void assumeComprehensiveTests() {
    assumeTrue(
        "Skipping comprehensive window test; pass -PrunComprehensiveTests or set"
            + " FIRESTORE_RUN_COMPREHENSIVE_TESTS=true to enable.",
        BuildConfig.RUN_COMPREHENSIVE_TESTS);
  }

  // --- default suite -------------------------------------------------------------------------

  @Test
  public void
      testWindowFieldsComputesRunningAggregatesOverADocumentsFramePartitionedByAFieldAndAnExpressionAndSortedByMultipleKeys() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .partition("product", Expression.toUpper("region"))
                    .sort(ascending("date"), descending("salesPrice"))
                    .documents(WindowBound.UNBOUNDED, constant(0)),
                AggregateFunction.sum("salesPrice").alias("stats.runningTotal"),
                AggregateFunction.arrayAgg("quantity").alias("quantities"),
                AggregateFunction.countAll().alias("count"))
            .sort(ascending("date"))
            .select("product", "stats", "quantities", "count")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"),
                entry("stats", mapOfEntries(entry("runningTotal", 12L))),
                entry("quantities", Collections.singletonList(1L)),
                entry("count", 1L)),
            mapOfEntries(
                entry("product", "phone"),
                entry("stats", mapOfEntries(entry("runningTotal", 30L))),
                entry("quantities", Collections.singletonList(2L)),
                entry("count", 1L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("stats", mapOfEntries(entry("runningTotal", 30L))),
                entry("quantities", Collections.singletonList(3L)),
                entry("count", 1L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("stats", mapOfEntries(entry("runningTotal", 60L))),
                entry("quantities", Collections.singletonList(4L)),
                entry("count", 1L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("stats", mapOfEntries(entry("runningTotal", 90L))),
                entry("quantities", Arrays.asList(3L, 5L)),
                entry("count", 2L))));
  }

  @Test
  public void testWindowFieldsCountsDocumentsInATimeUnitRangeFrame() {
    CollectionReference salesCol = windowTestCollection();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(salesCol.getPath())
            .addWindowFields(
                new WindowSpec().sort(ascending("date")).range(12, WindowBound.CURRENT, "hour"),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L))));
  }

  @Test
  public void testWindowFieldsComputesLookAheadAndNearbyAggregatesWithAccumulatorLevelFrames() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().sort(ascending("quantity")),
                AggregateFunction.average("salesPrice")
                    .over(new WindowSpec().documents(-1, 2))
                    .alias("lookAheadAverage"),
                AggregateFunction.sum("salesPrice")
                    .over(new WindowSpec().range(1.5, 0.5))
                    .alias("nearbyTotal"))
            .sort(ascending("date"))
            .select("product", "lookAheadAverage", "nearbyTotal")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"),
                entry("lookAheadAverage", 30.0),
                entry("nearbyTotal", 12L)),
            mapOfEntries(
                entry("product", "phone"),
                entry("lookAheadAverage", 45.0),
                entry("nearbyTotal", 42L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("lookAheadAverage", 60.0),
                entry("nearbyTotal", 60L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("lookAheadAverage", 60.0),
                entry("nearbyTotal", 90L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("lookAheadAverage", null),
                entry("nearbyTotal", 120L))));
  }

  @Test
  public void testWindowFieldsComputesRank() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().sort(ascending("salesPrice")),
                WindowFunction.rank().alias("priceRank"))
            .sort(ascending("date"))
            .select("salesPrice", "priceRank")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("salesPrice", 12L), entry("priceRank", 1L)),
            mapOfEntries(entry("salesPrice", 30L), entry("priceRank", 2L)),
            mapOfEntries(entry("salesPrice", 30L), entry("priceRank", 2L)),
            mapOfEntries(entry("salesPrice", 60L), entry("priceRank", 4L)),
            mapOfEntries(entry("salesPrice", 60L), entry("priceRank", 4L))));
  }

  @Test
  public void testWindowFieldsRejectsARangeFrameWithoutASort() {
    CollectionReference salesCol = windowTestCollection();
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                waitFor(
                    firestore
                        .pipeline()
                        .collection(salesCol)
                        .addWindowFields(
                            new WindowSpec().range(WindowBound.UNBOUNDED, WindowBound.CURRENT),
                            AggregateFunction.count("quantity").alias("windowCount"))
                        .execute()));
    assertThat(exception.getMessage().toLowerCase()).contains("range");
  }

  @Test
  public void testWindowFieldsRejectsDuplicateAliases() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                firestore
                    .pipeline()
                    .collection(firestore.collection("sales"))
                    .addWindowFields(
                        new WindowSpec().partition("product"),
                        AggregateFunction.sum("salesPrice").alias("total"),
                        AggregateFunction.average("salesPrice").alias("total")));
    assertThat(exception.getMessage()).contains("Duplicate alias: 'total'");
  }

  // --- comprehensive (count): partitioning ---------------------------------------------------

  @Test
  public void testWindowFieldsEmptySpecIsASingleGlobalWindow() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec(), AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 5L))));
  }

  @Test
  public void testWindowFieldsOmittedSpecIsASingleGlobalWindow() {
    assumeComprehensiveTests();
    // The no-window overload is sugar for `new WindowSpec()`.
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 5L))));
  }

  @Test
  public void testWindowFieldsPartitionsBySingleFieldName() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L))));
  }

  @Test
  public void testWindowFieldsPartitionsByMultipleFields() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product", "region"),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "region", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("region", "east"), entry("windowCount", 1L)),
            mapOfEntries(
                entry("product", "phone"), entry("region", "west"), entry("windowCount", 1L)),
            mapOfEntries(
                entry("product", "tablet"), entry("region", "east"), entry("windowCount", 2L)),
            mapOfEntries(
                entry("product", "tablet"), entry("region", "west"), entry("windowCount", 1L)),
            mapOfEntries(
                entry("product", "tablet"), entry("region", "east"), entry("windowCount", 2L))));
  }

  @Test
  public void testWindowFieldsPartitionsByExpression() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition(Expression.toUpper("region")),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("region", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("region", "east"), entry("windowCount", 3L)),
            mapOfEntries(entry("region", "west"), entry("windowCount", 2L)),
            mapOfEntries(entry("region", "east"), entry("windowCount", 3L)),
            mapOfEntries(entry("region", "west"), entry("windowCount", 2L)),
            mapOfEntries(entry("region", "east"), entry("windowCount", 3L))));
  }

  // --- comprehensive (count): sorting and default framing ------------------------------------

  @Test
  public void testWindowFieldsRunningCountWithTheDefaultFrame() {
    assumeComprehensiveTests();
    // The implicit default frame when `sort` is specified is `range(UNBOUNDED, CURRENT)`.
    // `quantity` is 1..5 in the same order as `date`, and has no ties, so the running count is
    // 1..5.
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().sort(ascending("quantity")),
                AggregateFunction.count("quantity").alias("runningCount"))
            .sort(ascending("date"))
            .select("product", "runningCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("runningCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("runningCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 4L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 5L))));
  }

  @Test
  public void testWindowFieldsRunningCountWithinEachPartition() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product").sort(ascending("quantity")),
                AggregateFunction.count("quantity").alias("runningCount"))
            .sort(ascending("date"))
            .select("product", "runningCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("runningCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("runningCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 3L))));
  }

  @Test
  public void testWindowFieldsHonorsADescendingSort() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .sort(descending("date"))
                    .documents(WindowBound.UNBOUNDED, WindowBound.CURRENT),
                AggregateFunction.count("quantity").alias("runningCount"))
            .sort(ascending("date"))
            .select("product", "runningCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("runningCount", 5L)),
            mapOfEntries(entry("product", "phone"), entry("runningCount", 4L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 1L))));
  }

  @Test
  public void testWindowFieldsHonorsMultipleSortOrderings() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .sort(ascending("product"), ascending("date"))
                    .documents(WindowBound.UNBOUNDED, WindowBound.CURRENT),
                AggregateFunction.count("quantity").alias("runningCount"))
            .sort(ascending("date"))
            .select("product", "runningCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("runningCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("runningCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 4L)),
            mapOfEntries(entry("product", "tablet"), entry("runningCount", 5L))));
  }

  // --- comprehensive (count): documents framing ----------------------------------------------

  @Test
  public void testWindowFieldsDocumentsUnboundedToCurrent() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .documents(WindowBound.UNBOUNDED, WindowBound.CURRENT)
                    .sort(ascending("date")),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 4L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 5L))));
  }

  @Test
  public void testWindowFieldsDocumentsUnboundedToCurrentExcludesTiedPeers() {
    assumeComprehensiveTests();
    // In a `documents` frame, CURRENT cuts off strictly at the current document's position (ties
    // are not included), so tied `salesPrice` values (30, 30 and 60, 60) receive distinct
    // sequential counts 1..5 rather than peer-grouped counts.
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .documents(WindowBound.UNBOUNDED, WindowBound.CURRENT)
                    .sort(ascending("salesPrice")),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("windowCount"))
            .select("salesPrice", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("salesPrice", 12L), entry("windowCount", 1L)),
            mapOfEntries(entry("salesPrice", 30L), entry("windowCount", 2L)),
            mapOfEntries(entry("salesPrice", 30L), entry("windowCount", 3L)),
            mapOfEntries(entry("salesPrice", 60L), entry("windowCount", 4L)),
            mapOfEntries(entry("salesPrice", 60L), entry("windowCount", 5L))));
  }

  @Test
  public void testWindowFieldsDocumentsUnboundedToUnbounded() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED)
                    .partition("product")
                    .sort(ascending("date")),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L))));
  }

  @Test
  public void testWindowFieldsDocumentsCurrentToCurrent() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .documents(WindowBound.CURRENT, WindowBound.CURRENT)
                    .sort(ascending("date")),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L))));
  }

  // --- comprehensive (count): range framing --------------------------------------------------
  //
  // In a `range` frame, CURRENT is peer-inclusive: it includes all documents whose sort value(s)
  // tie with the current document, making it semantically equivalent to a numeric `0` offset.

  @Test
  public void testWindowFieldsRangeCurrentToCurrentCountsTiedPeers() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .range(WindowBound.CURRENT, WindowBound.CURRENT)
                    .partition("product")
                    .sort(ascending("salesPrice")),
                AggregateFunction.count("quantity").alias("samePriceCount"))
            .sort(ascending("date"))
            .select("product", "salesPrice", "samePriceCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("salesPrice", 12L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "phone"), entry("salesPrice", 30L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "tablet"), entry("salesPrice", 30L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "tablet"), entry("salesPrice", 60L), entry("samePriceCount", 2L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("salesPrice", 60L),
                entry("samePriceCount", 2L))));
  }

  @Test
  public void testWindowFieldsRangeZeroOffsetCountsTiedPeers() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().range(0, 0).partition("product").sort(ascending("salesPrice")),
                AggregateFunction.count("quantity").alias("samePriceCount"))
            .sort(ascending("date"))
            .select("product", "salesPrice", "samePriceCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("salesPrice", 12L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "phone"), entry("salesPrice", 30L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "tablet"), entry("salesPrice", 30L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "tablet"), entry("salesPrice", 60L), entry("samePriceCount", 2L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("salesPrice", 60L),
                entry("samePriceCount", 2L))));
  }

  @Test
  public void testWindowFieldsRangeUnboundedToCurrentIncludesTiedPeers() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .range(WindowBound.UNBOUNDED, WindowBound.CURRENT)
                    .sort(ascending("salesPrice")),
                AggregateFunction.count("quantity").alias("cumulativeCount"))
            .sort(ascending("date"))
            .select("salesPrice", "cumulativeCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("salesPrice", 12L), entry("cumulativeCount", 1L)),
            mapOfEntries(entry("salesPrice", 30L), entry("cumulativeCount", 3L)),
            mapOfEntries(entry("salesPrice", 30L), entry("cumulativeCount", 3L)),
            mapOfEntries(entry("salesPrice", 60L), entry("cumulativeCount", 5L)),
            mapOfEntries(entry("salesPrice", 60L), entry("cumulativeCount", 5L))));
  }

  @Test
  public void testWindowFieldsRangeUnboundedToZeroOffsetIncludesTiedPeers() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().range(WindowBound.UNBOUNDED, 0).sort(ascending("salesPrice")),
                AggregateFunction.count("quantity").alias("cumulativeCount"))
            .sort(ascending("date"))
            .select("salesPrice", "cumulativeCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("salesPrice", 12L), entry("cumulativeCount", 1L)),
            mapOfEntries(entry("salesPrice", 30L), entry("cumulativeCount", 3L)),
            mapOfEntries(entry("salesPrice", 30L), entry("cumulativeCount", 3L)),
            mapOfEntries(entry("salesPrice", 60L), entry("cumulativeCount", 5L)),
            mapOfEntries(entry("salesPrice", 60L), entry("cumulativeCount", 5L))));
  }

  @Test
  public void testWindowFieldsRangeUnboundedToCurrentWithStringSortKey() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .range(WindowBound.UNBOUNDED, WindowBound.CURRENT)
                    .sort(ascending("product")),
                AggregateFunction.count("quantity").alias("cumulativeCount"))
            .sort(ascending("date"))
            .select("product", "cumulativeCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("cumulativeCount", 2L)),
            mapOfEntries(entry("product", "phone"), entry("cumulativeCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("cumulativeCount", 5L)),
            mapOfEntries(entry("product", "tablet"), entry("cumulativeCount", 5L)),
            mapOfEntries(entry("product", "tablet"), entry("cumulativeCount", 5L))));
  }

  @Test
  public void testWindowFieldsRangeUnboundedToCurrentWithMultipleSortOrderings() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .range(WindowBound.UNBOUNDED, WindowBound.CURRENT)
                    .sort(ascending("product"), ascending("region")),
                AggregateFunction.count("quantity").alias("cumulativeCount"))
            .sort(ascending("date"))
            .select("product", "region", "cumulativeCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("region", "east"), entry("cumulativeCount", 1L)),
            mapOfEntries(
                entry("product", "phone"), entry("region", "west"), entry("cumulativeCount", 2L)),
            mapOfEntries(
                entry("product", "tablet"), entry("region", "east"), entry("cumulativeCount", 4L)),
            mapOfEntries(
                entry("product", "tablet"), entry("region", "west"), entry("cumulativeCount", 5L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("region", "east"),
                entry("cumulativeCount", 4L))));
  }

  @Test
  public void testWindowFieldsRangeUnboundedToUnbounded() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .range(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED)
                    .partition("product")
                    .sort(ascending("salesPrice")),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L))));
  }

  @Test
  public void testWindowFieldsAppliesTheTimeUnitToARangeFrameOffset() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().sort(ascending("date")).range(12, WindowBound.CURRENT, "hour"),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L))));
  }

  // --- comprehensive (count): accumulator level framing --------------------------------------

  @Test
  public void testWindowFieldsEvaluatesEachAccumulatorOverItsOwnFrame() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product").sort(ascending("date")),
                AggregateFunction.count("quantity")
                    .over(new WindowSpec().documents(WindowBound.UNBOUNDED, WindowBound.CURRENT))
                    .alias("runningCount"),
                AggregateFunction.count("quantity")
                    .over(new WindowSpec().documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED))
                    .alias("partitionCount"))
            .sort(ascending("date"))
            .select("product", "runningCount", "partitionCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("runningCount", 1L), entry("partitionCount", 2L)),
            mapOfEntries(
                entry("product", "phone"), entry("runningCount", 2L), entry("partitionCount", 2L)),
            mapOfEntries(
                entry("product", "tablet"), entry("runningCount", 1L), entry("partitionCount", 3L)),
            mapOfEntries(
                entry("product", "tablet"), entry("runningCount", 2L), entry("partitionCount", 3L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("runningCount", 3L),
                entry("partitionCount", 3L))));
  }

  @Test
  public void testWindowFieldsSupportsARangeFrameInOver() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product").sort(ascending("salesPrice")),
                AggregateFunction.count("quantity")
                    .over(new WindowSpec().range(0, 0))
                    .alias("samePriceCount"))
            .sort(ascending("date"))
            .select("product", "salesPrice", "samePriceCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("salesPrice", 12L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "phone"), entry("salesPrice", 30L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "tablet"), entry("salesPrice", 30L), entry("samePriceCount", 1L)),
            mapOfEntries(
                entry("product", "tablet"), entry("salesPrice", 60L), entry("samePriceCount", 2L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("salesPrice", 60L),
                entry("samePriceCount", 2L))));
  }

  // --- comprehensive (count): output fields and composition ----------------------------------

  @Test
  public void testWindowFieldsSupportsMultipleOutputFieldsInASingleStage() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.count("quantity").alias("productCount"),
                AggregateFunction.count("quantity").alias("productCountCopy"))
            .sort(ascending("date"))
            .select("product", "productCount", "productCountCopy")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"),
                entry("productCount", 2L),
                entry("productCountCopy", 2L)),
            mapOfEntries(
                entry("product", "phone"),
                entry("productCount", 2L),
                entry("productCountCopy", 2L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("productCount", 3L),
                entry("productCountCopy", 3L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("productCount", 3L),
                entry("productCountCopy", 3L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("productCount", 3L),
                entry("productCountCopy", 3L))));
  }

  @Test
  public void testWindowFieldsSupportsNestedOutputFieldPaths() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.count("quantity").alias("stats.productCount"))
            .sort(ascending("date"))
            .select("product", "stats")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("stats", mapOfEntries(entry("productCount", 2L)))),
            mapOfEntries(
                entry("product", "phone"), entry("stats", mapOfEntries(entry("productCount", 2L)))),
            mapOfEntries(
                entry("product", "tablet"),
                entry("stats", mapOfEntries(entry("productCount", 3L)))),
            mapOfEntries(
                entry("product", "tablet"),
                entry("stats", mapOfEntries(entry("productCount", 3L)))),
            mapOfEntries(
                entry("product", "tablet"),
                entry("stats", mapOfEntries(entry("productCount", 3L))))));
  }

  @Test
  public void testWindowFieldsSupportsChainingMultipleStages() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.count("quantity").alias("productCount"))
            .addWindowFields(
                new WindowSpec().partition("region"),
                AggregateFunction.count("quantity").alias("regionCount"))
            .sort(ascending("date"))
            .select("product", "region", "productCount", "regionCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"),
                entry("region", "east"),
                entry("productCount", 2L),
                entry("regionCount", 3L)),
            mapOfEntries(
                entry("product", "phone"),
                entry("region", "west"),
                entry("productCount", 2L),
                entry("regionCount", 2L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("region", "east"),
                entry("productCount", 3L),
                entry("regionCount", 3L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("region", "west"),
                entry("productCount", 3L),
                entry("regionCount", 2L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("region", "east"),
                entry("productCount", 3L),
                entry("regionCount", 3L))));
  }

  @Test
  public void testWindowFieldsSupportsFilteringOnAWindowFieldInALaterStage() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.count("quantity").alias("productCount"))
            .where(field("productCount").greaterThan(2))
            .sort(ascending("date"))
            .select("product", "productCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "tablet"), entry("productCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("productCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("productCount", 3L))));
  }

  @Test
  public void testWindowFieldsSupportsSortingOnAWindowFieldInALaterStage() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.count("quantity").alias("productCount"))
            .sort(ascending("productCount"), ascending("date"))
            .select("product", "productCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("productCount", 2L)),
            mapOfEntries(entry("product", "phone"), entry("productCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("productCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("productCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("productCount", 3L))));
  }

  // --- comprehensive (count): error handling -------------------------------------------------

  @Test
  public void testWindowFieldsRejectsMixingStageAndAccumulatorFraming() {
    assumeComprehensiveTests();
    CollectionReference salesCol = windowTestCollection();
    assertThrows(
        RuntimeException.class,
        () ->
            waitFor(
                firestore
                    .pipeline()
                    .collection(salesCol)
                    .addWindowFields(
                        new WindowSpec()
                            .sort(ascending("date"))
                            .documents(WindowBound.UNBOUNDED, WindowBound.CURRENT),
                        AggregateFunction.count("quantity")
                            .over(
                                new WindowSpec()
                                    .documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED))
                            .alias("windowCount"))
                    .execute()));
  }

  @Test
  public void
      testWindowFieldsEvaluatesAccumulatorsWithoutOverUsingTheDefaultFrameWhenOthersUseOver() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().sort(ascending("date")),
                AggregateFunction.count("quantity")
                    .over(new WindowSpec().documents(0, 0))
                    .alias("framed"),
                AggregateFunction.count("quantity").alias("unframed"))
            .sort(ascending("date"))
            .select("product", "framed", "unframed")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("framed", 1L), entry("unframed", 1L)),
            mapOfEntries(entry("product", "phone"), entry("framed", 1L), entry("unframed", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("framed", 1L), entry("unframed", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("framed", 1L), entry("unframed", 4L)),
            mapOfEntries(entry("product", "tablet"), entry("framed", 1L), entry("unframed", 5L))));
  }

  @Test
  public void testWindowFieldsRejectsAPartitionSuppliedToAnAccumulatorLevelOver() {
    assumeComprehensiveTests();
    CollectionReference salesCol = windowTestCollection();
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                waitFor(
                    firestore
                        .pipeline()
                        .collection(salesCol)
                        .addWindowFields(
                            new WindowSpec().sort(ascending("quantity")),
                            AggregateFunction.count("quantity")
                                .over(
                                    new WindowSpec()
                                        .partition("product")
                                        .documents(WindowBound.UNBOUNDED, WindowBound.CURRENT))
                                .alias("windowCount"))
                        .execute()));
    assertThat(exception.getMessage().toLowerCase()).contains("unexpected field");
  }

  // --- comprehensive (aggregators): numeric aggregators over a partition ---------------------

  @Test
  public void testWindowFieldsComputesSumAverageMinimumAndMaximum() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.sum("salesPrice").alias("total"),
                AggregateFunction.average("salesPrice").alias("averagePrice"),
                AggregateFunction.minimum("salesPrice").alias("minimumPrice"),
                AggregateFunction.maximum("salesPrice").alias("maximumPrice"))
            .sort(ascending("date"))
            .select("product", "total", "averagePrice", "minimumPrice", "maximumPrice")
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(5);

    assertThat(results.get(0).getData().get("total")).isEqualTo(42L);
    assertThat(results.get(0).getData().get("averagePrice")).isEqualTo(21.0);
    assertThat(results.get(0).getData().get("minimumPrice")).isEqualTo(12L);
    assertThat(results.get(0).getData().get("maximumPrice")).isEqualTo(30L);

    assertThat(results.get(2).getData().get("total")).isEqualTo(150L);
    assertThat(results.get(2).getData().get("averagePrice")).isEqualTo(50.0);
    assertThat(results.get(2).getData().get("minimumPrice")).isEqualTo(30L);
    assertThat(results.get(2).getData().get("maximumPrice")).isEqualTo(60L);
  }

  @Test
  @Ignore("Pending backend support for countIf and countDistinct in addWindowFields")
  public void testWindowFieldsComputesCountIf() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.countIf(field("salesPrice").greaterThan(20))
                    .alias("expensiveCount"))
            .sort(ascending("date"))
            .select("product", "expensiveCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("expensiveCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("expensiveCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("expensiveCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("expensiveCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("expensiveCount", 3L))));
  }

  @Test
  @Ignore("Pending backend support for countIf and countDistinct in addWindowFields")
  public void testWindowFieldsComputesCountDistinct() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.countDistinct("salesPrice").alias("distinctPriceCount"))
            .sort(ascending("date"))
            .select("product", "distinctPriceCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("distinctPriceCount", 2L)),
            mapOfEntries(entry("product", "phone"), entry("distinctPriceCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("distinctPriceCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("distinctPriceCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("distinctPriceCount", 2L))));
  }

  @Test
  public void testWindowFieldsAggregatesOverAComputedExpression() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.sum(multiply(field("salesPrice"), field("quantity")))
                    .alias("totalRevenue"))
            .sort(ascending("date"))
            .select("product", "totalRevenue")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("totalRevenue", 72L)),
            mapOfEntries(entry("product", "phone"), entry("totalRevenue", 72L)),
            mapOfEntries(entry("product", "tablet"), entry("totalRevenue", 630L)),
            mapOfEntries(entry("product", "tablet"), entry("totalRevenue", 630L)),
            mapOfEntries(entry("product", "tablet"), entry("totalRevenue", 630L))));
  }

  // --- comprehensive (aggregators): ordered aggregators --------------------------------------

  @Test
  public void testWindowFieldsComputesFirstAndLastOverThePartition() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED)
                    .partition("product")
                    .sort(ascending("date")),
                AggregateFunction.first("salesPrice").alias("firstPrice"),
                AggregateFunction.last("salesPrice").alias("lastPrice"))
            .sort(ascending("date"))
            .select("product", "firstPrice", "lastPrice")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("firstPrice", 12L), entry("lastPrice", 30L)),
            mapOfEntries(
                entry("product", "phone"), entry("firstPrice", 12L), entry("lastPrice", 30L)),
            mapOfEntries(
                entry("product", "tablet"), entry("firstPrice", 30L), entry("lastPrice", 60L)),
            mapOfEntries(
                entry("product", "tablet"), entry("firstPrice", 30L), entry("lastPrice", 60L)),
            mapOfEntries(
                entry("product", "tablet"), entry("firstPrice", 30L), entry("lastPrice", 60L))));
  }

  @Test
  public void testWindowFieldsComputesArrayAgg() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED)
                    .partition("product")
                    .sort(ascending("date")),
                AggregateFunction.arrayAgg("salesPrice").alias("allPrices"))
            .sort(ascending("date"))
            .select("product", "allPrices")
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(5);

    assertThat(results.get(0).getData().get("allPrices")).isEqualTo(Arrays.asList(12L, 30L));
    assertThat(results.get(2).getData().get("allPrices")).isEqualTo(Arrays.asList(30L, 60L, 60L));
  }

  @Test
  public void testWindowFieldsComputesArrayAggDistinct() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED)
                    .partition("product")
                    .sort(ascending("date")),
                AggregateFunction.arrayAggDistinct("salesPrice").alias("distinctPrices"))
            .sort(ascending("date"))
            .select("product", "distinctPrices")
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(5);

    assertThat((List<?>) results.get(0).getData().get("distinctPrices")).containsExactly(12L, 30L);
    assertThat((List<?>) results.get(2).getData().get("distinctPrices")).containsExactly(30L, 60L);
  }

  // --- comprehensive (aggregators): numeric documents offsets --------------------------------

  @Test
  public void testWindowFieldsComputesARunningTotal() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec()
                    .documents(WindowBound.UNBOUNDED, WindowBound.CURRENT)
                    .sort(ascending("date")),
                AggregateFunction.sum("salesPrice").alias("runningTotal"))
            .sort(ascending("date"))
            .select("product", "runningTotal")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("runningTotal", 12L)),
            mapOfEntries(entry("product", "phone"), entry("runningTotal", 42L)),
            mapOfEntries(entry("product", "tablet"), entry("runningTotal", 72L)),
            mapOfEntries(entry("product", "tablet"), entry("runningTotal", 132L)),
            mapOfEntries(entry("product", "tablet"), entry("runningTotal", 192L))));
  }

  @Test
  public void testWindowFieldsComputesACenteredMovingAverage() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().documents(1, 1).sort(ascending("date")),
                AggregateFunction.average("salesPrice").alias("movingAverage"),
                AggregateFunction.countAll().alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "movingAverage", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(
                entry("product", "phone"), entry("movingAverage", 21.0), entry("windowCount", 2L)),
            mapOfEntries(
                entry("product", "phone"), entry("movingAverage", 24.0), entry("windowCount", 3L)),
            mapOfEntries(
                entry("product", "tablet"), entry("movingAverage", 40.0), entry("windowCount", 3L)),
            mapOfEntries(
                entry("product", "tablet"), entry("movingAverage", 50.0), entry("windowCount", 3L)),
            mapOfEntries(
                entry("product", "tablet"),
                entry("movingAverage", 60.0),
                entry("windowCount", 2L))));
  }

  @Test
  public void testWindowFieldsComputesATrailingSum() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().documents(2, 0).sort(ascending("date")),
                AggregateFunction.sum("salesPrice").alias("trailingTotal"))
            .sort(ascending("date"))
            .select("product", "trailingTotal")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("trailingTotal", 12L)),
            mapOfEntries(entry("product", "phone"), entry("trailingTotal", 42L)),
            mapOfEntries(entry("product", "tablet"), entry("trailingTotal", 72L)),
            mapOfEntries(entry("product", "tablet"), entry("trailingTotal", 120L)),
            mapOfEntries(entry("product", "tablet"), entry("trailingTotal", 150L))));
  }

  @Test
  public void testWindowFieldsComputesALookAheadAverageWithANegativePrecedingBound() {
    assumeComprehensiveTests();
    // Equivalent to "documents between 1 following and 2 following": the window is
    // [index - preceding, index + following].
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().documents(-1, 2).sort(ascending("date")),
                AggregateFunction.average("salesPrice").alias("lookAheadAverage"),
                AggregateFunction.countAll().alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "lookAheadAverage", "windowCount")
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(5);

    assertThat(results.get(0).getData().get("lookAheadAverage")).isEqualTo(30.0);
    assertThat(results.get(0).getData().get("windowCount")).isEqualTo(2L);

    assertThat(results.get(1).getData().get("lookAheadAverage")).isEqualTo(45.0);
    assertThat(results.get(1).getData().get("windowCount")).isEqualTo(2L);

    assertThat(results.get(2).getData().get("lookAheadAverage")).isEqualTo(60.0);
    assertThat(results.get(2).getData().get("windowCount")).isEqualTo(2L);

    assertThat(results.get(3).getData().get("lookAheadAverage")).isEqualTo(60.0);
    assertThat(results.get(3).getData().get("windowCount")).isEqualTo(1L);

    // The last document has an empty window.
    assertThat(results.get(4).getData().get("lookAheadAverage")).isNull();
    assertThat(results.get(4).getData().get("windowCount")).isEqualTo(0L);
  }

  @Test
  public void testWindowFieldsComputesALookBehindAverageWithANegativeFollowingBound() {
    assumeComprehensiveTests();
    // Equivalent to "documents between 2 preceding and 1 preceding": the window is
    // [index - preceding, index + following].
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().documents(2, -1).sort(ascending("date")),
                AggregateFunction.average("salesPrice").alias("lookBehindAverage"),
                AggregateFunction.countAll().alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "lookBehindAverage", "windowCount")
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(5);

    // The first document has an empty window.
    assertThat(results.get(0).getData().get("lookBehindAverage")).isNull();
    assertThat(results.get(0).getData().get("windowCount")).isEqualTo(0L);

    assertThat(results.get(1).getData().get("lookBehindAverage")).isEqualTo(12.0);
    assertThat(results.get(1).getData().get("windowCount")).isEqualTo(1L);

    assertThat(results.get(2).getData().get("lookBehindAverage")).isEqualTo(21.0);
    assertThat(results.get(2).getData().get("windowCount")).isEqualTo(2L);

    assertThat(results.get(3).getData().get("lookBehindAverage")).isEqualTo(30.0);
    assertThat(results.get(3).getData().get("windowCount")).isEqualTo(2L);

    assertThat(results.get(4).getData().get("lookBehindAverage")).isEqualTo(45.0);
    assertThat(results.get(4).getData().get("windowCount")).isEqualTo(2L);
  }

  @Test
  public void testWindowFieldsComputesARangeLookBehindAverageWithANegativeFollowingBound() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().range(WindowBound.UNBOUNDED, -1).sort(ascending("salesPrice")),
                AggregateFunction.average("salesPrice").alias("lookBehindAverage"),
                AggregateFunction.countAll().alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "lookBehindAverage", "windowCount")
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(5);

    assertThat(results.get(0).getData().get("lookBehindAverage")).isNull();
    assertThat(results.get(0).getData().get("windowCount")).isEqualTo(0L);

    assertThat(results.get(1).getData().get("lookBehindAverage")).isEqualTo(12.0);
    assertThat(results.get(1).getData().get("windowCount")).isEqualTo(1L);

    assertThat(results.get(2).getData().get("lookBehindAverage")).isEqualTo(12.0);
    assertThat(results.get(2).getData().get("windowCount")).isEqualTo(1L);

    assertThat(results.get(3).getData().get("lookBehindAverage")).isEqualTo(24.0);
    assertThat(results.get(3).getData().get("windowCount")).isEqualTo(3L);

    assertThat(results.get(4).getData().get("lookBehindAverage")).isEqualTo(24.0);
    assertThat(results.get(4).getData().get("windowCount")).isEqualTo(3L);
  }

  @Test
  public void testWindowFieldsComputesARangeLookAheadAverageWithANegativePrecedingBound() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().range(-1, WindowBound.UNBOUNDED).sort(ascending("salesPrice")),
                AggregateFunction.average("salesPrice").alias("lookAheadAverage"),
                AggregateFunction.countAll().alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "lookAheadAverage", "windowCount")
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(5);

    assertThat(results.get(0).getData().get("lookAheadAverage")).isEqualTo(45.0);
    assertThat(results.get(0).getData().get("windowCount")).isEqualTo(4L);

    assertThat(results.get(1).getData().get("lookAheadAverage")).isEqualTo(60.0);
    assertThat(results.get(1).getData().get("windowCount")).isEqualTo(2L);

    assertThat(results.get(2).getData().get("lookAheadAverage")).isEqualTo(60.0);
    assertThat(results.get(2).getData().get("windowCount")).isEqualTo(2L);

    assertThat(results.get(3).getData().get("lookAheadAverage")).isNull();
    assertThat(results.get(3).getData().get("windowCount")).isEqualTo(0L);

    assertThat(results.get(4).getData().get("lookAheadAverage")).isNull();
    assertThat(results.get(4).getData().get("windowCount")).isEqualTo(0L);
  }

  // --- comprehensive (aggregators): numeric and time range offsets ---------------------------

  @Test
  public void testWindowFieldsComputesAValueBasedRangeWindow() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().range(20, 0).sort(ascending("salesPrice")),
                AggregateFunction.sum("salesPrice").alias("nearbyTotal"))
            .sort(ascending("date"))
            .select("salesPrice", "nearbyTotal")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("salesPrice", 12L), entry("nearbyTotal", 12L)),
            mapOfEntries(entry("salesPrice", 30L), entry("nearbyTotal", 72L)),
            mapOfEntries(entry("salesPrice", 30L), entry("nearbyTotal", 72L)),
            mapOfEntries(entry("salesPrice", 60L), entry("nearbyTotal", 120L)),
            mapOfEntries(entry("salesPrice", 60L), entry("nearbyTotal", 120L))));
  }

  @Test
  public void testWindowFieldsComputesAValueBasedRangeWindowWithDoubleOffsets() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().range(17.5, 0.5).sort(ascending("salesPrice")),
                AggregateFunction.sum("salesPrice").alias("nearbyTotal"))
            .sort(ascending("date"))
            .select("salesPrice", "nearbyTotal")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("salesPrice", 12L), entry("nearbyTotal", 12L)),
            mapOfEntries(entry("salesPrice", 30L), entry("nearbyTotal", 60L)),
            mapOfEntries(entry("salesPrice", 30L), entry("nearbyTotal", 60L)),
            mapOfEntries(entry("salesPrice", 60L), entry("nearbyTotal", 120L)),
            mapOfEntries(entry("salesPrice", 60L), entry("nearbyTotal", 120L))));
  }

  @Test
  public void testWindowFieldsComputesATrailingThreeDayTotal() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().range(3, WindowBound.CURRENT, "day").sort(ascending("date")),
                AggregateFunction.sum("salesPrice").alias("threeDayTotal"))
            .sort(ascending("date"))
            .select("product", "threeDayTotal")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("threeDayTotal", 12L)),
            mapOfEntries(entry("product", "phone"), entry("threeDayTotal", 42L)),
            mapOfEntries(entry("product", "tablet"), entry("threeDayTotal", 72L)),
            mapOfEntries(entry("product", "tablet"), entry("threeDayTotal", 132L)),
            mapOfEntries(entry("product", "tablet"), entry("threeDayTotal", 60L))));
  }

  // --- comprehensive (aggregators): countAll -------------------------------------------------

  @Test
  public void testWindowFieldsCountAllCountsEveryDocumentInTheWindow() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().partition("product"),
                AggregateFunction.countAll().alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L))));
  }

  // --- comprehensive (aggregators): documents framing with numeric offsets -------------------

  @Test
  public void testWindowFieldsDocumentsZeroPrecedingAndZeroFollowing() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().documents(0, 0).sort(ascending("date")),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L))));
  }

  @Test
  public void testWindowFieldsDocumentsZeroPrecedingToUnboundedFollowing() {
    assumeComprehensiveTests();
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(windowTestCollection())
            .addWindowFields(
                new WindowSpec().documents(0, WindowBound.UNBOUNDED).sort(ascending("date")),
                AggregateFunction.count("quantity").alias("windowCount"))
            .sort(ascending("date"))
            .select("product", "windowCount")
            .execute();
    expectResults(
        waitFor(execute).getResults(),
        Arrays.asList(
            mapOfEntries(entry("product", "phone"), entry("windowCount", 5L)),
            mapOfEntries(entry("product", "phone"), entry("windowCount", 4L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 3L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 2L)),
            mapOfEntries(entry("product", "tablet"), entry("windowCount", 1L))));
  }
}
