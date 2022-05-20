package com.example.firestore_kotlin_serialization

import android.util.Log
import com.example.firestore_kotlin_serialization.testutil.IntegrationTestUtil
import com.example.firestore_kotlin_serialization.testutil.IntegrationTestUtil.Companion.testDocument
import com.example.firestore_kotlin_serialization.testutil.IntegrationTestUtil.Companion.waitFor
import com.google.firebase.firestore.ktx.toObject
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Test

class NestedMapSerializationIntegrationTest {

    companion object {
        private const val TAG = "LogTest"
        private val firestore = IntegrationTestUtil.testFirestore
    }

    @Serializable
    data class Project(val name: String? = null, val owner: String? = null)

    @Serializable
    data class Owner(val name: String? = null)

    @Serializable
    data class ObjectInsideOfObject(val name: String? = null, val owner: Owner? = null)

    @Serializable
    data class ListOfObjectsInsideOfObject(
        val name: String? = null,
        val listOfOwner: List<Owner?>? = null
    )


    val docRef = firestore.collection("collection").document("docPath")
    val docRef1 = firestore.collection("collection1").document("docPath")

    @Test
    fun testSerializationSetMethodSameAsPOJOSet() {
        val docRefKotlin = testDocument("kotlin_set")
        val docRefPOJO = testDocument("pojo_set")
        val project1 = Project()
        val project2 = Project("x")
        val project3 = Project("x", "y")
        val project4 = Project(null, null)
        val projectList = listOf(project1, project2, project3, project4)

        for (project in projectList) {
            docRefKotlin.set<Project>(project)
            docRefPOJO.set(project)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testSerializationSetMethodWorksForNestedObject() {
        val docRefKotlin = testDocument("kotlin_set")
        val docRefPOJO = testDocument("pojo_set")
        val project1 = ObjectInsideOfObject()
        val project2 = ObjectInsideOfObject("x")
        val project3 = ObjectInsideOfObject("x", Owner())
        val project4 = ObjectInsideOfObject("x", Owner("yyy"))
        val project5 = ObjectInsideOfObject("x", Owner(name = null))
        val projectList = listOf(project1, project2, project3, project4, project5)

        for (project in projectList) {
            docRefKotlin.set<ObjectInsideOfObject>(project)
            docRefPOJO.set(project)
            val expected = waitFor(docRefPOJO.get()).data
            Log.d(TAG, "${expected}")
            val actual = waitFor(docRefKotlin.get()).data
            Log.d(TAG, "${actual}")
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testSerializationSetMethodWorksForList() {
        val docRefKotlin = testDocument("kotlin_set")
        val docRefPOJO = testDocument("pojo_set")
        val project1 = ListOfObjectsInsideOfObject()
        val project2 = ListOfObjectsInsideOfObject("x")
        val project3 = ListOfObjectsInsideOfObject("x", listOf())
        val project4 = ListOfObjectsInsideOfObject("x", listOf(Owner("a"), Owner("b")))
        val project5 = ListOfObjectsInsideOfObject("x", listOf(Owner("a"), Owner()))
        //TODO: Investigate the feasibility of supporting encode nullable List<T?>
        // i.e. val project6 = ListOfObjectsInsideOfObject("x", listOf(Owner("a"), null))
        // currently, List<Any> is not supported by the serialization compiler plugin
        val projectList = listOf(project1, project2, project3, project4, project5)

        for (project in projectList) {
            docRefKotlin.set<ListOfObjectsInsideOfObject>(project)
            docRefPOJO.set(project)
            val expected = waitFor(docRefPOJO.get()).data
            Log.d(TAG, "${expected}")
            val actual = waitFor(docRefKotlin.get()).data
            Log.d(TAG, "${actual}")
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testSerializationGetMethodSameAsPOJOGet() {
        val docRefKotlin = testDocument("kotlin_get")
        val docRefPOJO = testDocument("pojo_get")
        val project1 = Project()
        val project2 = Project("x")
        val project3 = Project("x", "y")
        val project4 = Project(null, null)
        val projectList = listOf(project1, project2, project3, project4)

        for (project in projectList) {
            docRefPOJO.set(project)
            docRefKotlin.set(project)
            val expected = waitFor(docRefPOJO.get()).toObject<Project>()
            Log.d(TAG, "${expected}")
            val actual = waitFor(docRefKotlin.get()).get<Project>()
            Log.d(TAG, "${actual}")
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testSerializationGetMethodWorksForNestedObject() {
        val docRefKotlin = testDocument("kotlin_set")
        val docRefPOJO = testDocument("pojo_set")
        val project1 = ObjectInsideOfObject()
        val project2 = ObjectInsideOfObject("x")
        val project3 = ObjectInsideOfObject("x", Owner())
        val project4 = ObjectInsideOfObject("x", Owner("b"))
        //TODO: Fix the bug to decode nested nullable properties
        // i.e. val project5 = ObjectInsideOfObject("x", Owner(name = null))
        val projectList = listOf(project1, project2, project3, project4)

        for (project in projectList) {
            docRefPOJO.set(project)
            docRefKotlin.set(project)
            val expected = waitFor(docRefPOJO.get()).toObject<ObjectInsideOfObject>()
            Log.d(TAG, "${expected}")
            val actual = waitFor(docRefKotlin.get()).get<ObjectInsideOfObject>()
            Log.d(TAG, "${actual}")
            assertEquals(expected, actual)
        }
    }

    @Test
    fun testSerializationGetMethodWorksForList() {
        val docRefKotlin = testDocument("kotlin_set")
        val docRefPOJO = testDocument("pojo_set")
        val project1 = ListOfObjectsInsideOfObject()
        val project2 = ListOfObjectsInsideOfObject("x")
        val project3 = ListOfObjectsInsideOfObject("x", listOf())
        val project4 = ListOfObjectsInsideOfObject("x", listOf(Owner("a"), Owner("b")))
        val project5 = ListOfObjectsInsideOfObject("x", listOf(Owner("a"), Owner()))
        //TODO: Investigate the feasibility of supporting decode nullable List<T?>
        // i.e. val project6 = ListOfObjectsInsideOfObject("x", listOf(Owner("a"),null))
        // mix type list (List<Any>) decoding is not supported i.e. listOf("123", 123)
        val projectList = listOf(project1, project2, project3, project4, project5)

        for (project in projectList) {
            docRefPOJO.set(project)
            docRefKotlin.set(project)
            val expected = waitFor(docRefPOJO.get()).toObject<ListOfObjectsInsideOfObject>()
            Log.d(TAG, "${expected}")
            val actual = waitFor(docRefKotlin.get()).get<ListOfObjectsInsideOfObject>()
            Log.d(TAG, "${actual}")
            assertEquals(expected, actual)
        }
    }
}