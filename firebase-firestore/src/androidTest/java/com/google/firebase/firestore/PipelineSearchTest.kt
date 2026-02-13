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
package com.google.firebase.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.and
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.documentContainsText
import com.google.firebase.firestore.pipeline.Expression.Companion.documentSnippet
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.or
import com.google.firebase.firestore.pipeline.Expression.Companion.snippet
import com.google.firebase.firestore.pipeline.Expression.Companion.topicalityScore
import com.google.firebase.firestore.pipeline.Expression.SearchMode
import com.google.firebase.firestore.pipeline.SearchOptions
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class PipelineSearchTest {
    private val randomCol: CollectionReference? = null
    private val firestore: FirebaseFirestore? = null

    @Test
    fun basicSearch() {
        firestore!!.pipeline().collection("books")
            .search(
                SearchOptions()
                    .withQuery(documentContainsText("waffles"))
                    .withSort(topicalityScore().descending())
            )

        firestore.pipeline()
            .collection("books")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
                    .withSort(topicalityScore().descending())
            )

        firestore.pipeline()
            .collection("books")
            .search(
                SearchOptions()
                    .withQuery(
                        field("location")
                            .geoDistance(GeoPoint(38.989177, -107.065076))
                            .lessThan(1000 /* meters */)
                    )
            )

        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(
                        field("menu").searchFor("waffles", SearchMode.SEMANTIC_SEARCH)
                    )
            )



        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(
                        and(
                            field("menu").searchFor("waffles"),
                            field("description").searchFor("diner")
                        )
                    )
                    .withSort(topicalityScore().descending())
            )

        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(
                        and(
                            field("menu").searchFor("waffles"),
                            field("location")
                                .geoDistance(GeoPoint(38.989177, -107.065076))
                                .lessThan(1000 /* meters */)
                        )
                    )
                    .withSort(topicalityScore().descending())
            )

        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles").not())
            )

        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(documentContainsText("(waffles OR pancakes) AND eggs"))
            )


        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery("(waffles OR pancakes) AND eggs")
            )

        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery("menu:(waffles OR pancakes) AND description:\"breakfast all day\"")
            )

        firestore.pipeline()
            .collection("products")
            .search(
                SearchOptions()
                    .withQuery(
                        and(
                            documentContainsText("gaming laptop"),
                            field("ram").between(32, 48)
                        )
                    )
            )

        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("-shellfish AND (hamburger OR steak)"))
            )


        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
                    .withAddFields(
                        topicalityScore().alias("searchScore"),
                        snippet("menu", "waffles").alias("snippet")
                    )
            )


        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
                    .withSelect(
                        field("menu"),  // string will be accepted as shorthand for field("location")

                        "location",  // select the document ID

                        field(FieldPath.documentId()),

                        topicalityScore().alias("searchScore"),
                        snippet("menu", "waffles").alias("snippet")
                    )
            )


        // When `sort` is not specified, the query does not affect the sort order.
        // The documents returned by this query are newest to oldest.
        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
            )


        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
                    .withSort(topicalityScore().descending())
            )

        // Find restaurants with "waffles" on the menu, but order the results
        // only by distance to a query point.
        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
                    .withSort(
                        field("location")
                            .geoDistance(GeoPoint(38.989177, -107.065076))
                            .ascending()
                    )
            )

        // In this example bracket the computed geoDistance in 10km segments.
        // First sort by the distance bracket, then sort by search score.
        // Expected result is that restaurants within 10km appear first in the results
        // even if the topicality is lower than a document >10km away.
        firestore.pipeline()
            .collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
                    .withSort(
                        field("location")
                            .geoDistance(GeoPoint(38.989177, -107.065076))
                            .divide(10000)
                            .floor()
                            .ascending(),
                        topicalityScore().descending()
                    )
            )


        // Return the first 10 documents according to the sort order.
        // In this example it is the 10 documents with the highest topicality
        // because the topicalityScore sort order is used.
        firestore.pipeline().collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
                    .withSort(topicalityScore().descending())
                    .withLimit(10)
            )

        // Given a search-index with the pre-sort order set as:
        //   sort_order_field_path: "publicationDate"
        //   default_sort_order: 2 /* DESCENDING */
        //
        // Then the following search will score only the most recent 1000 `foodBlogPosts`
        // and return the 10 most topical documents for the query 'kona coffee'
        firestore.pipeline().collection("foodBlogPosts")
            .search(
                SearchOptions()
                    .withQuery(documentContainsText("kona coffee"))
                    .withSort(topicalityScore().descending())
                    .withLimit(10)
                    .withMaxToScore(1000)
            )


        val currentPage: Long = 0
        firestore.pipeline().collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles"))
                    .withLimit(10)
                    .withOffset(10 * (currentPage - 1)) // When implementing paging with offset, it's much cheaper
                // to sort by the default pre-sort order. Sorting by score
                // could be an O(N^2) operation.
                //.withSort(<default>)

            )

        firestore.pipeline().collection("foodBlogPosts")
            .search(
                SearchOptions()
                    .withQuery(field("body").searchFor("kona coffee"))
                    .withAddFields(
                        field("body").snippet("kona coffee").alias("snippet")
                    )
            )

        val rquery = "\"mac and cheese\""
        firestore.pipeline().collection("foodBlogPosts")
            .search(
                SearchOptions()
                    .withQuery(rquery)
                    .withAddFields(
                        Expression.Companion.concat(field("description"), field("summary"))
                            .snippet(rquery).alias("snippet")
                    )
            )


        val rqueryWithFields = "body:\"mac and cheese\" AND tags:quick"
        firestore.pipeline().collection("foodBlogPosts")
            .search(
                SearchOptions()
                    .withQuery(rquery)
                    .withAddFields(
                        documentSnippet(rqueryWithFields).alias("snippet")
                    )
            )


        firestore.pipeline().collection("foodBlogPosts")
            .search(
                SearchOptions()
                    .withQuery(
                        or(
                            field("menu").searchFor("waffles"),
                            field("description").searchFor("\"breakfast all day\"")
                        )
                    )
                    .withAddFields(
                        Expression.Companion.concat(
                            field("menu").snippet("waffle"),
                            constant("\n"),
                            field("description").snippet("\"breakfast all day\"")
                        ).alias("snippet")
                    )
            )

        firestore.pipeline().collection("restaurants")
            .search(
                SearchOptions()
                    .withQuery(field("menu").searchFor("waffles", SearchMode.SEMANTIC_SEARCH))
                    .withAddFields(
                        field("menu")
                            .snippet(Expression.SnippetOptions("kona coffee")
                                .withMaxSnippetWidth(2000)
                                .withMaxSnippets(2)
                                .withSeparator("...")
                                .withSearchMode(SearchMode.SEMANTIC_SEARCH))
                            .alias("snippet")
                    )
            )

        firestore.pipeline().collection("emails")
            .search(
                SearchOptions()
                    .withQuery(field("body").searchFor("urgent"))
                    .withPartition("email", "user@domain")
            )

        firestore.pipeline().collection("emails")
            .search(
                SearchOptions()
                    .withQuery(field("body").searchFor("urgent"))
                    .withPartition(mapOf(
                            "email" to "user@domain",
                            "folder" to "inbox"
                        )
                    )
            )
    }
}

