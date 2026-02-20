// Copyright 2025 Google LLC
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
import static com.google.firebase.firestore.pipeline.Expression.add;
import static com.google.firebase.firestore.pipeline.Expression.and;
import static com.google.firebase.firestore.pipeline.Expression.array;
import static com.google.firebase.firestore.pipeline.Expression.arrayContains;
import static com.google.firebase.firestore.pipeline.Expression.arrayContainsAny;
import static com.google.firebase.firestore.pipeline.Expression.collectionId;
import static com.google.firebase.firestore.pipeline.Expression.concat;
import static com.google.firebase.firestore.pipeline.Expression.constant;
import static com.google.firebase.firestore.pipeline.Expression.cosineDistance;
import static com.google.firebase.firestore.pipeline.Expression.currentTimestamp;
import static com.google.firebase.firestore.pipeline.Expression.documentId;
import static com.google.firebase.firestore.pipeline.Expression.endsWith;
import static com.google.firebase.firestore.pipeline.Expression.equal;
import static com.google.firebase.firestore.pipeline.Expression.euclideanDistance;
import static com.google.firebase.firestore.pipeline.Expression.exists;
import static com.google.firebase.firestore.pipeline.Expression.field;
import static com.google.firebase.firestore.pipeline.Expression.greaterThan;
import static com.google.firebase.firestore.pipeline.Expression.join;
import static com.google.firebase.firestore.pipeline.Expression.length;
import static com.google.firebase.firestore.pipeline.Expression.lessThan;
import static com.google.firebase.firestore.pipeline.Expression.logicalMaximum;
import static com.google.firebase.firestore.pipeline.Expression.logicalMinimum;
import static com.google.firebase.firestore.pipeline.Expression.map;
import static com.google.firebase.firestore.pipeline.Expression.mapGet;
import static com.google.firebase.firestore.pipeline.Expression.not;
import static com.google.firebase.firestore.pipeline.Expression.notEqual;
import static com.google.firebase.firestore.pipeline.Expression.nullValue;
import static com.google.firebase.firestore.pipeline.Expression.or;
import static com.google.firebase.firestore.pipeline.Expression.split;
import static com.google.firebase.firestore.pipeline.Expression.startsWith;
import static com.google.firebase.firestore.pipeline.Expression.stringConcat;
import static com.google.firebase.firestore.pipeline.Expression.subtract;
import static com.google.firebase.firestore.pipeline.Expression.vector;
import static com.google.firebase.firestore.pipeline.Ordering.ascending;
import static com.google.firebase.firestore.pipeline.Ordering.descending;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.isRunningAgainstEmulator;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.truth.Correspondence;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.pipeline.AggregateFunction;
import com.google.firebase.firestore.pipeline.AggregateHints;
import com.google.firebase.firestore.pipeline.AggregateOptions;
import com.google.firebase.firestore.pipeline.AggregateStage;
import com.google.firebase.firestore.pipeline.CollectionHints;
import com.google.firebase.firestore.pipeline.CollectionSourceOptions;
import com.google.firebase.firestore.pipeline.Expression;
import com.google.firebase.firestore.pipeline.Field;
import com.google.firebase.firestore.pipeline.FindNearestOptions;
import com.google.firebase.firestore.pipeline.FindNearestStage;
import com.google.firebase.firestore.pipeline.RawStage;
import com.google.firebase.firestore.pipeline.UnnestOptions;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PipelineTest {

  private static final Correspondence<PipelineResult, Map<String, Object>> DATA_CORRESPONDENCE =
      Correspondence.from(
          (result, expected) -> {
            assertThat(result.getData())
                .comparingValuesUsing(
                    Correspondence.from(
                        (x, y) -> {
                          if (x instanceof Long && y instanceof Integer) {
                            return (long) x == (long) (int) y;
                          }
                          if (x instanceof Double && y instanceof Integer) {
                            return (double) x == (double) (int) y;
                          }
                          return Objects.equals(x, y);
                        },
                        "MapValueCompare"))
                .containsExactlyEntriesIn(expected);
            return true;
          },
          "GetData");

  private static final Correspondence<PipelineResult, String> ID_CORRESPONDENCE =
      Correspondence.transforming(x -> x.getRef().getId(), "GetRefId");

  private CollectionReference randomCol;
  private FirebaseFirestore firestore;

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  private final Map<String, Map<String, Object>> bookDocs =
      mapOfEntries(
          entry(
              "book1",
              mapOfEntries(
                  entry("title", "The Hitchhiker's Guide to the Galaxy"),
                  entry("author", "Douglas Adams"),
                  entry("genre", "Science Fiction"),
                  entry("published", 1979),
                  entry("rating", 4.2),
                  entry("tags", ImmutableList.of("comedy", "space", "adventure")),
                  entry("awards", ImmutableMap.of("hugo", true, "nebula", false)),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {10.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0})),
                  entry(
                      "nestedField",
                      ImmutableMap.of("level.1", ImmutableMap.of("level.2", true))))),
          entry(
              "book2",
              mapOfEntries(
                  entry("title", "Pride and Prejudice"),
                  entry("author", "Jane Austen"),
                  entry("genre", "Romance"),
                  entry("published", 1813),
                  entry("rating", 4.5),
                  entry("tags", ImmutableList.of("classic", "social commentary", "love")),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 10.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0})),
                  entry("awards", ImmutableMap.of("none", true)))),
          entry(
              "book3",
              mapOfEntries(
                  entry("title", "One Hundred Years of Solitude"),
                  entry("author", "Gabriel García Márquez"),
                  entry("genre", "Magical Realism"),
                  entry("published", 1967),
                  entry("rating", 4.3),
                  entry("tags", ImmutableList.of("family", "history", "fantasy")),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 1.0, 10.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0})),
                  entry("awards", ImmutableMap.of("nobel", true, "nebula", false)))),
          entry(
              "book4",
              mapOfEntries(
                  entry("title", "The Lord of the Rings"),
                  entry("author", "J.R.R. Tolkien"),
                  entry("genre", "Fantasy"),
                  entry("published", 1954),
                  entry("rating", 4.7),
                  entry("tags", ImmutableList.of("adventure", "magic", "epic")),
                  entry("sales", ImmutableList.of(100, 200, 50)),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0})),
                  entry("awards", ImmutableMap.of("hugo", false, "nebula", false)))),
          entry(
              "book5",
              mapOfEntries(
                  entry("title", "The Handmaid's Tale"),
                  entry("author", "Margaret Atwood"),
                  entry("genre", "Dystopian"),
                  entry("published", 1985),
                  entry("rating", 4.1),
                  entry("tags", ImmutableList.of("feminism", "totalitarianism", "resistance")),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0, 1.0, 1.0})),
                  entry(
                      "awards", ImmutableMap.of("arthur c. clarke", true, "booker prize", false)))),
          entry(
              "book6",
              mapOfEntries(
                  entry("title", "Crime and Punishment"),
                  entry("author", "Fyodor Dostoevsky"),
                  entry("genre", "Psychological Thriller"),
                  entry("published", 1866),
                  entry("rating", 4.3),
                  entry("tags", ImmutableList.of("philosophy", "crime", "redemption")),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0, 1.0})),
                  entry("awards", ImmutableMap.of("none", true)))),
          entry(
              "book7",
              mapOfEntries(
                  entry("title", "To Kill a Mockingbird"),
                  entry("author", "Harper Lee"),
                  entry("genre", "Southern Gothic"),
                  entry("published", 1960),
                  entry("rating", 4.2),
                  entry("tags", ImmutableList.of("racism", "injustice", "coming-of-age")),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 1.0, 1.0})),
                  entry("awards", ImmutableMap.of("pulitzer", true)))),
          entry(
              "book8",
              mapOfEntries(
                  entry("title", "1984"),
                  entry("author", "George Orwell"),
                  entry("genre", "Dystopian"),
                  entry("published", 1949),
                  entry("rating", 4.2),
                  entry("tags", ImmutableList.of("surveillance", "totalitarianism", "propaganda")),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0, 1.0})),
                  entry("awards", ImmutableMap.of("prometheus", true)))),
          entry(
              "book9",
              mapOfEntries(
                  entry("title", "The Great Gatsby"),
                  entry("author", "F. Scott Fitzgerald"),
                  entry("genre", "Modernist"),
                  entry("published", 1925),
                  entry("rating", 4.0),
                  entry("tags", ImmutableList.of("wealth", "american dream", "love")),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0, 1.0})),
                  entry("awards", ImmutableMap.of("none", true)))),
          entry(
              "book10",
              mapOfEntries(
                  entry("title", "Dune"),
                  entry("author", "Frank Herbert"),
                  entry("genre", "Science Fiction"),
                  entry("published", 1965),
                  entry("rating", 4.6),
                  entry("tags", ImmutableList.of("politics", "desert", "ecology")),
                  entry(
                      "embedding",
                      FieldValue.vector(
                          new double[] {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 10.0})),
                  entry("awards", ImmutableMap.of("hugo", true, "nebula", true)))),
          entry(
              "book11",
              mapOfEntries(
                  entry("title", "Timestamp Book"),
                  entry("author", "Timestamp Author"),
                  entry("timestamp", new Date()))));

  @Before
  public void setup() {
    assumeTrue(
        "Skip PipelineTest on standard backend",
        IntegrationTestUtil.getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE);
    randomCol = IntegrationTestUtil.testCollectionWithDocs(bookDocs);
    firestore = randomCol.firestore;
  }

  @Test
  public void emptyResults() {
    Task<Pipeline.Snapshot> execute =
        firestore.pipeline().collection(randomCol.getPath()).limit(0).execute();
    assertThat(waitFor(execute).getResults()).isEmpty();
  }

  @Test
  public void fullResults() {
    Task<Pipeline.Snapshot> execute =
        firestore.pipeline().collection(randomCol.getPath()).execute();
    assertThat(waitFor(execute).getResults()).hasSize(11);
  }

  @Test
  public void aggregateResultsCountAll() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .aggregate(AggregateFunction.countAll().alias("count"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("count", 11));
  }

  @Test
  @Ignore("Not supported yet")
  public void aggregateResultsMany() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("genre", "Science Fiction"))
            .aggregate(
                AggregateFunction.countAll().alias("count"),
                AggregateFunction.average("rating").alias("avgRating"),
                field("rating").maximum().alias("maxRating"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(entry("count", 10), entry("avgRating", 4.4), entry("maxRating", 4.6)));
  }

  @Test
  public void groupAndAccumulateResults() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expression.lessThan(field("published"), 1984))
            .aggregate(
                AggregateStage.withAccumulators(
                        AggregateFunction.average("rating").alias("avgRating"))
                    .withGroups("genre"))
            .where(greaterThan("avgRating", 4.3))
            .sort(field("avgRating").descending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(entry("avgRating", 4.7), entry("genre", "Fantasy")),
            mapOfEntries(entry("avgRating", 4.5), entry("genre", "Romance")),
            mapOfEntries(entry("avgRating", 4.4), entry("genre", "Science Fiction")));
  }

  @Test
  public void groupAndAccumulateResultsGeneric() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .rawStage(
                RawStage.ofName("where")
                    .withArguments(Expression.lessThan(field("published"), 1984)))
            .rawStage(
                RawStage.ofName("aggregate")
                    .withArguments(
                        ImmutableMap.of("avgRating", AggregateFunction.average("rating")),
                        ImmutableMap.of("genre", field("genre"))))
            .rawStage(RawStage.ofName("where").withArguments(greaterThan("avgRating", 4.3)))
            .rawStage(RawStage.ofName("sort").withArguments(field("avgRating").descending()))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(entry("avgRating", 4.7), entry("genre", "Fantasy")),
            mapOfEntries(entry("avgRating", 4.5), entry("genre", "Romance")),
            mapOfEntries(entry("avgRating", 4.4), entry("genre", "Science Fiction")));
  }

  @Test
  public void minAndMaxAccumulations() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .aggregate(
                AggregateFunction.countAll().alias("count"),
                field("rating").maximum().alias("maxRating"),
                field("published").minimum().alias("minPublished"))
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results)
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(entry("count", 11), entry("maxRating", 4.7), entry("minPublished", 1813)));
  }

  @Test
  public void canSelectFields() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select("title", "author")
            .sort(field("author").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("title", "The Hitchhiker's Guide to the Galaxy"),
                entry("author", "Douglas Adams")),
            mapOfEntries(
                entry("title", "The Great Gatsby"), entry("author", "F. Scott Fitzgerald")),
            mapOfEntries(entry("title", "Dune"), entry("author", "Frank Herbert")),
            mapOfEntries(
                entry("title", "Crime and Punishment"), entry("author", "Fyodor Dostoevsky")),
            mapOfEntries(
                entry("title", "One Hundred Years of Solitude"),
                entry("author", "Gabriel García Márquez")),
            mapOfEntries(entry("title", "1984"), entry("author", "George Orwell")),
            mapOfEntries(entry("title", "To Kill a Mockingbird"), entry("author", "Harper Lee")),
            mapOfEntries(
                entry("title", "The Lord of the Rings"), entry("author", "J.R.R. Tolkien")),
            mapOfEntries(entry("title", "Pride and Prejudice"), entry("author", "Jane Austen")),
            mapOfEntries(entry("title", "The Handmaid's Tale"), entry("author", "Margaret Atwood")),
            mapOfEntries(entry("title", "Timestamp Book"), entry("author", "Timestamp Author")))
        .inOrder();
  }

  @Test
  public void whereWithAnd() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(and(greaterThan("rating", 4.5), equal("genre", "Science Fiction")))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(ID_CORRESPONDENCE)
        .containsExactly("book10");
  }

  @Test
  public void whereWithOr() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(or(equal("genre", "Romance"), equal("genre", "Dystopian")))
            .select("title")
            .sort(field("title").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "1984"),
            ImmutableMap.of("title", "Pride and Prejudice"),
            ImmutableMap.of("title", "The Handmaid's Tale"));
  }

  @Test
  public void offsetAndLimits() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(ascending("author"))
            .offset(5)
            .limit(3)
            .select("title", "author")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(entry("title", "1984"), entry("author", "George Orwell")),
            mapOfEntries(entry("title", "To Kill a Mockingbird"), entry("author", "Harper Lee")),
            mapOfEntries(
                entry("title", "The Lord of the Rings"), entry("author", "J.R.R. Tolkien")));
  }

  @Test
  public void arrayContainsWorks() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(arrayContains("tags", "comedy"))
            .select("title")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void arrayContainsAnyWorks() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(arrayContainsAny("tags", ImmutableList.of("comedy", "classic")))
            .select("title")
            .sort(field("title").descending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"),
            ImmutableMap.of("title", "Pride and Prejudice"));
  }

  @Test
  public void arrayContainsAllWorks() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("tags").arrayContainsAll(ImmutableList.of("adventure", "magic")))
            .select("title")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("title", "The Lord of the Rings"));
  }

  @Test
  public void arrayLengthWorks() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(field("tags").arrayLength().alias("tagsCount"))
            .where(equal("tagsCount", 3))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(10);
  }

  @Test
  public void arrayFirstWorks() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
                .where(equal("title", "The Lord of the Rings"))
                .select(field("tags").arrayFirst().alias("firstTag"))
            .execute();
    assertThat(waitFor(execute).getResults())
            .comparingElementsUsing(DATA_CORRESPONDENCE)
            .containsExactly(ImmutableMap.of("firstTag", "adventure"));
  }

  @Test
  @Ignore("Not supported yet")
  public void arrayConcatWorks() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                field("tags")
                    .arrayConcat(ImmutableList.of("newTag1", "newTag2"))
                    .alias("modifiedTags"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "modifiedTags",
                ImmutableList.of("comedy", "space", "adventure", "newTag1", "newTag2")));
  }

  @Test
  public void arraySumWorks() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Lord of the Rings"))
            .select(Expression.arraySum("sales").alias("totalSales"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("totalSales", 350));
  }

  @Test
  public void testConcat() {
    // String concat
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(concat(field("author"), " ", field("title")).alias("author_title"))
            .execute();
    Map<String, Object> result = waitFor(execute).getResults().get(0).getData();
    assertThat(result.get("author_title"))
        .isEqualTo("Douglas Adams The Hitchhiker's Guide to the Galaxy");

    // Array concat
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(concat(field("tags"), ImmutableList.of("newTag")).alias("new_tags"))
            .execute();
    result = waitFor(execute).getResults().get(0).getData();
    assertThat((List<Object>) result.get("new_tags"))
        .containsExactly("comedy", "space", "adventure", "newTag")
        .inOrder();

    // Blob concat
    byte[] bytes1 = new byte[] {1, 2};
    byte[] bytes2 = new byte[] {3, 4};
    byte[] expected = new byte[] {1, 2, 3, 4};
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .limit(1)
            .select(
                concat(constant(Blob.fromBytes(bytes1)), Blob.fromBytes(bytes2))
                    .alias("concatenated_blob"))
            .execute();
    result = waitFor(execute).getResults().get(0).getData();
    assertThat(((Blob) result.get("concatenated_blob")).toBytes()).isEqualTo(expected);

    // Mismatched types should fail
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(concat(field("title"), field("tags")).alias("mismatched"))
            .execute();
    assertThat(waitForException(execute)).isNotNull();
  }

  @Test
  public void testStrConcat() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(ascending(Field.DOCUMENT_ID))
            .select(
                stringConcat(field("author"), constant(" - "), field("title")).alias("bookInfo"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("bookInfo", "Douglas Adams - The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testStartsWith() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(startsWith("title", "The"))
            .select("title")
            .sort(field("title").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "The Great Gatsby"),
            ImmutableMap.of("title", "The Handmaid's Tale"),
            ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"),
            ImmutableMap.of("title", "The Lord of the Rings"));
  }

  @Test
  public void testEndsWith() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(endsWith("title", "y"))
            .select("title")
            .sort(field("title").descending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"),
            ImmutableMap.of("title", "The Great Gatsby"));
  }

  @Test
  public void testLength() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(field("title").charLength().alias("titleLength"), field("title"))
            .where(greaterThan("titleLength", 20))
            .sort(field("title").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("titleLength", 29, "title", "One Hundred Years of Solitude"),
            ImmutableMap.of("titleLength", 36, "title", "The Hitchhiker's Guide to the Galaxy"),
            ImmutableMap.of("titleLength", 21, "title", "The Lord of the Rings"),
            ImmutableMap.of("titleLength", 21, "title", "To Kill a Mockingbird"));
  }

  @Test
  public void canComputeTheLengthOfStringValue() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .limit(1)
            .select(field("title").length().alias("titleLength"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("titleLength", 36));
  }

  @Test
  public void canComputeTheLengthOfStringValueWithTheTopLevelFunction() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .limit(1)
            .select(length("title").alias("titleLength"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("titleLength", 36));
  }

  @Test
  public void canComputeTheLengthOfArrayValue() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .limit(1)
            .select(field("tags").length().alias("tagsLength"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("tagsLength", 3));
  }

  @Test
  public void canComputeTheLengthOfArrayValueWithTheTopLevelFunction() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .limit(1)
            .select(length("tags").alias("tagsLength"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("tagsLength", 3));
  }

  @Test
  public void canComputeTheLengthOfMapValue() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .limit(1)
            .select(field("awards").length().alias("awardsLength"))
            .execute();
    // The "awards" map for this book is {"hugo": true, "nebula": false}, which has a length of 2.
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("awardsLength", 2));
  }

  @Test
  public void canComputeTheLengthOfVectorValue() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .limit(1)
            .select(field("embedding").length().alias("embeddingLength"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("embeddingLength", 10));
  }

  @Test
  public void testToLowercase() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(Field.DOCUMENT_ID.ascending())
            .select(field("title").toLower().alias("lowercaseTitle"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("lowercaseTitle", "the hitchhiker's guide to the galaxy"));
  }

  @Test
  public void testToUppercase() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(Field.DOCUMENT_ID.ascending())
            .select(field("author").toUpper().alias("uppercaseAuthor"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("uppercaseAuthor", "DOUGLAS ADAMS"));
  }

  @Test
  public void testTrim() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(field(FieldPath.documentId()).ascending())
            .limit(1)
            .addFields(
                Expression.stringConcat(constant("  "), field("title"), " \t ")
                    .alias("spacedTitle"))
            .select("spacedTitle", field("spacedTitle").trim().alias("trimmedTitle"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "spacedTitle",
                "  The Hitchhiker's Guide to the Galaxy \t ",
                "trimmedTitle",
                "The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testTrimWithCharacters() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(field(FieldPath.documentId()).ascending())
            .limit(1)
            .addFields(
                Expression.stringConcat(constant("_-"), field("title"), "-_").alias("paddedTitle"))
            .select(field("paddedTitle").trimValue("_-").alias("trimmedTitle"), "paddedTitle")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "paddedTitle",
                "_-The Hitchhiker's Guide to the Galaxy-_",
                "trimmedTitle",
                "The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testLike() {
    assumeFalse("Regexes are not supported against the emulator.", isRunningAgainstEmulator());

    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expression.like("title", "%Guide%"))
            .select("title")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testJoin() {
    // Test join with a constant delimiter
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(join("tags", ", ").alias("joined_tags"))
            .execute();
    Map<String, Object> result = waitFor(execute).getResults().get(0).getData();
    assertThat(result.get("joined_tags")).isEqualTo("comedy, space, adventure");

    // Test join with an expression delimiter
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(join(field("tags"), constant(" | ")).alias("joined_tags"))
            .execute();
    result = waitFor(execute).getResults().get(0).getData();
    assertThat(result.get("joined_tags")).isEqualTo("comedy | space | adventure");

    // Test extension method
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(field("tags").join(" - ").alias("joined_tags"))
            .execute();
    result = waitFor(execute).getResults().get(0).getData();
    assertThat(result.get("joined_tags")).isEqualTo("comedy - space - adventure");
  }

  @Test
  public void testRegexContains() {
    assumeFalse("Regexes are not supported against the emulator.", isRunningAgainstEmulator());

    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expression.regexContains("title", "(?i)(the|of)"))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(5);
  }

  @Test
  public void testRegexFind() {
    assumeFalse("Regexes are not supported against the emulator.", isRunningAgainstEmulator());

    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(Expression.regexFind("title", "^\\w+").alias("firstWordInTitle"))
            .sort(field("firstWordInTitle").ascending())
            .limit(3)
            .execute();

    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("firstWordInTitle", "1984"),
            ImmutableMap.of("firstWordInTitle", "Crime"),
            ImmutableMap.of("firstWordInTitle", "Dune"));
  }

  @Test
  public void testRegexFindAll() {
    assumeFalse("Regexes are not supported against the emulator.", isRunningAgainstEmulator());

    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(Expression.regexFindAll("title", "\\w+").alias("wordsInTitle"))
            .sort(field("wordsInTitle").ascending())
            .limit(3)
            .execute();

    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("wordsInTitle", List.of("1984")),
            ImmutableMap.of("wordsInTitle", List.of("Crime", "and", "Punishment")),
            ImmutableMap.of("wordsInTitle", List.of("Dune")));
  }

  @Test
  public void testRegexMatches() {
    assumeFalse("Regexes are not supported against the emulator.", isRunningAgainstEmulator());

    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expression.regexContains("title", ".*(?i)(the|of).*"))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(5);
  }

  @Test
  public void testArithmeticOperations() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(ascending(Field.DOCUMENT_ID))
            .limit(1)
            .select(
                add(field("rating"), 1).alias("ratingPlusOne"),
                subtract(field("published"), 1900).alias("yearsSince1900"),
                field("rating").multiply(10).alias("ratingTimesTen"),
                field("rating").divide(2).alias("ratingDividedByTwo"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("ratingPlusOne", 5.2),
                entry("yearsSince1900", 79),
                entry("ratingTimesTen", 42),
                entry("ratingDividedByTwo", 2.1)));
  }

  @Test
  public void testComparisonOperators() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(
                and(
                    greaterThan("rating", 4.2),
                    Expression.lessThanOrEqual(field("rating"), 4.5),
                    notEqual("genre", "Science Function")))
            .select("rating", "title")
            .sort(field("title").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("rating", 4.3, "title", "Crime and Punishment"),
            ImmutableMap.of("rating", 4.3, "title", "One Hundred Years of Solitude"),
            ImmutableMap.of("rating", 4.5, "title", "Pride and Prejudice"));
  }

  @Test
  public void testLogicalOperators() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(
                or(
                    and(greaterThan("rating", 4.5), equal("genre", "Science Fiction")),
                    Expression.lessThan(field("published"), 1900)))
            .select("title")
            .sort(field("title").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "Crime and Punishment"),
            ImmutableMap.of("title", "Dune"),
            ImmutableMap.of("title", "Pride and Prejudice"));
  }

  @Test
  public void testChecks() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(ascending(Field.DOCUMENT_ID))
            .where(field("rating").notEqual(Double.NaN))
            .select(
                field("rating").equal(nullValue()).alias("ratingIsNull"),
                field("rating").equal(Expression.nullValue()).alias("ratingEqNull"),
                not(field("rating").equal(Double.NaN)).alias("ratingIsNotNan"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("ratingIsNull", false),
                entry("ratingEqNull", false),
                entry("ratingIsNotNan", true)));
  }

  @Test
  public void testLogicalMax() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("author").equal("Douglas Adams"))
            .select(
                field("rating").logicalMaximum(4.5).alias("max_rating"),
                logicalMaximum(field("published"), 1900).alias("max_published"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("max_rating", 4.5, "max_published", 1979));
  }

  @Test
  public void testLogicalMin() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("author").equal("Douglas Adams"))
            .select(
                field("rating").logicalMinimum(4.5).alias("min_rating"),
                logicalMinimum(field("published"), 1900).alias("min_published"))
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results)
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("min_rating", 4.2, "min_published", 1900));
  }

  @Test
  public void testMapGet() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(field("title").descending())
            .select(field("awards").mapGet("hugo").alias("hugoAward"), field("title"))
            .where(equal("hugoAward", true))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("hugoAward", true, "title", "The Hitchhiker's Guide to the Galaxy"),
            ImmutableMap.of("hugoAward", true, "title", "Dune"));
  }

  @Test
  public void testDistanceFunctions() {
    double[] sourceVector = {0.1, 0.1};
    double[] targetVector = {0.5, 0.8};
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(
                cosineDistance(vector(sourceVector), targetVector).alias("cosineDistance"),
                Expression.dotProduct(vector(sourceVector), targetVector)
                    .alias("dotProductDistance"),
                euclideanDistance(vector(sourceVector), targetVector).alias("euclideanDistance"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "cosineDistance", 0.02560880430538015,
                "dotProductDistance", 0.13,
                "euclideanDistance", 0.806225774829855));
  }

  @Test
  public void testNestedFields() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("awards.hugo", true))
            .select("title", "awards.hugo")
            .sort(field("title").descending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy", "awards.hugo", true),
            ImmutableMap.of("title", "Dune", "awards.hugo", true));
  }

  @Test
  public void testMapGetWithFieldNameIncludingNotation() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("awards.hugo", true))
            .sort(field("title").descending())
            .select(
                "title",
                field("nestedField.level.1"),
                mapGet("nestedField", "level.1").mapGet("level.2").alias("nested"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("title", "The Hitchhiker's Guide to the Galaxy"), entry("nested", true)),
            mapOfEntries(entry("title", "Dune")));
  }

  @Test
  public void testListEquals() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("tags", ImmutableList.of("philosophy", "crime", "redemption")))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(ID_CORRESPONDENCE)
        .containsExactly("book6");
  }

  @Test
  public void testMapEquals() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("awards", ImmutableMap.of("nobel", true, "nebula", false)))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(ID_CORRESPONDENCE)
        .containsExactly("book3");
  }

  @Test
  public void testAllDataTypes() {
    Date refDate = new Date();
    Timestamp refTimestamp = Timestamp.now();
    GeoPoint refGeoPoint = new GeoPoint(1, 2);
    Blob refBytes = Blob.fromBytes(new byte[] {1, 2, 3});

    Map<String, Object> refMap =
        mapOfEntries(
            entry("number", 1L),
            entry("string", "a string"),
            entry("boolean", true),
            entry("null", null),
            entry("geoPoint", refGeoPoint),
            entry("timestamp", refTimestamp),
            entry("date", new Timestamp(refDate)),
            entry("bytes", refBytes));

    List<Object> refArray =
        Lists.newArrayList(
            1L,
            "a string",
            true,
            null,
            refTimestamp,
            refGeoPoint,
            new Timestamp(refDate),
            refBytes);

    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .limit(1)
            .select(
                constant(1L).alias("number"),
                constant("a string").alias("string"),
                constant(true).alias("boolean"),
                Expression.nullValue().alias("null"),
                constant(refTimestamp).alias("timestamp"),
                constant(refDate).alias("date"),
                constant(refGeoPoint).alias("geoPoint"),
                constant(refBytes).alias("bytes"),
                map(refMap).alias("map"),
                array(refArray).alias("array"))
            .execute();

    Map<String, Object> expectedData = new LinkedHashMap<>();
    expectedData.put("number", 1L);
    expectedData.put("string", "a string");
    expectedData.put("boolean", true);
    expectedData.put("null", null);
    expectedData.put("timestamp", refTimestamp);
    expectedData.put("date", new Timestamp(refDate));
    expectedData.put("geoPoint", refGeoPoint);
    expectedData.put("bytes", refBytes);
    expectedData.put("map", refMap);
    expectedData.put("array", refArray);

    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(expectedData);
  }

  @Test
  public void testResultMetadata() {
    Pipeline pipeline = firestore.pipeline().collection(randomCol.getPath());
    Pipeline.Snapshot snapshot = waitFor(pipeline.execute());
    assertThat(snapshot.getExecutionTime()).isNotNull();

    for (PipelineResult result : snapshot.getResults()) {
      assertThat(result.getCreateTime()).isAtMost(result.getUpdateTime());
      assertThat(result.getUpdateTime().compareTo(snapshot.getExecutionTime())).isLessThan(0);
    }

    waitFor(randomCol.document("book1").update("rating", 5.0));
    snapshot =
        waitFor(pipeline.where(equal("title", "The Hitchhiker's Guide to the Galaxy")).execute());
    for (PipelineResult result : snapshot.getResults()) {
      assertThat(result.getCreateTime().compareTo(result.getUpdateTime())).isLessThan(0);
    }
  }

  @Test
  public void testResultIsEqual() {
    Pipeline pipeline =
        firestore.pipeline().collection(randomCol.getPath()).sort(field("title").ascending());
    Pipeline.Snapshot snapshot1 = waitFor(pipeline.limit(1).execute());
    Pipeline.Snapshot snapshot2 = waitFor(pipeline.limit(1).execute());
    Pipeline.Snapshot snapshot3 = waitFor(pipeline.offset(1).limit(1).execute());

    assertThat(snapshot1.getResults()).hasSize(1);
    assertThat(snapshot2.getResults()).hasSize(1);
    assertThat(snapshot3.getResults()).hasSize(1);
    assertThat(snapshot1.getResults().get(0)).isEqualTo(snapshot2.getResults().get(0));
    assertThat(snapshot1.getResults().get(0)).isNotEqualTo(snapshot3.getResults().get(0));
  }

  @Test
  public void testAggregateResultMetadata() {
    Pipeline pipeline =
        firestore
            .pipeline()
            .collection(randomCol)
            .aggregate(AggregateFunction.countAll().alias("count"));
    Pipeline.Snapshot snapshot = waitFor(pipeline.execute());
    assertThat(snapshot.getResults()).hasSize(1);
    assertThat(snapshot.getExecutionTime()).isNotNull();

    PipelineResult aggregateResult = snapshot.getResults().get(0);
    assertThat(aggregateResult.getCreateTime()).isNull();
    assertThat(aggregateResult.getUpdateTime()).isNull();

    // Ensure execution time is recent, within a tolerance.
    long now = new Date().getTime();
    long executionTime = snapshot.getExecutionTime().toDate().getTime();
    assertThat(now - executionTime).isLessThan(3000); // 3 seconds tolerance
  }

  @Test
  public void addAndRemoveFields() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("author").notEqual("Timestamp Author"))
            .addFields(
                Expression.stringConcat(field("author"), "_", field("title")).alias("author_title"),
                Expression.stringConcat(field("title"), "_", field("author")).alias("title_author"))
            .removeFields("title_author", "tags", "awards", "rating", "title", "embedding")
            .removeFields("published", "genre", "nestedField", "sales")
            .sort(field("author_title").ascending())
            .execute();

    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("author_title", "Douglas Adams_The Hitchhiker's Guide to the Galaxy"),
                entry("author", "Douglas Adams")),
            mapOfEntries(
                entry("author_title", "F. Scott Fitzgerald_The Great Gatsby"),
                entry("author", "F. Scott Fitzgerald")),
            mapOfEntries(
                entry("author_title", "Frank Herbert_Dune"), entry("author", "Frank Herbert")),
            mapOfEntries(
                entry("author_title", "Fyodor Dostoevsky_Crime and Punishment"),
                entry("author", "Fyodor Dostoevsky")),
            mapOfEntries(
                entry("author_title", "Gabriel García Márquez_One Hundred Years of Solitude"),
                entry("author", "Gabriel García Márquez")),
            mapOfEntries(
                entry("author_title", "George Orwell_1984"), entry("author", "George Orwell")),
            mapOfEntries(
                entry("author_title", "Harper Lee_To Kill a Mockingbird"),
                entry("author", "Harper Lee")),
            mapOfEntries(
                entry("author_title", "J.R.R. Tolkien_The Lord of the Rings"),
                entry("author", "J.R.R. Tolkien")),
            mapOfEntries(
                entry("author_title", "Jane Austen_Pride and Prejudice"),
                entry("author", "Jane Austen")),
            mapOfEntries(
                entry("author_title", "Margaret Atwood_The Handmaid's Tale"),
                entry("author", "Margaret Atwood")))
        .inOrder();
  }

  @Test
  public void testDistinct() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(lessThan("published", 1900))
            .distinct(field("genre").toLower().alias("lower_genre"))
            .sort(field("lower_genre").descending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(entry("lower_genre", "romance")),
            mapOfEntries(entry("lower_genre", "psychological thriller")));
  }

  @Test
  public void testReplaceWith() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .replaceWith("awards")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(mapOfEntries(entry("hugo", true), entry("nebula", false)));

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .replaceWith(
                Expression.map(
                    ImmutableMap.of(
                        "foo",
                        "bar",
                        "baz",
                        Expression.map(ImmutableMap.of("title", field("title"))))))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("foo", "bar"),
                entry("baz", ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"))));
  }

  @Test
  public void testSampleLimit() {
    Task<Pipeline.Snapshot> execute =
        firestore.pipeline().collection(randomCol).sample(3).execute();
    assertThat(waitFor(execute).getResults()).hasSize(3);
  }

  @Test
  public void testUnion() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .union(firestore.pipeline().collection(randomCol))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(22);
  }

  @Test
  public void testUnnest() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .unnest("tags", "tag")
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(3);
  }

  @Test
  public void testPaginationWithStartAfter() {
    CollectionReference paginationCollection =
        IntegrationTestUtil.testCollectionWithDocs(
            mapOfEntries(
                entry("doc1", ImmutableMap.of("order", 1)),
                entry("doc2", ImmutableMap.of("order", 2)),
                entry("doc3", ImmutableMap.of("order", 3)),
                entry("doc4", ImmutableMap.of("order", 4))));

    Pipeline pipeline =
        firestore.pipeline().collection(paginationCollection).sort(ascending("order")).limit(2);

    Pipeline.Snapshot snapshot = waitFor(pipeline.execute());
    assertThat(snapshot.getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("order", 1), ImmutableMap.of("order", 2));

    PipelineResult lastResult = snapshot.getResults().get(snapshot.getResults().size() - 1);
    Query startedAfter = paginationCollection.orderBy("order").startAfter(lastResult.get("order"));
    snapshot = waitFor(firestore.pipeline().createFrom(startedAfter).execute());
    assertThat(snapshot.getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("order", 3), ImmutableMap.of("order", 4));
  }

  @Test
  public void testFindNearest() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .findNearest(
                "embedding",
                vector(new double[] {10.0, 1.0, 2.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0}),
                FindNearestStage.DistanceMeasure.EUCLIDEAN,
                new FindNearestOptions().withLimit(2).withDistanceField("computedDistance"))
            .select("title", "computedDistance")
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(2);
    assertThat(results.get(0).getData().get("title"))
        .isEqualTo("The Hitchhiker's Guide to the Galaxy");
    assertThat((Double) results.get(0).getData().get("computedDistance")).isWithin(0.00001).of(1.0);
    assertThat(results.get(1).getData().get("title")).isEqualTo("One Hundred Years of Solitude");
    assertThat((Double) results.get(1).getData().get("computedDistance"))
        .isWithin(0.00001)
        .of(12.041594578792296);
  }

  @Test
  public void testMoreAggregates() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .aggregate(
                AggregateFunction.sum("rating").alias("sum_rating"),
                AggregateFunction.count("rating").alias("count_rating"),
                AggregateFunction.countDistinct("genre").alias("distinct_genres"))
            .execute();
    Map<String, Object> result = waitFor(execute).getResults().get(0).getData();
    assertThat((Double) result.get("sum_rating")).isWithin(0.00001).of(43.1);
    assertThat(result.get("count_rating")).isEqualTo(10);
    assertThat(result.get("distinct_genres")).isEqualTo(8);
  }

  @Test
  public void testCountIfAggregate() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .aggregate(
                AggregateFunction.countIf(Expression.greaterThan(field("rating"), 4.3))
                    .alias("count"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("count", 3));
  }

  @Test
  public void testStringFunctions() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(field("title").stringReverse().alias("reversed_title"), field("author"))
            .where(field("author").equal("Douglas Adams"))
            .execute();
    assertThat(waitFor(execute).getResults().get(0).getData().get("reversed_title"))
        .isEqualTo("yxalaG eht ot ediuG s'rekihhctiH ehT");

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(
                field("author"),
                field("title").stringConcat("_银河系漫", "游指南").byteLength().alias("title_byte_length"))
            .where(field("author").equal("Douglas Adams"))
            .execute();
    assertThat(waitFor(execute).getResults().get(0).getData().get("title_byte_length"))
        .isEqualTo(58);
  }

  @Test
  public void testStrContains() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expression.stringContains(field("title"), "'s"))
            .select("title")
            .sort(field("title").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "The Handmaid's Tale"),
            ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testSubstring() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Lord of the Rings"))
            .select(
                Expression.substring(field("title"), constant(9), constant(2)).alias("of"),
                Expression.substring("title", 16, 5).alias("Rings"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("of", "of", "Rings", "Rings"));
  }

  @Test
  public void testSplitStringByStringDelimiter() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(split(field("title"), " ").alias("split_title"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "split_title",
                ImmutableList.of("The", "Hitchhiker's", "Guide", "to", "the", "Galaxy")));

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(field("title").split(" ").alias("split_title"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "split_title",
                ImmutableList.of("The", "Hitchhiker's", "Guide", "to", "the", "Galaxy")));
  }

  @Test
  public void testSplitStringByExpressionDelimiter() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(split(field("title"), constant(" ")).alias("split_title"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "split_title",
                ImmutableList.of("The", "Hitchhiker's", "Guide", "to", "the", "Galaxy")));

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(field("title").split(constant(" ")).alias("split_title"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "split_title",
                ImmutableList.of("The", "Hitchhiker's", "Guide", "to", "the", "Galaxy")));
  }

  @Test
  public void testSplitBlobByByteArrayDelimiter() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .limit(1)
            .addFields(
                constant(Blob.fromBytes(new byte[] {0x01, 0x02, 0x03, 0x04, 0x01, 0x05}))
                    .alias("data"))
            .select(split(field("data"), Blob.fromBytes(new byte[] {0x01})).alias("split_data"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "split_data",
                ImmutableList.of(
                    Blob.fromBytes(new byte[] {}),
                    Blob.fromBytes(new byte[] {0x02, 0x03, 0x04}),
                    Blob.fromBytes(new byte[] {0x05}))));

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .limit(1)
            .addFields(
                constant(Blob.fromBytes(new byte[] {0x01, 0x02, 0x03, 0x04, 0x01, 0x05}))
                    .alias("data"))
            .select(field("data").split(Blob.fromBytes(new byte[] {0x01})).alias("split_data"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "split_data",
                ImmutableList.of(
                    Blob.fromBytes(new byte[] {}),
                    Blob.fromBytes(new byte[] {0x02, 0x03, 0x04}),
                    Blob.fromBytes(new byte[] {0x05}))));
  }

  @Test
  public void testSplitStringFieldByStringDelimiter() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(split("title", " ").alias("split_title"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "split_title",
                ImmutableList.of("The", "Hitchhiker's", "Guide", "to", "the", "Galaxy")));
  }

  @Test
  public void testSplitStringFieldByExpressionDelimiter() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(split("title", constant(" ")).alias("split_title"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "split_title",
                ImmutableList.of("The", "Hitchhiker's", "Guide", "to", "the", "Galaxy")));
  }

  @Test
  public void testSplitWithMismatchedTypesShouldFail() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                split(field("title"), Blob.fromBytes(new byte[] {0x01})).alias("mismatched_split"))
            .execute();
    assertThat(waitForException(execute)).isNotNull();
  }

  @Test
  public void testLogicalAndComparisonOperators() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(
                Expression.xor(
                    equal("genre", "Romance"),
                    equal("genre", "Dystopian"),
                    equal("genre", "Fantasy"),
                    equal("published", 1949)))
            .select("title")
            .sort(field("title").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "Pride and Prejudice"),
            ImmutableMap.of("title", "The Handmaid's Tale"),
            ImmutableMap.of("title", "The Lord of the Rings"));

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expression.equalAny("genre", ImmutableList.of("Romance", "Dystopian")))
            .select("title")
            .sort(descending("title"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "The Handmaid's Tale"),
            ImmutableMap.of("title", "Pride and Prejudice"),
            ImmutableMap.of("title", "1984"));

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(exists("genre"))
            .where(Expression.notEqualAny("genre", ImmutableList.of("Romance", "Dystopian")))
            .select("genre")
            .distinct("genre")
            .sort(ascending("genre"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("genre", "Fantasy"),
            ImmutableMap.of("genre", "Magical Realism"),
            ImmutableMap.of("genre", "Modernist"),
            ImmutableMap.of("genre", "Psychological Thriller"),
            ImmutableMap.of("genre", "Science Fiction"),
            ImmutableMap.of("genre", "Southern Gothic"));
  }

  @Test
  public void testCondExpression() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("title").notEqual("Timestamp Book"))
            .select(
                Expression.conditional(
                        Expression.greaterThan(field("published"), 1980), "Modern", "Classic")
                    .alias("era"),
                field("title"),
                field("published"))
            .sort(field("published").ascending())
            .limit(2)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("era", "Classic"),
                entry("title", "Pride and Prejudice"),
                entry("published", 1813)),
            mapOfEntries(
                entry("era", "Classic"),
                entry("title", "Crime and Punishment"),
                entry("published", 1866)));
  }

  @Test
  public void testDataManipulationExpressions() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "Timestamp Book"))
            .select(
                Expression.timestampAdd(field("timestamp"), "day", 1).alias("timestamp_plus_day"),
                Expression.timestampSubtract(field("timestamp"), "hour", 1)
                    .alias("timestamp_minus_hour"))
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(1);
    Date originalTimestamp = (Date) bookDocs.get("book11").get("timestamp");
    Timestamp timestampPlusDay = (Timestamp) results.get(0).getData().get("timestamp_plus_day");
    Timestamp timestampMinusHour = (Timestamp) results.get(0).getData().get("timestamp_minus_hour");
    assertThat(timestampPlusDay.toDate().getTime() - originalTimestamp.getTime())
        .isEqualTo(24 * 60 * 60 * 1000);
    assertThat(originalTimestamp.getTime() - timestampMinusHour.toDate().getTime())
        .isEqualTo(60 * 60 * 1000);

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                Expression.arrayGet("tags", 1).alias("second_tag"),
                field("awards")
                    .mapMerge(Expression.map(ImmutableMap.of("new_award", true)))
                    .alias("merged_awards"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("second_tag", "space"),
                entry(
                    "merged_awards",
                    ImmutableMap.of("hugo", true, "nebula", false, "new_award", true))));

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                Expression.arrayReverse("tags").alias("reversed_tags"),
                Expression.mapRemove(field("awards"), "nebula").alias("removed_awards"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("reversed_tags", ImmutableList.of("adventure", "space", "comedy")),
                entry("removed_awards", ImmutableMap.of("hugo", true))));
  }

  @Test
  public void testTimestampTrunc() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "Timestamp Book"))
            .select(
                Expression.timestampTruncate(field("timestamp"), "year").alias("trunc_year"),
                Expression.timestampTruncate(field("timestamp"), "month").alias("trunc_month"),
                Expression.timestampTruncate(field("timestamp"), "day").alias("trunc_day"),
                Expression.timestampTruncate(field("timestamp"), "hour").alias("trunc_hour"),
                Expression.timestampTruncate(field("timestamp"), "minute").alias("trunc_minute"),
                Expression.timestampTruncate(field("timestamp"), "second").alias("trunc_second"))
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(1);
    Map<String, Object> data = results.get(0).getData();
    Date originalDate = (Date) bookDocs.get("book11").get("timestamp");
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    cal.setTime(originalDate);

    cal.set(Calendar.MONTH, Calendar.JANUARY);
    cal.set(Calendar.DAY_OF_MONTH, 1);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    assertThat(data.get("trunc_year")).isEqualTo(new Timestamp(cal.getTime()));

    cal.setTime(originalDate);
    cal.set(Calendar.DAY_OF_MONTH, 1);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    assertThat(data.get("trunc_month")).isEqualTo(new Timestamp(cal.getTime()));

    cal.setTime(originalDate);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    assertThat(data.get("trunc_day")).isEqualTo(new Timestamp(cal.getTime()));

    cal.setTime(originalDate);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    assertThat(data.get("trunc_hour")).isEqualTo(new Timestamp(cal.getTime()));

    cal.setTime(originalDate);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    assertThat(data.get("trunc_minute")).isEqualTo(new Timestamp(cal.getTime()));

    cal.setTime(originalDate);
    cal.set(Calendar.MILLISECOND, 0);
    assertThat(data.get("trunc_second")).isEqualTo(new Timestamp(cal.getTime()));
  }

  @Test
  public void testMathExpressions() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                Expression.ceil(field("rating")).alias("ceil_rating"),
                Expression.floor(field("rating")).alias("floor_rating"),
                Expression.pow(field("rating"), 2).alias("pow_rating"),
                Expression.round(field("rating")).alias("round_rating"),
                Expression.sqrt(field("rating")).alias("sqrt_rating"),
                field("published").mod(10).alias("mod_published"))
            .execute();
    Map<String, Object> result = waitFor(execute).getResults().get(0).getData();
    assertThat((Double) result.get("ceil_rating")).isEqualTo(5.0);
    assertThat((Double) result.get("floor_rating")).isEqualTo(4.0);
    assertThat((Double) result.get("pow_rating")).isWithin(0.00001).of(17.64);
    assertThat((Double) result.get("round_rating")).isEqualTo(4.0);
    assertThat((Double) result.get("sqrt_rating")).isWithin(0.00001).of(2.04939);
    assertThat(result.get("mod_published")).isEqualTo(9);
  }

  @Test
  public void testAdvancedMathExpressions() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Lord of the Rings"))
            .select(
                Expression.exp(field("rating")).alias("exp_rating"),
                Expression.ln(field("rating")).alias("ln_rating"),
                Expression.log(field("rating"), 10).alias("log_rating"),
                field("rating").log10().alias("log10_rating"))
            .execute();
    Map<String, Object> result = waitFor(execute).getResults().get(0).getData();
    assertThat((Double) result.get("exp_rating")).isWithin(0.00001).of(109.94717);
    assertThat((Double) result.get("ln_rating")).isWithin(0.00001).of(1.54756);
    assertThat((Double) result.get("log_rating")).isWithin(0.00001).of(0.67209);
    assertThat((Double) result.get("log10_rating")).isWithin(0.00001).of(0.67209);
  }

  @Test
  public void testTimestampConversions() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .limit(1)
            .select(
                Expression.unixSecondsToTimestamp(constant(1741380235L))
                    .alias("unixSecondsToTimestamp"),
                Expression.unixMillisToTimestamp(constant(1741380235123L))
                    .alias("unixMillisToTimestamp"),
                Expression.timestampToUnixSeconds(constant(new Timestamp(1741380235L, 123456789)))
                    .alias("timestampToUnixSeconds"),
                Expression.timestampToUnixMillis(constant(new Timestamp(1741380235L, 123456789)))
                    .alias("timestampToUnixMillis"))
            .execute();
    Map<String, Object> result = waitFor(execute).getResults().get(0).getData();
    assertThat(result.get("unixSecondsToTimestamp")).isEqualTo(new Timestamp(1741380235L, 0));
    assertThat(result.get("unixMillisToTimestamp"))
        .isEqualTo(new Timestamp(1741380235L, 123000000));
    assertThat(result.get("timestampToUnixSeconds")).isEqualTo(1741380235L);
    assertThat(result.get("timestampToUnixMillis")).isEqualTo(1741380235123L);
  }

  @Test
  public void testCurrentTimestamp() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .limit(1)
            .select(currentTimestamp().alias("now"))
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(1);
    Object nowValue = results.get(0).getData().get("now");
    assertThat(nowValue).isInstanceOf(Timestamp.class);
    Timestamp nowTimestamp = (Timestamp) nowValue;
    // Check that the timestamp is recent (e.g., within the last 5 seconds)
    long diff = new Date().getTime() - nowTimestamp.toDate().getTime();
    assertThat(diff).isAtMost(5000L);
  }

  @Test
  public void testTypeFunction() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .sort(field(FieldPath.documentId()).ascending())
            .limit(1)
            .select(
                field("title").type().alias("titleType"),
                field("published").type().alias("publishedType"),
                field("published").add(1.2).type().alias("publishedToDoubleType"),
                field("awards").mapGet("hugo").type().alias("hugoType"),
                Expression.nullValue().type().alias("nullType"),
                field("tags").type().alias("tagsType"),
                field("awards").type().alias("awardsType"),
                constant(new Date()).type().alias("timestampType"),
                field("embedding").type().alias("embeddingType"),
                field("nestedField").type().alias("nestedFieldType"))
            .execute();

    Map<String, Object> result = waitFor(execute).getResults().get(0).getData();
    assertThat(result.get("titleType")).isEqualTo("string");
    assertThat(result.get("publishedType")).isEqualTo("int64");
    assertThat(result.get("publishedToDoubleType")).isEqualTo("float64");
    assertThat(result.get("hugoType")).isEqualTo("boolean");
    assertThat(result.get("nullType")).isEqualTo("null");
    assertThat(result.get("tagsType")).isEqualTo("array");
    assertThat(result.get("awardsType")).isEqualTo("map");
    assertThat(result.get("timestampType")).isEqualTo("timestamp");
    assertThat(result.get("embeddingType")).isEqualTo("vector");
    assertThat(result.get("nestedFieldType")).isEqualTo("map");

    execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .sort(field(FieldPath.documentId()).ascending())
            .limit(1)
            .select(
                Expression.type("title").alias("titleType"),
                Expression.type("published").alias("publishedType"),
                Expression.type(field("awards").mapGet("hugo")).alias("hugoType"),
                Expression.type(Expression.nullValue()).alias("nullType"),
                Expression.type("tags").alias("tagsType"),
                Expression.type("awards").alias("awardsType"),
                Expression.type(constant(new Date())).alias("timestampType"),
                Expression.type("embedding").alias("embeddingType"),
                Expression.type("nestedField").alias("nestedFieldType"))
            .execute();

    result = waitFor(execute).getResults().get(0).getData();
    assertThat(result.get("titleType")).isEqualTo("string");
    assertThat(result.get("publishedType")).isEqualTo("int64");
    assertThat(result.get("hugoType")).isEqualTo("boolean");
    assertThat(result.get("nullType")).isEqualTo("null");
    assertThat(result.get("tagsType")).isEqualTo("array");
    assertThat(result.get("awardsType")).isEqualTo("map");
    assertThat(result.get("timestampType")).isEqualTo("timestamp");
    assertThat(result.get("embeddingType")).isEqualTo("vector");
    assertThat(result.get("nestedFieldType")).isEqualTo("map");
  }

  @Test
  public void testVectorLength() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .limit(1)
            .select(
                Expression.vectorLength(Expression.vector(new double[] {1.0, 2.0, 3.0}))
                    .alias("vectorLength"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("vectorLength", 3));
  }

  @Test
  public void canGetTheCollectionIdFromAPath() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .limit(1)
            .select(field("__name__").collectionId().alias("collectionId"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("collectionId", randomCol.getId()));
  }

  @Test
  public void canGetTheCollectionIdFromAPathWithTheTopLevelFunction() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .limit(1)
            .select(collectionId("__name__").alias("collectionId"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("collectionId", randomCol.getId()));
  }

  @Test
  public void testSupportsDocumentId() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .sort(field("rating").descending())
            .limit(1)
            .select(documentId(field("__name__")).alias("docId"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("docId", "book4"));

    execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .sort(field("rating").descending())
            .limit(1)
            .select(field("__name__").documentId().alias("docId"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("docId", "book4"));
  }

  @Test
  public void testDocumentsAsSource() {
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .documents(
                randomCol.document("book1"),
                randomCol.document("book2"),
                randomCol.document("book3"))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(3);
  }

  @Test
  public void testCollectionGroupAsSource() {
    String subcollectionId = randomCol.document().getId();
    waitFor(
        randomCol.document("book1").collection(subcollectionId).add(ImmutableMap.of("order", 1)));
    waitFor(
        randomCol.document("book2").collection(subcollectionId).add(ImmutableMap.of("order", 2)));
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collectionGroup(subcollectionId)
            .sort(field("order").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("order", 1), ImmutableMap.of("order", 2));
  }

  @Test
  public void testErrorHandling() {
    Exception exception =
        assertThrows(
            Exception.class,
            () -> {
              waitFor(
                  firestore
                      .pipeline()
                      .collection(randomCol)
                      .rawStage(RawStage.ofName("invalidStage"))
                      .execute());
            });
  }

  @Test
  public void testIfAbsent() {
    // Case 1: Field is present, should return the field value.
    Task<Pipeline.Snapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(field("rating").ifAbsent(0.0).alias("rating_or_default"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("rating_or_default", 4.2));

    // Case 2: Field is absent, should return the default value.
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                Expression.ifAbsent(field("non_existent_field"), "default")
                    .alias("field_or_default"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("field_or_default", "default"));

    // Case 3: Field is present and null, should return null.
    Map values = new HashMap<>();
    values.put("title", "Book With Null");
    values.put("optional_field", null);
    waitFor(randomCol.document("bookWithNull").set(values));
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "Book With Null"))
            .select(
                Expression.ifAbsent(field("optional_field"), "default").alias("field_or_default"))
            .execute();
    assertThat(waitFor(execute).getResults().get(0).get("field_or_default")).isNull();
    waitFor(randomCol.document("bookWithNull").delete());

    // Case 4: Test different overloads.
    // ifAbsent(String, Any)
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "Dune"))
            .select(Expression.ifAbsent("non_existent_field", "default_string").alias("res"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("res", "default_string"));

    // ifAbsent(String, Expression)
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "Dune"))
            .select(Expression.ifAbsent("non_existent_field", field("author")).alias("res"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("res", "Frank Herbert"));

    // ifAbsent(Expression, Expression)
    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(equal("title", "Dune"))
            .select(Expression.ifAbsent(field("non_existent_field"), field("author")).alias("res"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("res", "Frank Herbert"));
  }

  @Test
  public void testCrossDatabaseRejection() {
    FirebaseFirestore firestore2 = IntegrationTestUtil.testAlternateFirestore();
    CollectionReference collection2 = firestore2.collection("test-collection");
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              firestore.pipeline().collection(collection2);
            });
    assertThat(exception.getMessage()).contains("Invalid CollectionReference");
  }

  @Test
  public void testOptions() {
    assumeFalse(
        "Certain options are not supported against the emulator yet.", isRunningAgainstEmulator());

    Pipeline.ExecuteOptions opts =
        new Pipeline.ExecuteOptions().withIndexMode(Pipeline.ExecuteOptions.IndexMode.RECOMMENDED);

    double[] vector = {1.0, 2.0, 3.0};

    Pipeline pipeline =
        firestore
            .pipeline()
            .collection(
                firestore.collection("k"),
                new CollectionSourceOptions()
                    .withHints(new CollectionHints().withForceIndex("abcdef")))
            .findNearest(
                "topicVectors",
                vector(vector),
                FindNearestStage.DistanceMeasure.COSINE,
                new FindNearestOptions().withLimit(10).withDistanceField("distance"))
            .unnest(field("awards").alias("award"), new UnnestOptions().withIndexField("fgoo"))
            .aggregate(
                AggregateStage.withAccumulators(
                        AggregateFunction.average("rating").alias("avg_rating"))
                    .withGroups("genre"),
                new AggregateOptions()
                    .withHints(new AggregateHints().withForceStreamableEnabled()));

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              waitFor(pipeline.execute());
            });
    assertThat(exception.getMessage()).contains("Invalid index");
  }

  @Test
  public void disallowDuplicateAliasesInAggregate() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              firestore
                  .pipeline()
                  .collection(randomCol)
                  .aggregate(
                      AggregateFunction.countAll().alias("dup"),
                      AggregateFunction.average("rating").alias("dup"))
                  .execute();
            });
    assertThat(exception.getMessage()).contains("Duplicate alias: 'dup'");
  }

  @Test
  public void disallowDuplicateAliasesInSelect() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              firestore
                  .pipeline()
                  .collection(randomCol)
                  .select(field("rating").alias("dup"), field("published").alias("dup"))
                  .execute();
            });
    assertThat(exception.getMessage()).contains("Duplicate alias: 'dup'");
  }

  @Test
  public void disallowDuplicateAliasesInAddFields() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              firestore
                  .pipeline()
                  .collection(randomCol)
                  .addFields(field("rating").alias("dup"), field("published").alias("dup"))
                  .execute();
            });
    assertThat(exception.getMessage()).contains("Duplicate alias: 'dup'");
  }

  @Test
  public void disallowDuplicateAliasesInDistinct() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              firestore
                  .pipeline()
                  .collection(randomCol)
                  .distinct(field("rating").alias("dup"), field("published").alias("dup"))
                  .execute();
            });
    assertThat(exception.getMessage()).contains("Duplicate alias: 'dup'");
  }

  static <T> Map.Entry<String, T> entry(String key, T value) {
    return new Map.Entry<String, T>() {
      private String k = key;
      private T v = value;

      @Override
      public String getKey() {
        return k;
      }

      @Override
      public T getValue() {
        return v;
      }

      @Override
      public T setValue(T value) {
        T old = v;
        v = value;
        return old;
      }

      @Override
      public boolean equals(Object o) {
        if (!(o instanceof Map.Entry)) {
          return false;
        }

        Map.Entry<?, ?> that = (Map.Entry<?, ?>) o;
        return com.google.common.base.Objects.equal(k, that.getKey())
            && com.google.common.base.Objects.equal(v, that.getValue());
      }

      @Override
      public int hashCode() {
        return com.google.common.base.Objects.hashCode(k, v);
      }
    };
  }

  @SafeVarargs
  static <T> Map<String, T> mapOfEntries(Map.Entry<String, T>... entries) {
    Map<String, T> res = new LinkedHashMap<>();
    for (Map.Entry<String, T> entry : entries) {
      res.put(entry.getKey(), entry.getValue());
    }
    return Collections.unmodifiableMap(res);
  }
}
