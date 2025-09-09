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
import static com.google.firebase.firestore.pipeline.Expr.add;
import static com.google.firebase.firestore.pipeline.Expr.and;
import static com.google.firebase.firestore.pipeline.Expr.arrayContains;
import static com.google.firebase.firestore.pipeline.Expr.arrayContainsAny;
import static com.google.firebase.firestore.pipeline.Expr.cosineDistance;
import static com.google.firebase.firestore.pipeline.Expr.endsWith;
import static com.google.firebase.firestore.pipeline.Expr.eq;
import static com.google.firebase.firestore.pipeline.Expr.euclideanDistance;
import static com.google.firebase.firestore.pipeline.Expr.field;
import static com.google.firebase.firestore.pipeline.Expr.gt;
import static com.google.firebase.firestore.pipeline.Expr.logicalMaximum;
import static com.google.firebase.firestore.pipeline.Expr.lt;
import static com.google.firebase.firestore.pipeline.Expr.lte;
import static com.google.firebase.firestore.pipeline.Expr.mapGet;
import static com.google.firebase.firestore.pipeline.Expr.neq;
import static com.google.firebase.firestore.pipeline.Expr.not;
import static com.google.firebase.firestore.pipeline.Expr.or;
import static com.google.firebase.firestore.pipeline.Expr.startsWith;
import static com.google.firebase.firestore.pipeline.Expr.strConcat;
import static com.google.firebase.firestore.pipeline.Expr.subtract;
import static com.google.firebase.firestore.pipeline.Expr.vector;
import static com.google.firebase.firestore.pipeline.Ordering.ascending;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Correspondence;
import com.google.firebase.firestore.pipeline.AggregateFunction;
import com.google.firebase.firestore.pipeline.AggregateStage;
import com.google.firebase.firestore.pipeline.Expr;
import com.google.firebase.firestore.pipeline.RawStage;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Collections;
import java.util.LinkedHashMap;
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
                  entry("awards", ImmutableMap.of("hugo", true, "nebula", true)))));

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
    assertThat(waitFor(execute).getResults()).hasSize(10);
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
        .containsExactly(ImmutableMap.of("count", 10));
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
                AggregateFunction.avg("rating").alias("avgRating"),
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
                AggregateStage.withAccumulators(AggregateFunction.avg("rating").alias("avgRating"))
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
                        ImmutableMap.of("avgRating", AggregateFunction.avg("rating")),
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
  @Ignore("Not supported yet")
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
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            mapOfEntries(entry("count", 10), entry("maxRating", 4.7), entry("minPublished", 1813)));
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
            mapOfEntries(entry("title", "The Handmaid's Tale"), entry("author", "Margaret Atwood")))
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
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "Pride and Prejudice"),
            ImmutableMap.of("title", "The Handmaid's Tale"),
            ImmutableMap.of("title", "1984"));
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
            .select(field("author").strConcat(" - ", field("title")).alias("bookInfo"))
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
  @Ignore("Not supported yet")
  public void testToLowercase() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select(field("title").toLower().alias("lowercaseTitle"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("lowercaseTitle", "the hitchhiker's guide to the galaxy"));
  }

  @Test
  @Ignore("Not supported yet")
  public void testToUppercase() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
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
            .select(
                add(field("rating"), 1).alias("ratingPlusOne"),
                subtract(field("published"), 1900).alias("yearsSince1900"),
                field("rating").multiply(10).alias("ratingTimesTen"),
                field("rating").divide(2).alias("ratingDividedByTwo"))
            .limit(1)
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
  @Ignore("Not supported yet")
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
  @Ignore("Not supported yet")
  public void testLogicalMin() {
    Task<PipelineSnapshot> execute =
        firestore.pipeline().collection(randomCol).sort(field("rating").ascending()).execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("min_rating", 4.2, "min_published", 1900));
  }

  @Test
  public void testMapGet() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
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
