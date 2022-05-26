package com.example.firestore_kotlin_serialization

import kotlinx.serialization.Serializable
import org.junit.Assert.assertTrue
import org.junit.Test

class NestedMapDecoderTest {
    @Serializable
    data class PlainProject(val name: String, val ownerName: String)

    @Serializable
    data class MapInsideOfMapProject(val name: String, val owner: Owner)

    @Serializable
    data class Owner(val name: String)

    @Serializable
    data class ListInsideOfMapProject(val name: String, val listOfOwner: List<Owner>)

    private val firestoreDocument = null

    @Test
    fun decodePlainMap() {
        val plainMap =
            mapOf<String, Any>("name" to "kotlinx.serialization", "ownerName" to "kotlin")
        val decodedObject = decodeFromNestedMap<PlainProject>(plainMap, firestoreDocument)
        val expectedObject = PlainProject("kotlinx.serialization", "kotlin")
        assertTrue(decodedObject == expectedObject)
    }

    @Test
    fun decodeRandomOrderMap() {
        val plainMap =
            mapOf<String, Any>("ownerName" to "kotlin", "name" to "kotlinx.serialization")
        val decodedObject = decodeFromNestedMap<PlainProject>(plainMap, firestoreDocument)
        val expectedObject = PlainProject("kotlinx.serialization", "kotlin")
        assertTrue(decodedObject == expectedObject)
    }

    @Test
    fun decodeMapInSideOfMap() {
        val ownerMap = mapOf<String, Any>("name" to "kotlin")
        val mapInsideOfMap =
            mapOf<String, Any>("name" to "kotlinx.serialization", "owner" to ownerMap)
        val decodedObject = decodeFromNestedMap<MapInsideOfMapProject>(mapInsideOfMap, firestoreDocument)
        val expectedObject = MapInsideOfMapProject("kotlinx.serialization", Owner("kotlin"))
        assertTrue(decodedObject == expectedObject)
    }

    @Test
    fun decodeListInsideOfMap() {
        val listOfOwnerMap = listOf<Map<String, Any>>(
            mapOf("name" to "a"),
            mapOf("name" to "b"),
            mapOf("name" to "c")
        )
        val listInsideOfMap =
            mapOf<String, Any>("name" to "kotlinx.serialization", "listOfOwner" to listOfOwnerMap)
        val decodedObject = decodeFromNestedMap<ListInsideOfMapProject>(listInsideOfMap, firestoreDocument)
        val listOfOwner = listOf<Owner>(Owner("a"), Owner("b"), Owner("c"))
        val expectedObject = ListInsideOfMapProject("kotlinx.serialization", listOfOwner)
        assertTrue(decodedObject == expectedObject)
    }
}
