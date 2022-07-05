package com.google.firebase.firestore

import android.util.Log
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.firestore.testutil.testFirestore
import com.google.firebase.firestore.testutil.waitFor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.Test

class TransactionIntegrationTests {
    @Serializable
    data class City(
        val name: String? = null,
        val state: String? = null,
        val country: String? = null,
//        @field:JvmField // use this annotation if your Boolean field is prefixed with 'is'
        @SerialName("capital")
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
    fun explore_how_transcation_works() {
        cities.document("SF").set(data1)
        cities.document("LA").set(data2)
        cities.document("DC").set(data3)
        cities.document("TOK").set(data4)
        cities.document("BJ").set(data5)

        // Create a reference to the cities collection
        val citiesRef = testFirestore.collection("cities")
        val sfDocRef = testFirestore.collection("cities").document("SF")

        fun myTransactionfunction(transaction: Transaction){
            val snapshot = transaction.get(sfDocRef)
            transaction.set(sfDocRef, City())
        }

//        val run = waitFor(testFirestore.runTransaction())
        testFirestore.runTransaction { transaction ->
            val snapshot = transaction.get(sfDocRef)

            // Note: this could be done without a transaction
            //       by updating the population using FieldValue.increment()
            val newPopulation = snapshot.getDouble("population")!! + 1
            transaction.update(sfDocRef, "population", newPopulation)

            // Success
            null
        }.addOnSuccessListener { Log.d(TAG, "Transaction success!") }
            .addOnFailureListener { e -> Log.w(TAG, "Transaction failure.", e) }



        val nycRef = testFirestore.collection("cities").document("NYC")
        val sfRef = testFirestore.collection("cities").document("SF")
        val laRef = testFirestore.collection("cities").document("LA")

        testFirestore.runBatch { batch ->
            // Set the value of 'NYC'
            batch.set(nycRef, City())

            // Update the population of 'SF'
            batch.update(sfRef, "population", 1000000L)

            // Delete the city 'LA'
            batch.delete(laRef)
        }.addOnCompleteListener {
            // ...
        }
    }
}