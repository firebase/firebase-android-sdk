package com.google.firebase.firestore.ktx

import com.google.firebase.firestore.ktx.serialization.encodeToMap
import kotlinx.serialization.Serializable
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedMapEncoderTests {

    @Test
    fun encodePlainObject() {
        @Serializable data class PlainProject(val name: String, val ownerName: String)

        val plainObject = PlainProject("kotlinx.serialization", "kotlin")
        val encodedMap = encodeToMap(plainObject)
        val expectedMap = mapOf("name" to "kotlinx.serialization", "ownerName" to "kotlin")
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun encodeObjectInsideOfObject() {
        @Serializable data class Owner(val name: String)
        @Serializable data class ObjectInsideOfObject(val name: String, val owner: Owner)

        val objectInsideOfObject = ObjectInsideOfObject("kotlinx.serialization", Owner("kotlin"))
        val encodedMap = encodeToMap(objectInsideOfObject)
        val expectedMap =
            mapOf("name" to "kotlinx.serialization", "owner" to mapOf("name" to "kotlin"))
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun encodeListOfPrimitiveInsideOfObject() {
        @Serializable
        data class ListOfPrimitiveInsideOfObject(val name: String, val listOfNums: List<Long>)

        val listOfPrimitiveInsideOfObject =
            ListOfPrimitiveInsideOfObject("kotlinx.serialization", listOf(1L, 10L, 100L, 1000L))
        val encodeMap = encodeToMap(listOfPrimitiveInsideOfObject)
        val expectedMap =
            mapOf("name" to "kotlinx.serialization", "listOfNums" to listOf(1L, 10L, 100L, 1000L))
        assertTrue(encodeMap == expectedMap)
    }

    @Test
    fun encodeListOfObjectsInsideOfObject() {
        @Serializable data class Owner(val name: String)
        @Serializable
        data class ListOfObjectsInsideOfObject(val name: String, val listOfOwner: List<Owner>)

        val listOfOwner = listOf(Owner("a"), Owner("b"), Owner("c"))
        val listOfObjectsInsideOfObject =
            ListOfObjectsInsideOfObject("kotlinx.serialization", listOfOwner)
        val encodeMap = encodeToMap(listOfObjectsInsideOfObject)
        val expectedMap =
            mapOf(
                "name" to "kotlinx.serialization",
                "listOfOwner" to
                    listOf(mapOf("name" to "a"), mapOf("name" to "b"), mapOf("name" to "c"))
            )
        assertTrue(encodeMap == expectedMap)
    }

    @Serializable
    enum class Direction {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    @Test
    fun encodeObjectWithEnumField() {

        @Serializable data class Movement(val direction: Direction, val distance: Long)

        val movement = Movement(Direction.EAST, 100)
        val encodedMap = encodeToMap(movement)
        val expectedMap = mapOf("direction" to "EAST", "distance" to 100L)
        assertTrue(encodedMap == expectedMap)
    }

    @Test
    fun encodeObjectWithNullField() {
        @Serializable data class Visitor(val name: String? = null, val age: String)

        val visitor = Visitor(age = "100")
        val encodedMap = encodeToMap(visitor)
        val expectedMap = mutableMapOf("name" to null, "age" to "100")
        assertTrue(encodedMap == expectedMap)
    }
}
