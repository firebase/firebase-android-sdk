// Copyright 2022 Google LLC
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
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.gson.Gson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryIntegrationTests {

    private data class JavaCity(
        val name: String? = null,
        val state: String? = null,
        val country: String? = null,
        // use this annotation if your Boolean field is prefixed with 'is' during encoding
        @field:JvmField @PropertyName("capital") val isCapital: Boolean? = null,
        val population: Long? = null,
        val regions: List<String>? = null,
        @DocumentId val docId: String? = null
    )

    @Serializable
    private data class KtxCity(
        val name: String,
        val state: String,
        val country: String,
        @SerialName("capital") val isCapital: Boolean,
        val population: Long,
        val regions: List<String>,
        @DocumentId val docId: String
    )

    // Create a reference to host the collection for cities
    private val cities =
        testFirestore.collection("cities").apply {
            document("SF").set(data1)
            document("LA").set(data2)
            document("DC").set(data3)
            document("TOK").set(data4)
            document("BJ").set(data5)
        }

    @Test
    fun ktx_query_serialization_is_equivalent_to_java() {
        // Test for QueryDocumentSnapshot
        val query: Query = cities.whereEqualTo("state", "CA")
        waitFor(query.get()).map {
            val javaCity = it.withoutCustomMappers { toObject<JavaCity>() } as JavaCity
            val ktxCity = it.toObject<KtxCity>()
            assertThat(Gson().toJson(ktxCity)).isEqualTo(Gson().toJson(javaCity))
        }

        // Test for QuerySnapshot
        val querySnapshot: QuerySnapshot = waitFor(query.get())
        val listOfJavaCities =
            querySnapshot.withoutCustomMappers { toObjects<JavaCity>() } as List<JavaCity>
        val listOfKtxCities = querySnapshot.toObjects<KtxCity>()

        for (i in listOfJavaCities.indices) {
            val javaCity = listOfJavaCities.get(i)
            val ktxCity = listOfKtxCities.get(i)
            assertThat(Gson().toJson(ktxCity)).isEqualTo(Gson().toJson(javaCity))
        }
    }

    @Test
    fun transaction_set_method_should_be_the_same() {
        val javaTransactionDocRef = testFirestore.collection("java_cities").document("transaction")
        val ktxTransactionDocRef = testFirestore.collection("ktx_cities").document("transaction")
        val javaCity =
            JavaCity("Waterloo", "ON", "CA", false, 9999, listOf("west", "south"), "foo/bar")
        val ktxCity =
            KtxCity("Waterloo", "ON", "CA", false, 9999, listOf("west", "south"), "foo/bar")

        waitFor(
            testFirestore.runTransaction { transaction: Transaction ->
                transaction.set(javaTransactionDocRef, javaCity)
                transaction.set(ktxTransactionDocRef, ktxCity)
                null
            }
        )
        val javaSnapshot = waitFor(javaTransactionDocRef.get()).data
        val ktxSnapshot = waitFor(ktxTransactionDocRef.get()).data
        assertThat(ktxSnapshot).containsExactlyEntriesIn(javaSnapshot)
    }

    @Test
    fun batchWrite_set_method_should_be_the_same() {
        val javaTransactionDocRef = testFirestore.collection("java_cities").document("transaction")
        val ktxTransactionDocRef = testFirestore.collection("ktx_cities").document("transaction")
        val javaCity =
            JavaCity("Waterloo", "ON", "CA", false, 9999, listOf("west", "south"), "foo/bar")
        val ktxCity =
            KtxCity("Waterloo", "ON", "CA", false, 9999, listOf("west", "south"), "foo/bar")

        waitFor(
            testFirestore.runBatch { batch: WriteBatch ->
                batch.set(javaTransactionDocRef, javaCity)
                batch.set(ktxTransactionDocRef, ktxCity)
            }
        )
        val javaSnapshot = waitFor(javaTransactionDocRef.get()).data
        val ktxSnapshot = waitFor(ktxTransactionDocRef.get()).data
        assertThat(ktxSnapshot).containsExactlyEntriesIn(javaSnapshot)
    }
}

private val data1 =
    hashMapOf(
        "name" to "San Francisco",
        "state" to "CA",
        "country" to "USA",
        "capital" to false,
        "population" to 860000,
        "regions" to listOf("west_coast", "norcal")
    )

private val data2 =
    hashMapOf(
        "name" to "Los Angeles",
        "state" to "CA",
        "country" to "USA",
        "capital" to false,
        "population" to 3900000,
        "regions" to listOf("west_coast", "socal")
    )

private val data3 =
    hashMapOf(
        "name" to "Washington D.C.",
        "state" to null,
        "country" to "USA",
        "capital" to true,
        "population" to 680000,
        "regions" to listOf("east_coast")
    )

private val data4 =
    hashMapOf(
        "name" to "Tokyo",
        "state" to null,
        "country" to "Japan",
        "capital" to true,
        "population" to 9000000,
        "regions" to listOf("kanto", "honshu")
    )

private val data5 =
    hashMapOf(
        "name" to "Beijing",
        "state" to null,
        "country" to "China",
        "capital" to true,
        "population" to 21500000,
        "regions" to listOf("jingjinji", "hebei")
    )
