package com.example.firestore_kotlin_serialization

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable
import org.junit.Assert.*
import org.junit.Test


class NestedMapEncoderTest {

    @Serializable
    data class PlainProject(val name: String, val ownerName: String)

    @Serializable
    data class ObjectInsideOfObject(val name: String, val owner: Owner)

    @Serializable
    data class Owner(val name: String)

    @Serializable
    data class ListOfPrimitiveInsideOfObject(val name: String, val listOfNums: List<Long>)

    @Serializable
    data class ListOfObjectsInsideOfObject(val name: String, val listOfOwner: List<Owner>)


    @Test
    fun encodePlainObject() {
        val plainObject = PlainProject("kotlinx.serialization", "kotlin")
        val encodedMap = encodeToMap(plainObject)
        val expectedMap = mapOf("name" to "kotlinx.serialization", "ownerName" to "kotlin")
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun encodeObjectInsideOfObject() {
        val objectInsideOfObject = ObjectInsideOfObject("kotlinx.serialization", Owner("kotlin"))
        val encodedMap = encodeToMap(objectInsideOfObject)
        val expectedMap = mapOf(
            "name" to "kotlinx.serialization",
            "owner" to mapOf("name" to "kotlin")
        )
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun encodeListOfPrimitiveInsideOfObject() {
        val listOfPrimitiveInsideOfObject =
            ListOfPrimitiveInsideOfObject("kotlinx.serialization", listOf(1L, 10L, 100L, 1000L))
        val encodeMap = encodeToMap(listOfPrimitiveInsideOfObject)
        val expectedMap = mapOf(
            "name" to "kotlinx.serialization",
            "listOfNums" to listOf(1L, 10L, 100L, 1000L)
        )
        assertTrue(encodeMap == expectedMap)
    }

    @Test
    fun encodeListOfObjectsInsideOfObject() {
        val listOfOwner = listOf(Owner("a"), Owner("b"), Owner("c"))
        val listOfObjectsInsideOfObject =
            ListOfObjectsInsideOfObject("kotlinx.serialization", listOfOwner)
        val encodeMap = encodeToMap(listOfObjectsInsideOfObject)
        val expectedMap = mapOf(
            "name" to "kotlinx.serialization",
            "listOfOwner" to listOf(
                mapOf("name" to "a"),
                mapOf("name" to "b"),
                mapOf("name" to "c")
            )
        )
        assertTrue(encodeMap == expectedMap)
    }

    @Test
    fun integrationTest(){

//        val inputObject = PlainProject("name","owner")
//        docRef.set(inputObject)
//        docRef.get().addOnSuccessListener { documentSnapshot ->
//            val result = documentSnapshot.toObject<PlainProject>()
//            println("==============================")
//            println(result)
//        }
        assertTrue(true)

    }


}