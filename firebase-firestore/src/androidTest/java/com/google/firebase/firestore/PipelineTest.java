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
import static com.google.firebase.firestore.pipeline.Function.add;
import static com.google.firebase.firestore.pipeline.Function.and;
import static com.google.firebase.firestore.pipeline.Function.arrayContains;
import static com.google.firebase.firestore.pipeline.Function.arrayContainsAny;
import static com.google.firebase.firestore.pipeline.Function.endsWith;
import static com.google.firebase.firestore.pipeline.Function.eq;
import static com.google.firebase.firestore.pipeline.Function.gt;
import static com.google.firebase.firestore.pipeline.Function.lt;
import static com.google.firebase.firestore.pipeline.Function.lte;
import static com.google.firebase.firestore.pipeline.Function.neq;
import static com.google.firebase.firestore.pipeline.Function.not;
import static com.google.firebase.firestore.pipeline.Function.or;
import static com.google.firebase.firestore.pipeline.Function.startsWith;
import static com.google.firebase.firestore.pipeline.Function.strConcat;
import static com.google.firebase.firestore.pipeline.Function.subtract;
import static com.google.firebase.firestore.pipeline.Ordering.ascending;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static java.util.Map.entry;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Correspondence;
import com.google.firebase.firestore.pipeline.Accumulator;
import com.google.firebase.firestore.pipeline.AggregateStage;
import com.google.firebase.firestore.pipeline.Field;
import com.google.firebase.firestore.pipeline.Function;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
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

  private Map<String, Map<String, Object>> bookDocs =
      ImmutableMap.ofEntries(
          entry(
              "book1",
              ImmutableMap.ofEntries(
                  entry("title", "The Hitchhiker's Guide to the Galaxy"),
                  entry("author", "Douglas Adams"),
                  entry("genre", "Science Fiction"),
                  entry("published", 1979),
                  entry("rating", 4.2),
                  entry("tags", ImmutableList.of("comedy", "space", "adventure")),
                  entry("awards", ImmutableMap.of("hugo", true, "nebula", false)))),
          entry(
              "book2",
              ImmutableMap.ofEntries(
                  entry("title", "Pride and Prejudice"),
                  entry("author", "Jane Austen"),
                  entry("genre", "Romance"),
                  entry("published", 1813),
                  entry("rating", 4.5),
                  entry("tags", ImmutableList.of("classic", "social commentary", "love")),
                  entry("awards", ImmutableMap.of("none", true)))),
          entry(
              "book3",
              ImmutableMap.ofEntries(
                  entry("title", "One Hundred Years of Solitude"),
                  entry("author", "Gabriel García Márquez"),
                  entry("genre", "Magical Realism"),
                  entry("published", 1967),
                  entry("rating", 4.3),
                  entry("tags", ImmutableList.of("family", "history", "fantasy")),
                  entry("awards", ImmutableMap.of("nobel", true, "nebula", false)))),
          entry(
              "book4",
              ImmutableMap.ofEntries(
                  entry("title", "The Lord of the Rings"),
                  entry("author", "J.R.R. Tolkien"),
                  entry("genre", "Fantasy"),
                  entry("published", 1954),
                  entry("rating", 4.7),
                  entry("tags", ImmutableList.of("adventure", "magic", "epic")),
                  entry("awards", ImmutableMap.of("hugo", false, "nebula", false)))),
          entry(
              "book5",
              ImmutableMap.ofEntries(
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
              ImmutableMap.ofEntries(
                  entry("title", "Crime and Punishment"),
                  entry("author", "Fyodor Dostoevsky"),
                  entry("genre", "Psychological Thriller"),
                  entry("published", 1866),
                  entry("rating", 4.3),
                  entry("tags", ImmutableList.of("philosophy", "crime", "redemption")),
                  entry("awards", ImmutableMap.of("none", true)))),
          entry(
              "book7",
              ImmutableMap.ofEntries(
                  entry("title", "To Kill a Mockingbird"),
                  entry("author", "Harper Lee"),
                  entry("genre", "Southern Gothic"),
                  entry("published", 1960),
                  entry("rating", 4.2),
                  entry("tags", ImmutableList.of("racism", "injustice", "coming-of-age")),
                  entry("awards", ImmutableMap.of("pulitzer", true)))),
          entry(
              "book8",
              ImmutableMap.ofEntries(
                  entry("title", "1984"),
                  entry("author", "George Orwell"),
                  entry("genre", "Dystopian"),
                  entry("published", 1949),
                  entry("rating", 4.2),
                  entry("tags", ImmutableList.of("surveillance", "totalitarianism", "propaganda")),
                  entry("awards", ImmutableMap.of("prometheus", true)))),
          entry(
              "book9",
              ImmutableMap.ofEntries(
                  entry("title", "The Great Gatsby"),
                  entry("author", "F. Scott Fitzgerald"),
                  entry("genre", "Modernist"),
                  entry("published", 1925),
                  entry("rating", 4.0),
                  entry("tags", ImmutableList.of("wealth", "american dream", "love")),
                  entry("awards", ImmutableMap.of("none", true)))),
          entry(
              "book10",
              ImmutableMap.ofEntries(
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
            .aggregate(Accumulator.countAll().as("count"))
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
            .where(Function.eq("genre", "Science Fiction"))
            .aggregate(
                Accumulator.countAll().as("count"),
                Accumulator.avg("rating").as("avgRating"),
                Field.of("rating").max().as("maxRating"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.ofEntries(
                entry("count", 10), entry("avgRating", 4.4), entry("maxRating", 4.6)));
  }

  @Test
  public void groupAndAccumulateResults() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .where(lt(Field.of("published"), 1984))
            .aggregate(
                AggregateStage.withAccumulators(Accumulator.avg("rating").as("avgRating"))
                    .withGroups("genre"))
            .where(gt("avgRating", 4.3))
            .sort(Field.of("avgRating").descending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.ofEntries(entry("avgRating", 4.7), entry("genre", "Fantasy")),
            ImmutableMap.ofEntries(entry("avgRating", 4.5), entry("genre", "Romance")),
            ImmutableMap.ofEntries(entry("avgRating", 4.4), entry("genre", "Science Fiction")));
  }

  @Test
  @Ignore("Not supported yet")
  public void minAndMaxAccumulations() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .aggregate(
                Accumulator.countAll().as("count"),
                Field.of("rating").max().as("maxRating"),
                Field.of("published").min().as("minPublished"))
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.ofEntries(
                entry("count", 10), entry("maxRating", 4.7), entry("minPublished", 1813)));
  }

  @Test
  public void canSelectFields() {
    Task<PipelineSnapshot> execute =
        firestore
            .pipeline()
            .collection(randomCol)
            .select("title", "author")
            .sort(Field.of("author").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.ofEntries(
                entry("title", "The Hitchhiker's Guide to the Galaxy"),
                entry("author", "Douglas Adams")),
            ImmutableMap.ofEntries(
                entry("title", "The Great Gatsby"), entry("author", "F. Scott Fitzgerald")),
            ImmutableMap.ofEntries(entry("title", "Dune"), entry("author", "Frank Herbert")),
            ImmutableMap.ofEntries(
                entry("title", "Crime and Punishment"), entry("author", "Fyodor Dostoevsky")),
            ImmutableMap.ofEntries(
                entry("title", "One Hundred Years of Solitude"),
                entry("author", "Gabriel García Márquez")),
            ImmutableMap.ofEntries(entry("title", "1984"), entry("author", "George Orwell")),
            ImmutableMap.ofEntries(
                entry("title", "To Kill a Mockingbird"), entry("author", "Harper Lee")),
            ImmutableMap.ofEntries(
                entry("title", "The Lord of the Rings"), entry("author", "J.R.R. Tolkien")),
            ImmutableMap.ofEntries(
                entry("title", "Pride and Prejudice"), entry("author", "Jane Austen")),
            ImmutableMap.ofEntries(
                entry("title", "The Handmaid's Tale"), entry("author", "Margaret Atwood")))
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
            ImmutableMap.ofEntries(entry("title", "1984"), entry("author", "George Orwell")),
            ImmutableMap.ofEntries(
                entry("title", "To Kill a Mockingbird"), entry("author", "Harper Lee")),
            ImmutableMap.ofEntries(
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
            .where(Field.of("tags").arrayContainsAll(ImmutableList.of("adventure", "magic")))
            .select("title")
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("title", "The Lord of the Rings"));
  }

  @Test
  public void arrayLengthWorks() {
    Task<PipelineSnapshot> execute =
        randomCol
            .pipeline()
            .select(Field.of("tags").arrayLength().as("tagsCount"))
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
                Field.of("tags")
                    .arrayConcat(ImmutableList.of("newTag1", "newTag2"))
                    .as("modifiedTags"))
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
        randomCol
            .pipeline()
            .select(Field.of("author").strConcat(" - ", Field.of("title")).as("bookInfo"))
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
        randomCol
            .pipeline()
            .where(startsWith("title", "The"))
            .select("title")
            .sort(Field.of("title").ascending())
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
        randomCol
            .pipeline()
            .where(endsWith("title", "y"))
            .select("title")
            .sort(Field.of("title").descending())
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
        randomCol
            .pipeline()
            .select(Field.of("title").charLength().as("titleLength"), Field.of("title"))
            .where(gt("titleLength", 20))
            .sort(Field.of("title").ascending())
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
        randomCol
            .pipeline()
            .select(Field.of("title").toLower().as("lowercaseTitle"))
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
        randomCol
            .pipeline()
            .select(Field.of("author").toLower().as("uppercaseAuthor"))
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
        randomCol
            .pipeline()
            .addFields(strConcat(" ", Field.of("title"), " ").as("spacedTitle"))
            .select(Field.of("spacedTitle").trim().as("trimmedTitle"))
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
        randomCol.pipeline().where(Function.like("title", "%Guide%")).select("title").execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(ImmutableMap.of("title", "The Hitchhiker's Guide to the Galaxy"));
  }

  @Test
  public void testRegexContains() {
    Task<PipelineSnapshot> execute =
        randomCol.pipeline().where(Function.regexContains("title", "(?i)(the|of)")).execute();
    assertThat(waitFor(execute).getResults()).hasSize(5);
  }

  @Test
  public void testRegexMatches() {
    Task<PipelineSnapshot> execute =
        randomCol.pipeline().where(Function.regexContains("title", ".*(?i)(the|of).*")).execute();
    assertThat(waitFor(execute).getResults()).hasSize(5);
  }

  @Test
  public void testArithmeticOperations() {
    Task<PipelineSnapshot> execute =
        randomCol
            .pipeline()
            .select(
                add(Field.of("rating"), 1).as("ratingPlusOne"),
                subtract(Field.of("published"), 1900).as("yearsSince1900"),
                Field.of("rating").multiply(10).as("ratingTimesTen"),
                Field.of("rating").divide(2).as("ratingDividedByTwo"))
            .limit(1)
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.ofEntries(
                entry("ratingPlusOne", 5.2),
                entry("yearsSince1900", 79),
                entry("ratingTimesTen", 42),
                entry("ratingDividedByTwo", 2.1)));
  }

  @Test
  public void testComparisonOperators() {
    Task<PipelineSnapshot> execute =
        randomCol
            .pipeline()
            .where(
                and(
                    gt("rating", 4.2),
                    lte(Field.of("rating"), 4.5),
                    neq("genre", "Science Function")))
            .select("rating", "title")
            .sort(Field.of("title").ascending())
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
        randomCol
            .pipeline()
            .where(
                or(
                    and(gt("rating", 4.5), eq("genre", "Science Fiction")),
                    lt(Field.of("published"), 1900)))
            .select("title")
            .sort(Field.of("title").ascending())
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
        randomCol
            .pipeline()
            .where(not(Field.of("rating").isNan()))
            .select(Field.of("rating").eq(null).as("ratingIsNull"))
            .select("title")
            .sort(Field.of("title").ascending())
            .execute();
    assertThat(waitFor(execute).getResults())
        .comparingElementsUsing(DATA_CORRESPONDENCE)
        .containsExactly(
            ImmutableMap.of("title", "Crime and Punishment"),
            ImmutableMap.of("title", "Dune"),
            ImmutableMap.of("title", "Pride and Prejudice"));
  }

  @Test
  public void testMapGet() {}

  @Test
  public void testParent() {}

  @Test
  public void testCollectionId() {}

  @Test
  public void testDistanceFunctions() {}

  @Test
  public void testNestedFields() {}

  @Test
  public void testMapGetWithFieldNameIncludingNotation() {}
}
