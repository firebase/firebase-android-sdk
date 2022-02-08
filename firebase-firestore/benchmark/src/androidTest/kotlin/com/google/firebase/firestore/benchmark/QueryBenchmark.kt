package com.google.firebase.firestore.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.google.firebase.firestore.AccessHelper
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.testutil.IntegrationTestUtil
import com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor
import java.util.UUID
import kotlin.collections.Iterable
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class QueryBenchmark(
    var numberOfDocuments: Int,
    var numberOfResults: Int,
    var numberOfProperties: Int
) {
    private companion object {
        @JvmStatic
        val rootCollectionId = UUID.randomUUID()

        @JvmStatic
        @Parameterized.Parameters(name = "{index} - {0} docs, {1} results, {2} props")
        fun data(): Iterable<Array<Int>> {
            val documentCounts = arrayOf(500)
            val resultCounts = arrayOf(10, 50, 100)
            val propertyCounts = arrayOf(10, 50, 100)

            val combinations = mutableListOf<Array<Int>>()

            for (documentCount in documentCounts) {
                for (resultCount in resultCounts) {
                    for (propertyCount in propertyCounts) {
                        combinations.add(arrayOf(documentCount, resultCount, propertyCount))
                    }
                }
            }
            return combinations
        }
    }

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    lateinit var firestore: FirebaseFirestore
    lateinit var collection: CollectionReference

    @Before
    fun before() {
        firestore = IntegrationTestUtil.testFirestore()
        collection = firestore.collection(rootCollectionId.toString()).document("testcase")
        .collection("$numberOfDocuments docs with $numberOfProperties props")
    }

    @After
    fun after() {
        waitFor(firestore.terminate())
        waitFor(firestore.clearPersistence())
    }

    private fun initBatch(): WriteBatch {
        val data = mutableMapOf<String, Int>()
        for (i in 1..numberOfProperties) {
            data.put(Integer.toString(i), i)
        }
        val batch = firestore.batch()
        for (i in 1..numberOfDocuments) {
            data.put("count", i)
            batch.set(collection.document(), data)
        }
        return batch
    }

    private fun setUpOverlays() {
        firestore.disableNetwork()
        val batch = initBatch()
        batch.commit()
    }

    private fun setUpRemoteDocuments() {
        val snapshot = waitFor(collection.get())
        if (snapshot.isEmpty) {
            val batch = initBatch()
            waitFor(batch.commit())
            waitFor(collection.get())
        }
    }

    @Test
    fun overlaysWithoutIndex() {
        setUpOverlays()

        val query = collection.whereLessThanOrEqualTo("count", numberOfResults)
        benchmarkRule.measureRepeated {
            waitFor(query.get())
        }
    }

    @Test
    fun overlaysWithIndex() {
        setUpOverlays()
        setUpIndex()

        val query = collection.whereLessThanOrEqualTo("count", numberOfResults)
        benchmarkRule.measureRepeated {
            waitFor(query.get())
        }
    }

    private fun setUpIndex() {
        AccessHelper.setIndexConfiguration(firestore, "{\n" +
                "  indexes: [\n" +
                "    { \n" +
                "      collectionGroup: \"" + collection.id + "\",\n" +
                "      fields: [\n" +
                "        { fieldPath: \"count\", order: \"ASCENDING\"}\n" +
                "      ]\n" +
                "    }\n" +
                "  ]}\n")
        waitFor(AccessHelper.forceBackfill(firestore))
    }

    @Test
    fun remoteDocumentsWithoutIndex() {
        setUpRemoteDocuments()

        val query = collection.whereLessThanOrEqualTo("count", numberOfResults)
        benchmarkRule.measureRepeated {
            waitFor(query.get(Source.CACHE))
        }
    }

    @Test
    fun remoteDocumentsWithIndex() {
        setUpRemoteDocuments()
        setUpIndex()

        val query = collection.whereLessThanOrEqualTo("count", numberOfResults)
        benchmarkRule.measureRepeated {
            waitFor(query.get(Source.CACHE))
        }
    }

    @Test
    fun remoteDocumentsWithIndexFree() {
        setUpRemoteDocuments()

        val query = collection.whereLessThanOrEqualTo("count", numberOfResults)
        waitFor(query.get())
        benchmarkRule.measureRepeated {
            waitFor(query.get(Source.CACHE))
        }
    }
}
