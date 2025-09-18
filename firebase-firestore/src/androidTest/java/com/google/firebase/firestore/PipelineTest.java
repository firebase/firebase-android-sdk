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
import static com.google.firebase.firestore.pipeline.AggregateFunction.avg;
import static com.google.firebase.firestore.pipeline.Expr.add;
import static com.google.firebase.firestore.pipeline.Expr.and;
import static com.google.firebase.firestore.pipeline.Expr.array;
import static com.google.firebase.firestore.pipeline.Expr.arrayContains;
import static com.google.firebase.firestore.pipeline.Expr.arrayContainsAny;
import static com.google.firebase.firestore.pipeline.Expr.constant;
import static com.google.firebase.firestore.pipeline.Expr.cosineDistance;
import static com.google.firebase.firestore.pipeline.Expr.endsWith;
import static com.google.firebase.firestore.pipeline.Expr.eq;
import static com.google.firebase.firestore.pipeline.Expr.euclideanDistance;
import static com.google.firebase.firestore.pipeline.Expr.field;
import static com.google.firebase.firestore.pipeline.Expr.gt;
import static com.google.firebase.firestore.pipeline.Expr.logicalMaximum;
import static com.google.firebase.firestore.pipeline.Expr.logicalMinimum;
import static com.google.firebase.firestore.pipeline.Expr.lt;
import static com.google.firebase.firestore.pipeline.Expr.lte;
import static com.google.firebase.firestore.pipeline.Expr.map;
import static com.google.firebase.firestore.pipeline.Expr.mapGet;
import static com.google.firebase.firestore.pipeline.Expr.neq;
import static com.google.firebase.firestore.pipeline.Expr.not;
import static com.google.firebase.firestore.pipeline.Expr.or;
import static com.google.firebase.firestore.pipeline.Expr.startsWith;
import static com.google.firebase.firestore.pipeline.Expr.strConcat;
import static com.google.firebase.firestore.pipeline.Expr.subtract;
import static com.google.firebase.firestore.pipeline.Expr.vector;
import static com.google.firebase.firestore.pipeline.Ordering.ascending;
import static com.google.firebase.firestore.pipeline.Ordering.descending;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.isRunningAgainstEmulator;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

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
import com.google.firebase.firestore.pipeline.Expr;
import com.google.firebase.firestore.pipeline.Field;
import com.google.firebase.firestore.pipeline.FindNearestOptions;
import com.google.firebase.firestore.pipeline.FindNearestStage;
import com.google.firebase.firestore.pipeline.PipelineOptions;
import com.google.firebase.firestore.pipeline.RawStage;
import com.google.firebase.firestore.pipeline.UnnestOptions;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    randomCol = IntegrationTestUtil.testCollectionWithDocs(bookDocs);
    firestore = randomCol.firestore;
  }

  @Test
  public void emptyResults() {
    Task<PipelineSnapshot> execute =
        firestore.pipeline().collection(randomCol.getPath()).limit(0).execute();
    assertThat(waitFor(execute).getResults()).isEmpty();
  }

  @Test
  public void fullResults() {
    Task<PipelineSnapshot> execute = firestore.pipeline().collection(randomCol.getPath()).execute();
    assertThat(waitFor(execute).getResults()).hasSize(11);
  }

  @Test
  public void aggregateResultsCountAll() {
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("genre", "Science Fiction"))
            .aggregate(
                AggregateFunction.countAll().alias("count"),
                avg("rating").alias("avgRating"),
                field("rating").maximum().alias("maxRating"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(entry("count", 10), entry("avgRating", 4.4), entry("maxRating", 4.6)));
  }

  @Test
  public void groupAndAccumulateResults() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(lt(field("published"), 1984))
            .aggregate(
                AggregateStage.withAccumulators(avg("rating").alias("avgRating"))
                    .withGroups("genre"))
            .where(gt("avgRating", 4.3))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .rawStage(RawStage.ofName("where").withArguments(lt(field("published"), 1984)))
            .rawStage(
                RawStage.ofName("aggregate")
                    .withArguments(
                        ImmutableMap.of("avgRating", avg("rating")),
                        ImmutableMap.of("genre", field("genre"))))
            .rawStage(RawStage.ofName("where").withArguments(gt("avgRating", 4.3)))
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(and(gt("rating", 4.5), eq("genre", "Science Fiction")))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(ID_CORRESPONDENCE)
        .containsExactly("book10");
  }

  @Test
  public void whereWithOr() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(or(eq("genre", "Romance"), eq("genre", "Dystopian")))
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(field("tags").arrayLength().alias("tagsCount"))
            .where(eq("tagsCount", 3))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(10);
  }

  @Test
  @Ignore("Not supported yet")
  public void arrayConcatWorks() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("title", "The Hitchhiker's Guide to the Galaxy"))
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
  public void testStrConcat() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(ascending(Field.DOCUMENT_ID))
            .select(strConcat(field("author"), constant(" - "), field("title")).alias("bookInfo"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("bookInfo", "Douglas Adams - The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testStartsWith() {
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(field("title").charLength().alias("titleLength"), field("title"))
            .where(gt("titleLength", 20))
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
  public void testToLowercase() {
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
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
  @Ignore("Not supported yet")
  public void testTrim() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .addFields(strConcat(" ", field("title"), " ").alias("spacedTitle"))
            .select(field("spacedTitle").trim().alias("trimmedTitle"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of(
                "spacedTitle",
                " The Hitchhiker's Guide to the Galaxy ",
                "trimmedTitle",
                "The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testLike() {
    assumeFalse("Regexes are not supported against the emulator.", isRunningAgainstEmulator());

    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expr.like("title", "%Guide%"))
            .select("title")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testRegexContains() {
    assumeFalse("Regexes are not supported against the emulator.", isRunningAgainstEmulator());

    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expr.regexContains("title", "(?i)(the|of)"))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(5);
  }

  @Test
  public void testRegexMatches() {
    assumeFalse("Regexes are not supported against the emulator.", isRunningAgainstEmulator());

    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expr.regexContains("title", ".*(?i)(the|of).*"))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(5);
  }

  @Test
  public void testArithmeticOperations() {
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(
                and(gt("rating", 4.2), lte(field("rating"), 4.5), neq("genre", "Science Function")))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(
                or(
                    and(gt("rating", 4.5), eq("genre", "Science Fiction")),
                    lt(field("published"), 1900)))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(not(field("rating").isNan()))
            .select(
                field("rating").isNull().alias("ratingIsNull"),
                field("rating").eq(Expr.nullValue()).alias("ratingEqNull"),
                not(field("rating").isNan()).alias("ratingIsNotNan"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("ratingIsNull", false),
                entry("ratingEqNull", null),
                entry("ratingIsNotNan", true)));
  }

  @Test
  public void testLogicalMax() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("author").eq("Douglas Adams"))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("author").eq("Douglas Adams"))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .sort(field("title").descending())
            .select(field("awards").mapGet("hugo").alias("hugoAward"), field("title"))
            .where(eq("hugoAward", true))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(
                cosineDistance(vector(sourceVector), targetVector).alias("cosineDistance"),
                Expr.dotProduct(vector(sourceVector), targetVector).alias("dotProductDistance"),
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("awards.hugo", true))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("awards.hugo", true))
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
                entry("title", "The Hitchhiker's Guide to the Galaxy"),
                entry("nestedField.level.`1`", null),
                entry("nested", true)),
            mapOfEntries(
                entry("title", "Dune"),
                entry("nestedField.level.`1`", null),
                entry("nested", null)));
  }

  @Test
  public void testListEquals() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("tags", ImmutableList.of("philosophy", "crime", "redemption")))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(ID_CORRESPONDENCE)
        .containsExactly("book6");
  }

  @Test
  public void testMapEquals() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("awards", ImmutableMap.of("nobel", true, "nebula", false)))
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

    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol.getPath())
            .limit(1)
            .select(
                constant(1L).alias("number"),
                constant("a string").alias("string"),
                constant(true).alias("boolean"),
                Expr.nullValue().alias("null"),
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
    PipelineSnapshot snapshot = waitFor(pipeline.execute());
    assertThat(snapshot.getExecutionTime()).isNotNull();

    for (PipelineResult result : snapshot.getResults()) {
      assertThat(result.getCreateTime()).isAtMost(result.getUpdateTime());
      assertThat(result.getUpdateTime().compareTo(snapshot.getExecutionTime())).isLessThan(0);
    }

    waitFor(randomCol.document("book1").update("rating", 5.0));
    snapshot =
        waitFor(pipeline.where(eq("title", "The Hitchhiker's Guide to the Galaxy")).execute());
    for (PipelineResult result : snapshot.getResults()) {
      assertThat(result.getCreateTime().compareTo(result.getUpdateTime())).isLessThan(0);
    }
  }

  @Test
  public void testResultIsEqual() {
    Pipeline pipeline =
        firestore.pipeline().collection(randomCol.getPath()).sort(field("title").ascending());
    PipelineSnapshot snapshot1 = waitFor(pipeline.limit(1).execute());
    PipelineSnapshot snapshot2 = waitFor(pipeline.limit(1).execute());
    PipelineSnapshot snapshot3 = waitFor(pipeline.offset(1).limit(1).execute());

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
    PipelineSnapshot snapshot = waitFor(pipeline.execute());
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("author").neq("Timestamp Author"))
            .addFields(
                strConcat(field("author"), "_", field("title")).alias("author_title"),
                strConcat(field("title"), "_", field("author")).alias("title_author"))
            .removeFields("title_author", "tags", "awards", "rating", "title", "embedding")
            .removeFields("published", "genre", "nestedField")
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(lt("published", 1900))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("title", "The Hitchhiker's Guide to the Galaxy"))
            .replaceWith("awards")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(mapOfEntries(entry("hugo", true), entry("nebula", false)));

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("title", "The Hitchhiker's Guide to the Galaxy"))
            .replaceWith(
                Expr.map(
                    ImmutableMap.of(
                        "foo", "bar", "baz", Expr.map(ImmutableMap.of("title", field("title"))))))
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
    Task<PipelineSnapshot> execute = firestore.pipeline().collection(randomCol).sample(3).execute();
    assertThat(waitFor(execute).getResults()).hasSize(3);
  }

  @Test
  public void testUnion() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .union(firestore.pipeline().collection(randomCol))
            .execute();
    assertThat(waitFor(execute).getResults()).hasSize(22);
  }

  @Test
  public void testUnnest() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("title", "The Hitchhiker's Guide to the Galaxy"))
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

    PipelineSnapshot snapshot = waitFor(pipeline.execute());
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .aggregate(AggregateFunction.countIf(gt(field("rating"), 4.3)).alias("count"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("count", 3));
  }

  @Test
  public void testStringFunctions() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(field("title").strReverse().alias("reversed_title"), field("author"))
            .where(field("author").eq("Douglas Adams"))
            .execute();
    assertThat(waitFor(execute).getResults().get(0).getData().get("reversed_title"))
        .isEqualTo("yxalaG eht ot ediuG s'rekihhctiH ehT");

    execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(
                field("author"),
                field("title").strConcat("_银河系漫", "游指南").byteLength().alias("title_byte_length"))
            .where(field("author").eq("Douglas Adams"))
            .execute();
    assertThat(waitFor(execute).getResults().get(0).getData().get("title_byte_length"))
        .isEqualTo(58);
  }

  @Test
  public void testStrContains() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(Expr.strContains(field("title"), "'s"))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("title", "The Lord of the Rings"))
            .select(
                Expr.substr(field("title"), constant(9), constant(2)).alias("of"),
                Expr.substr("title", 16, 5).alias("Rings"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("of", "of", "Rings", "Rings"));
  }

  @Test
  public void testLogicalAndComparisonOperators() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(
                Expr.xor(
                    eq("genre", "Romance"),
                    eq("genre", "Dystopian"),
                    eq("genre", "Fantasy"),
                    eq("published", 1949)))
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
            .where(Expr.eqAny("genre", ImmutableList.of("Romance", "Dystopian")))
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
            .where(Expr.notEqAny("genre", ImmutableList.of("Romance", "Dystopian")))
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(field("title").neq("Timestamp Book"))
            .select(
                Expr.cond(gt(field("published"), 1980), "Modern", "Classic").alias("era"),
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("title", "Timestamp Book"))
            .select(
                Expr.timestampAdd(field("timestamp"), "day", 1).alias("timestamp_plus_day"),
                Expr.timestampSub(field("timestamp"), "hour", 1).alias("timestamp_minus_hour"))
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
            .where(eq("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                Expr.arrayGet("tags", 1).alias("second_tag"),
                field("awards")
                    .mapMerge(Expr.map(ImmutableMap.of("new_award", true)))
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
            .where(eq("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                Expr.arrayReverse("tags").alias("reversed_tags"),
                Expr.mapRemove(field("awards"), "nebula").alias("removed_awards"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(
                entry("reversed_tags", ImmutableList.of("adventure", "space", "comedy")),
                entry("removed_awards", ImmutableMap.of("hugo", true))));
  }

  @Test
  public void testMathExpressions() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("title", "The Hitchhiker's Guide to the Galaxy"))
            .select(
                Expr.ceil(field("rating")).alias("ceil_rating"),
                Expr.floor(field("rating")).alias("floor_rating"),
                Expr.pow(field("rating"), 2).alias("pow_rating"),
                Expr.round(field("rating")).alias("round_rating"),
                Expr.sqrt(field("rating")).alias("sqrt_rating"),
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
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(eq("title", "The Lord of the Rings"))
            .select(
                Expr.exp(field("rating")).alias("exp_rating"),
                Expr.ln(field("rating")).alias("ln_rating"),
                Expr.log(field("rating"), 10).alias("log_rating"))
            .execute();
    Map<String, Object> result = waitFor(execute).getResults().get(0).getData();
    assertThat((Double) result.get("exp_rating")).isWithin(0.00001).of(109.94717);
    assertThat((Double) result.get("ln_rating")).isWithin(0.00001).of(1.54756);
    assertThat((Double) result.get("log_rating")).isWithin(0.00001).of(0.67209);
  }

  @Test
  public void testTimestampConversions() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .limit(1)
            .select(
                Expr.unixSecondsToTimestamp(constant(1741380235L)).alias("unixSecondsToTimestamp"),
                Expr.unixMillisToTimestamp(constant(1741380235123L)).alias("unixMillisToTimestamp"),
                Expr.timestampToUnixSeconds(constant(new Timestamp(1741380235L, 123456789)))
                    .alias("timestampToUnixSeconds"),
                Expr.timestampToUnixMillis(constant(new Timestamp(1741380235L, 123456789)))
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
  public void testRand() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .limit(10)
            .select(Expr.rand().alias("result"))
            .execute();
    List<PipelineResult> results = waitFor(execute).getResults();
    assertThat(results).hasSize(10);
    for (PipelineResult result : results) {
      Double randVal = (Double) result.getData().get("result");
      assertThat(randVal).isAtLeast(0.0);
      assertThat(randVal).isLessThan(1.0);
    }
  }

  @Test
  public void testVectorLength() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .limit(1)
            .select(
                Expr.vectorLength(Expr.vector(new double[] {1.0, 2.0, 3.0})).alias("vectorLength"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("vectorLength", 3));
  }

  @Test
  public void testDocumentsAsSource() {
    Task<PipelineSnapshot> execute =
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
    Task<PipelineSnapshot> execute =
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

    PipelineOptions opts =
        new PipelineOptions().withIndexMode(PipelineOptions.IndexMode.RECOMMENDED);

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
                AggregateStage.withAccumulators(avg("rating").alias("avg_rating"))
                    .withGroups("genre"),
                new AggregateOptions()
                    .withHints(new AggregateHints().withForceStreamableEnabled()));

    waitFor(pipeline.execute(opts));
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
