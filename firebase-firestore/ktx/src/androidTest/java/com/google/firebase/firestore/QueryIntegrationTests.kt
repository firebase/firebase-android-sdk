package com.google.firebase.firestore

import android.util.Log
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.firestore.testutil.testFirestore
import com.google.firebase.firestore.testutil.waitFor
import org.junit.Test

class QueryIntegrationTests {
//    @Serializable
    data class City(
        val name: String? = null,
        val state: String? = null,
        val country: String? = null,
//        @field:JvmField // use this annotation if your Boolean field is prefixed with 'is'
//        @SerialName("capital")
        @PropertyName("capital")
        val isCapital: Boolean? = null,
        val population: Long? = null,
        val regions: List<String>? = null
    )

    val cities = testFirestore.collection("cities")
    val TAG = "TestLog"
    val data1 = hashMapOf(
        "name" to "San Francisco",
        "state" to "CA",
        "country" to "USA",
        "capital" to false,
        "population" to 860000,
        "regions" to listOf("west_coast", "norcal")
    )

    val data2 = hashMapOf(
        "name" to "Los Angeles",
        "state" to "CA",
        "country" to "USA",
        "capital" to false,
        "population" to 3900000,
        "regions" to listOf("west_coast", "socal")
    )

    val data3 = hashMapOf(
        "name" to "Washington D.C.",
        "state" to null,
        "country" to "USA",
        "capital" to true,
        "population" to 680000,
        "regions" to listOf("east_coast")
    )

    val data4 = hashMapOf(
        "name" to "Tokyo",
        "state" to null,
        "country" to "Japan",
        "capital" to true,
        "population" to 9000000,
        "regions" to listOf("kanto", "honshu")
    )

    val data5 = hashMapOf(
        "name" to "Beijing",
        "state" to null,
        "country" to "China",
        "capital" to true,
        "population" to 21500000,
        "regions" to listOf("jingjinji", "hebei")
    )

    @Test
    fun explore_how_querry_works() {
        cities.document("SF").set(data1)
        cities.document("LA").set(data2)
        cities.document("DC").set(data3)
        cities.document("TOK").set(data4)
        cities.document("BJ").set(data5)

        // Create a reference to the cities collection
        val citiesRef = testFirestore.collection("cities")

        // Create a query against the collection.
        val query: Query = citiesRef.whereEqualTo("state", "CA")
        waitFor(query.get()).map {
            val city = it.toObject<City>() // queryDoc goes here
            Log.d("TestLog", city.toString())
        }
        Log.d("TestLog", "=".repeat(25) + " Now Test For QuerySnapshot.toObjects()")

        val querySnapshot = waitFor(query.get())
        val listOfCitiess = querySnapshot.toObjects<City>() // query get goes here
        for (city in listOfCitiess) {
            Log.d("TestLog", city.toString())
        }
    }
}
