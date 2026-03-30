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
package com.google.firebase.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.pipeline.Expression.Companion.and
import com.google.firebase.firestore.pipeline.Expression.Companion.documentMatches
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.score
import com.google.firebase.firestore.pipeline.SearchStage
import com.google.firebase.firestore.testutil.IntegrationTestUtil
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipelineSearchTest {
  companion object {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var restaurantsCollection: CollectionReference

    private const val COLLECTION_NAME = "TextSearchIntegrationTests"

    private val restaurantDocs: Map<String, Map<String, Any>> =
      mapOf(
        "sunnySideUp" to
          mapOf(
            "name" to "The Sunny Side Up",
            "description" to
              "A cozy neighborhood diner serving classic breakfast favorites all day long, from fluffy pancakes to savory omelets.",
            "location" to GeoPoint(39.7541, -105.0002),
            "menu" to
              "<h3>Breakfast Classics</h3><ul><li>Denver Omelet - $12</li><li>Buttermilk Pancakes - $10</li><li>Steak and Eggs - $16</li></ul><h3>Sides</h3><ul><li>Hash Browns - $4</li><li>Thick-cut Bacon - $5</li><li>Drip Coffee - $2</li></ul>",
            "average_price_per_person" to 15
          ),
        "goldenWaffle" to
          mapOf(
            "name" to "The Golden Waffle",
            "description" to
              "Specializing exclusively in Belgian-style waffles. Open daily from 6:00 AM to 11:00 AM.",
            "location" to GeoPoint(39.7183, -104.9621),
            "menu" to
              "<h3>Signature Waffles</h3><ul><li>Strawberry Delight - $11</li><li>Chicken and Waffles - $14</li><li>Chocolate Chip Crunch - $10</li></ul><h3>Drinks</h3><ul><li>Fresh OJ - $4</li><li>Artisan Coffee - $3</li></ul>",
            "average_price_per_person" to 13
          ),
        "lotusBlossomThai" to
          mapOf(
            "name" to "Lotus Blossom Thai",
            "description" to
              "Authentic Thai cuisine featuring hand-crushed spices and traditional family recipes from the Chiang Mai region.",
            "location" to GeoPoint(39.7315, -104.9847),
            "menu" to
              "<h3>Appetizers</h3><ul><li>Spring Rolls - $7</li><li>Chicken Satay - $9</li></ul><h3>Main Course</h3><ul><li>Pad Thai - $15</li><li>Green Curry - $16</li><li>Drunken Noodles - $15</li></ul>",
            "average_price_per_person" to 22
          ),
        "mileHighCatch" to
          mapOf(
            "name" to "Mile High Catch",
            "description" to
              "Freshly sourced seafood offering a wide variety of Pacific fish and Atlantic shellfish in an upscale atmosphere.",
            "location" to GeoPoint(39.7401, -104.9903),
            "menu" to
              "<h3>From the Raw Bar</h3><ul><li>Oysters (Half Dozen) - $18</li><li>Lobster Cocktail - $22</li></ul><h3>Entrees</h3><ul><li>Pan-Seared Salmon - $28</li><li>King Crab Legs - $45</li><li>Fish and Chips - $19</li></ul>",
            "average_price_per_person" to 45
          ),
        "peakBurgers" to
          mapOf(
            "name" to "Peak Burgers",
            "description" to
              "Casual burger joint focused on locally sourced Colorado beef and hand-cut fries.",
            "location" to GeoPoint(39.7622, -105.0125),
            "menu" to
              "<h3>Burgers</h3><ul><li>The Peak Double - $12</li><li>Bison Burger - $15</li><li>Veggie Stack - $11</li></ul><h3>Sides</h3><ul><li>Truffle Fries - $6</li><li>Onion Rings - $5</li></ul>",
            "average_price_per_person" to 18
          ),
        "solTacos" to
          mapOf(
            "name" to "El Sol Tacos",
            "description" to
              "A vibrant street-side taco stand serving up quick, delicious, and traditional Mexican street food.",
            "location" to GeoPoint(39.6952, -105.0274),
            "menu" to
              "<h3>Tacos ($3.50 each)</h3><ul><li>Al Pastor</li><li>Carne Asada</li><li>Pollo Asado</li><li>Nopales (Cactus)</li></ul><h3>Beverages</h3><ul><li>Horchata - $4</li><li>Mexican Coke - $3</li></ul>",
            "average_price_per_person" to 12
          ),
        "eastsideTacos" to
          mapOf(
            "name" to "Eastside Cantina",
            "description" to
              "Authentic street tacos and hand-shaken margaritas on the vibrant east side of the city.",
            "location" to GeoPoint(39.735, -104.885),
            "menu" to
              "<h3>Tacos</h3><ul><li>Carnitas Tacos - $4</li><li>Barbacoa Tacos - $4.50</li><li>Shrimp Tacos - $5</li></ul><h3>Drinks</h3><ul><li>House Margarita - $9</li><li>Jarritos - $3</li></ul>",
            "average_price_per_person" to 18
          ),
        "eastsideChicken" to
          mapOf(
            "name" to "Eastside Chicken",
            "description" to "Fried chicken to go - next to Eastside Cantina.",
            "location" to GeoPoint(39.735, -104.885),
            "menu" to
              "<h3>Fried Chicken</h3><ul><li>Drumstick - $4</li><li>Wings - $1</li><li>Sandwich - $9</li></ul><h3>Drinks</h3><ul><li>House Margarita - $9</li><li>Jarritos - $3</li></ul>",
            "average_price_per_person" to 12
          )
      )

    @JvmStatic
    @BeforeClass
    fun setupRestaurantDocs() {
      firestore = IntegrationTestUtil.testFirestore()
      restaurantsCollection = firestore.collection(COLLECTION_NAME)

      val collectionSnapshot = IntegrationTestUtil.waitFor(restaurantsCollection.get())
      val expectedDocIds = restaurantDocs.keys
      val deletes = mutableListOf<Task<Void>>()
      for (ds in collectionSnapshot.documents) {
        if (!expectedDocIds.contains(ds.id)) {
          deletes.add(ds.reference.delete())
        }
      }
      IntegrationTestUtil.waitFor(Tasks.whenAll(deletes))

      // Add/overwrite all restaurant docs
      val writes = mutableListOf<Task<Void>>()
      for ((id, data) in restaurantDocs) {
        writes.add(restaurantsCollection.document(id).set(data))
      }
      IntegrationTestUtil.waitFor(Tasks.whenAll(writes))
    }
  }

  @Before
  fun beforeTest() {
    Assume.assumeTrue(
      IntegrationTestUtil.getTargetBackend() == IntegrationTestUtil.TargetBackend.NIGHTLY
    )

    Assume.assumeTrue(
      IntegrationTestUtil.getBackendEdition() == IntegrationTestUtil.BackendEdition.ENTERPRISE
    )
  }

  private fun assertResultIds(snapshot: Pipeline.Snapshot, vararg ids: String) {
    val resultIds = snapshot.results.mapNotNull { it.getId() }
    assertThat(resultIds).containsExactly(*ids).inOrder()
  }

  // =========================================================================
  // Search stage
  // =========================================================================

  // --- DISABLE query expansion ---

  // query
  // TODO(search) enable with backend support
  //  @Test
  //  fun searchWithLanguageCode() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery("waffles")
  //            .withLanguageCode("en")
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "goldenWaffle")
  //  }

  @Test
  fun searchFullDocument() {
    val ppl =
      firestore
        .pipeline()
        .collection(COLLECTION_NAME)
        .search(
          SearchStage(
            query = documentMatches("waffles"),
            //                queryEnhancement = SearchStage.QueryEnhancement.DISABLED
            )
        )

    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
    assertResultIds(snapshot, "goldenWaffle")
  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun searchSpecificField() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(field("menu").matches("waffles"))
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "goldenWaffle")
  //  }

  @Test
  fun geoNearQuery() {
    val ppl =
      firestore
        .pipeline()
        .collection(COLLECTION_NAME)
        .search(
          SearchStage.withQuery(
            field("location").geoDistance(GeoPoint(39.6985, -105.024)).lessThanOrEqual(1000)
          )
          //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
          )

    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
    assertResultIds(snapshot, "solTacos")
  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun conjunctionOfTextSearchPredicates() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(
  //              and(field("menu").matches("waffles"), field("description").matches("diner"))
  //            )
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "goldenWaffle", "sunnySideUp")
  //  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun conjunctionOfTextSearchAndGeoNear() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(
  //              and(
  //                field("menu").matches("tacos"),
  //                field("location").geoDistance(GeoPoint(39.6985, -105.024)).lessThan(10_000)
  //              )
  //            )
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "solTacos")
  //  }

  @Test
  fun negateMatch() {
    val ppl =
      firestore
        .pipeline()
        .collection(COLLECTION_NAME)
        .search(
          SearchStage.withQuery(documentMatches("coffee -waffles"))
          //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
          )

    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
    assertResultIds(snapshot, "sunnySideUp")
  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun rquerySearchTheDocumentWithConjunctionAndDisjunction() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(documentMatches("(waffles OR pancakes) AND coffee"))
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "goldenWaffle", "sunnySideUp")
  //  }

  @Test
  fun rqueryAsQueryParam() {
    val ppl =
      firestore
        .pipeline()
        .collection(COLLECTION_NAME)
        .search(
          SearchStage.withQuery("chicken wings")
          //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
          )

    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
    assertResultIds(snapshot, "eastsideChicken")
  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun rquerySupportsFieldPaths() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery("menu:(waffles OR pancakes) AND description:\"breakfast all
  // day\"")
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "sunnySideUp")
  //  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun conjunctionOfRqueryAndExpression() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(
  //              and(
  //                documentMatches("tacos"),
  //                greaterThanOrEqual(field("average_price_per_person"), 8),
  //                lessThanOrEqual(field("average_price_per_person"), 15)
  //              )
  //            )
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "solTacos")
  //  }

  // --- REQUIRE query expansion ---

  // TODO(search) enable with backend support
  //  @Test
  //  fun requireQueryExpansion_searchFullDocument() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(documentMatches("waffles"))
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.REQUIRED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "goldenWaffle", "sunnySideUp")
  //  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun requireQueryExpansion_searchSpecificField() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(field("menu").matches("waffles"))
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.REQUIRED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "goldenWaffle", "sunnySideUp")
  //  }

  // add fields
  @Test
  fun addFields_score() {
    val ppl =
      firestore
        .pipeline()
        .collection(COLLECTION_NAME)
        .search(
          SearchStage.withQuery(documentMatches("waffles"))
            .withAddFields(
              score().alias("searchScore"),
              //              field("menu").snippet("waffles").alias("snippet")
              )
          //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
          )
        .select("name", "searchScore", "snippet")

    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
    assertThat(snapshot.results).hasSize(1)
    val result = snapshot.results[0]
    assertThat(result.get("name")).isEqualTo("The Golden Waffle")
    assertThat(result.get("searchScore") as Double).isGreaterThan(0.0)
    //    assertThat((result.get("snippet") as String).length).isGreaterThan(0)
  }

  //  @Test
  //  fun addFields_geoDistance() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(
  //                  field("location").geoDistance(GeoPoint(39.6985,
  // -105.024)).lessThanOrEqual(1000))
  //            .withAddFields(
  //              field("location").geoDistance(GeoPoint(39.6985, -105.024)).alias("distance"),
  //            )
  //          //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //          )
  //        .select("name", "distance")
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertThat(snapshot.results).hasSize(1)
  //    val result = snapshot.results[0]
  //    assertThat(result.get("name")).isEqualTo("The Golden Waffle")
  //    assertThat(result.get("distance") as Double).isGreaterThan(0.0)
  //  }

  // select
  // TODO(search) enable with backend support
  //  @Test
  //  fun select_topicalityScoreAndSnippet() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(field("menu").matches("waffles"))
  //            .withSelect(
  //              field("name"),
  //              field("location"),
  //              score().alias("searchScore"),
  //              snippet("menu", "waffles").alias("snippet")
  //            )
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertThat(snapshot.results).hasSize(1)
  //    val result = snapshot.results[0]
  //    assertThat(result.get("name")).isEqualTo("The Golden Waffle")
  //    assertThat(result.get("location")).isEqualTo(GeoPoint(39.7183, -104.9621))
  //    assertThat(result.get("searchScore") as Double).isGreaterThan(0.0)
  //    assertThat((result.get("snippet") as String).length).isGreaterThan(0)
  //    assertThat(result.getData().keys.sorted())
  //      .containsExactly("location", "name", "searchScore", "snippet")
  //  }

  // sort
  @Test
  fun sort_byScore() {
    val ppl =
      firestore
        .pipeline()
        .collection(COLLECTION_NAME)
        .search(
          SearchStage.withQuery(documentMatches("tacos")).withSort(score().descending())
          //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
          )

    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
    assertResultIds(snapshot, "eastsideTacos", "solTacos")
  }

  @Test
  fun sort_byDistance() {
    val ppl =
      firestore
        .pipeline()
        .collection(COLLECTION_NAME)
        .search(
          SearchStage.withQuery(
              field("location").geoDistance(GeoPoint(39.6985, -105.024)).lessThanOrEqual(5600)
            )
            .withSort(field("location").geoDistance(GeoPoint(39.6985, -105.024)).descending())
          //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
          )

    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
    assertResultIds(snapshot, "solTacos", "lotusBlossomThai", "mileHighCatch")
  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun sort_byMultipleOrderings() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(field("menu").matches("tacos OR chicken"))
  //            .withSort(
  //              field("location").geoDistance(GeoPoint(39.6985, -105.024)).ascending(),
  //              score().descending()
  //            )
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "solTacos", "eastsideTacos", "eastsideChicken")
  //  }

  // limit
  // TODO(search) enable with backend support
  //  @Test
  //  fun limit_limitsTheNumberOfDocumentsReturned() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(constant(true))
  //            .withSort(field("location").geoDistance(GeoPoint(39.6985, -105.024)).ascending())
  //            .withLimit(5)
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "solTacos", "lotusBlossomThai", "goldenWaffle")
  //  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun limit_limitsTheNumberOfDocumentsScored() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(field("menu").matches("chicken OR tacos OR fish OR waffles"))
  //            .withRetrievalDepth(6)
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "eastsideChicken", "eastsideTacos", "solTacos", "mileHighCatch")
  //  }

  // offset
  // TODO(search) enable with backend support
  //  @Test
  //  fun offset_skipsNDocuments() {
  //    val ppl =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(constant(true))
  //            .withLimit(2)
  //            .withOffset(2)
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot = IntegrationTestUtil.waitFor(ppl.execute())
  //    assertResultIds(snapshot, "eastsideChicken", "eastsideTacos")
  //  }

  // =========================================================================
  // Snippet
  // =========================================================================

  // TODO(search) enable with backend support
  //  @Test
  //  @Ignore("Snippet options not implemented yet")
  //  fun snippetOptions() {
  //    val ppl1 =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(field("menu").matches("waffles"))
  //            .withAddFields(
  //              field("menu")
  //                .snippet(SnippetOptions("waffles").withMaxSnippetWidth(10))
  //                .alias("snippet")
  //            )
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot1 = IntegrationTestUtil.waitFor(ppl1.execute())
  //    assertThat(snapshot1.results).hasSize(1)
  //    assertThat(snapshot1.results[0].get("name")).isEqualTo("The Golden Waffle")
  //    val snip1 = snapshot1.results[0].get("snippet") as String
  //    assertThat(snip1.length).isGreaterThan(0)
  //
  //    val ppl2 =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(field("menu").matches("waffles"))
  //            .withAddFields(
  //              field("menu")
  //                .snippet(SnippetOptions("waffles").withMaxSnippetWidth(1000))
  //                .alias("snippet")
  //            )
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot2 = IntegrationTestUtil.waitFor(ppl2.execute())
  //    assertThat(snapshot2.results).hasSize(1)
  //    assertThat(snapshot2.results[0].get("name")).isEqualTo("The Golden Waffle")
  //    val snip2 = snapshot2.results[0].get("snippet") as String
  //    assertThat(snip2.length).isGreaterThan(snip1.length)
  //  }

  // TODO(search) enable with backend support
  //  @Test
  //  fun snippetOnMultipleFields() {
  //    // Get snippet from 1 field
  //    val ppl1 =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(documentMatches("waffle"))
  //            .withAddFields(field("menu").snippet("waffles").alias("snippet"))
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot1 = IntegrationTestUtil.waitFor(ppl1.execute())
  //    assertThat(snapshot1.results).hasSize(1)
  //    assertThat(snapshot1.results[0].get("name")).isEqualTo("The Golden Waffle")
  //    val snip1 = snapshot1.results[0].get("snippet") as String
  //    assertThat(snip1.length).isGreaterThan(0)
  //
  //    // Get snippet from 2 fields
  //    val ppl2 =
  //      firestore
  //        .pipeline()
  //        .collection(COLLECTION_NAME)
  //        .search(
  //          SearchStage.withQuery(documentMatches("waffle"))
  //            .withAddFields(
  //              concat(field("menu"), field("description"))
  //                .snippet(SnippetOptions("waffles").withMaxSnippetWidth(2000))
  //                .alias("snippet")
  //            )
  //            .withQueryEnhancement(SearchStage.QueryEnhancement.DISABLED)
  //        )
  //
  //    val snapshot2 = IntegrationTestUtil.waitFor(ppl2.execute())
  //    assertThat(snapshot2.results).hasSize(1)
  //    assertThat(snapshot2.results[0].get("name")).isEqualTo("The Golden Waffle")
  //    val snip2 = snapshot2.results[0].get("snippet") as String
  //    assertThat(snip2.length).isGreaterThan(snip1.length)
  //  }
}
