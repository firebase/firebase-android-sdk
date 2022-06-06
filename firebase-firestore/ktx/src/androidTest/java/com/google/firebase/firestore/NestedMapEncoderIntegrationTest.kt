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

import com.google.firebase.firestore.ktx.serialization.setData
import com.google.firebase.firestore.testutil.IntegrationTestUtil.Companion.testCollection
import com.google.firebase.firestore.testutil.IntegrationTestUtil.Companion.waitFor
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Test

class NestedMapEncoderIntegrationTest {

    @Test
    fun serializationSetDataMethodIsTheSameAsPOJOSetOnPlainObject() {

        @Serializable data class Project(val name: String? = null, val owner: String? = null)

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")
        val projectList = listOf(Project(), Project("x"), Project("x", "y"), Project(null, null))

        for (project in projectList) {
            docRefKotlin.setData(project)
            docRefPOJO.set(project)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertEquals(expected, actual)
        }
    }

    @Test
    fun serializationSetDataMethodIsTheSameAsPOJOSetOnNestedObject() {

        @Serializable data class Owner(val name: String? = null, val age: Int? = 100)

        @Serializable
        data class ObjectInsideOfObject(val name: String? = null, val owner: Owner? = null)

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")
        val listOfTestedObject =
            listOf(
                ObjectInsideOfObject(),
                ObjectInsideOfObject("x"),
                ObjectInsideOfObject("x", Owner()),
                ObjectInsideOfObject("x", Owner("a")),
                ObjectInsideOfObject("x", Owner("a", 101)),
                ObjectInsideOfObject("x", null),
                ObjectInsideOfObject(null, null)
            )

        for (testedObject in listOfTestedObject) {
            docRefKotlin.setData(testedObject)
            docRefPOJO.set(testedObject)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertEquals(expected, actual)
        }
    }

    @Test
    fun serializationSetDataMethodIsTheSameAsPOJOSetForListOfPrimitives() {

        @Serializable
        data class ListOfPrimitivesInsideOfObject(
            val name: String? = null,
            val listOfONumbers: List<Long?>? = null
        )

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")
        val listOfTestedObject =
            listOf(
                ListOfPrimitivesInsideOfObject(),
                ListOfPrimitivesInsideOfObject("x"),
                ListOfPrimitivesInsideOfObject("x", listOf()),
                ListOfPrimitivesInsideOfObject("x", listOf(100L)),
                ListOfPrimitivesInsideOfObject("x", listOf(101L, 102L)),
                ListOfPrimitivesInsideOfObject("x", listOf(null)),
                ListOfPrimitivesInsideOfObject("x", listOf(null, 103L)),
                ListOfPrimitivesInsideOfObject(listOfONumbers = listOf(null, 104L, 105L))
            )

        for (testedObject in listOfTestedObject) {
            docRefKotlin.setData(testedObject)
            docRefPOJO.set(testedObject)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertEquals(expected, actual)
        }
    }

    @Test
    fun serializationSetDataMethodIsTheSameAsPOJOSetForListOfObjects() {

        @Serializable data class Owner(val name: String? = null)

        @Serializable
        data class ListOfObjectsInsideOfObject(
            val name: String? = null,
            val listOfOwner: List<Owner?>? = null
        )

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")
        val listOfTestedObject =
            listOf(
                ListOfObjectsInsideOfObject(),
                ListOfObjectsInsideOfObject("x"),
                ListOfObjectsInsideOfObject("x", listOf()),
                ListOfObjectsInsideOfObject("x", listOf(Owner("a"), Owner("b"))),
                ListOfObjectsInsideOfObject("x", listOf(Owner("a"), Owner())),
                ListOfObjectsInsideOfObject("x", listOf(Owner("a"), null))
            )

        for (testedObject in listOfTestedObject) {
            docRefKotlin.setData(testedObject)
            docRefPOJO.set(testedObject)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertEquals(expected, actual)
        }
    }
}
