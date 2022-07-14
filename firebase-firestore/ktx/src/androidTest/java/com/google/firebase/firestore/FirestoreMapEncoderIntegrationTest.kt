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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.setData
import com.google.firebase.firestore.testCollection
import com.google.firebase.firestore.waitFor
import kotlin.test.assertFailsWith
import kotlinx.serialization.Serializable
import org.junit.Test

class FirestoreMapEncoderIntegrationTest {

    @Serializable
    enum class Grade {
        FRESHMAN,
        SOPHOMORE,
        JUNIOR,
        SENIOR
    }

    @Test
    fun flat_object_serialization_is_equivalent() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        @Serializable
        data class Student(
            val name: String? = null,
            val id: Int? = null,
            val age: Long? = null,
            val termAvg: Double? = null,
            val accumulateAvg: Float? = null,
            val male: Boolean? = null,
            val grade: Grade? = null
        )

        val studentList =
            listOf(
                Student(),
                Student(name = "foo"),
                Student(id = 1),
                Student(age = 20L),
                Student(termAvg = 100.0),
                Student(accumulateAvg = 99.5F),
                Student(male = true),
                Student(grade = Grade.FRESHMAN),
                Student("foo", 1, 20L, 100.0, 99.5F, true, Grade.FRESHMAN)
            )

        for (student in studentList) {
            docRefKotlin.setData(student)
            docRefPOJO.set(student)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertThat(expected).containsExactlyEntriesIn(actual)
        }
    }

    @Test
    fun non_supported_data_types_should_throw() {
        // char, byte, short are not supported (the current Java SDK's behavior)
        val docRefKotlin = testCollection("ktx").document("123")

        @Serializable
        data class NoneSupportedStudent(
            val initial: Char? = null,
            val byteValue: Byte? = null,
            val shortValue: Short? = null
        )

        val studentList =
            listOf(
                NoneSupportedStudent(initial = 'c'),
                NoneSupportedStudent(byteValue = 1),
                NoneSupportedStudent(shortValue = 1)
            )

        for (student in studentList) {
            assertFailsWith<IllegalArgumentException>(
                message = "not supported, please use",
                block = { docRefKotlin.setData(student) }
            )
        }
    }

    @Test
    fun object_without_optional_field_serialization_is_supported() {
        val docRefNonOptionalKotlin = testCollection("ktx").document("nonOptional")
        val docRefNonOptionalJava = testCollection("java").document("nonOptional")
        val docRefOptionalKotlin = testCollection("ktx").document("Optional")
        val docRefOptionalJava = testCollection("java").document("Optional")

        @Serializable
        data class NonOptionalStudent(
            val name: String,
            val id: Int,
            val age: Long,
            val termAvg: Double,
            val accumulateAvg: Float,
            val male: Boolean,
            val grade: Grade
        )

        @Serializable
        data class OptionalStudent(
            val name: String? = null,
            val id: Int? = null,
            val age: Long? = null,
            val termAvg: Double? = null,
            val accumulateAvg: Float? = null,
            val male: Boolean? = null,
            val grade: Grade? = null
        )

        val nonOptionalStudent =
            NonOptionalStudent("foo", 1, 20L, 100.0, 99.5F, true, Grade.FRESHMAN)
        docRefNonOptionalKotlin.setData(nonOptionalStudent)
        docRefNonOptionalJava.set(nonOptionalStudent)
        val expectedNonOptionalKtx = waitFor(docRefNonOptionalKotlin.get()).data
        val actualNonOptionalJava = waitFor(docRefNonOptionalJava.get()).data

        val optionalStudent = OptionalStudent("foo", 1, 20L, 100.0, 99.5F, true, Grade.FRESHMAN)
        docRefOptionalKotlin.setData(optionalStudent)
        docRefOptionalJava.set(optionalStudent)
        val expectedOptionalKtx = waitFor(docRefNonOptionalKotlin.get()).data
        val actualOptionalJava = waitFor(docRefNonOptionalJava.get()).data

        assertThat(expectedNonOptionalKtx).containsExactlyEntriesIn(actualNonOptionalJava)
        assertThat(expectedOptionalKtx).containsExactlyEntriesIn(actualOptionalJava)
        assertThat(expectedNonOptionalKtx).containsExactlyEntriesIn(actualOptionalJava)
    }

    @Test
    fun nested_object_serialization_is_equivalent() {

        @Serializable data class Owner(val name: String? = null, val age: Int? = 100)

        @Serializable data class Project(val name: String? = null, val owner: Owner? = null)

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")
        val listOfProjects =
            listOf(
                Project(),
                Project("x"),
                Project("x", Owner()),
                Project("x", Owner("a")),
                Project("x", Owner("a", 101)),
                Project("x", null),
                Project(null, null)
            )

        for (project in listOfProjects) {
            docRefKotlin.setData(project)
            docRefPOJO.set(project)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertThat(expected).containsExactlyEntriesIn(actual)
        }
    }

    @Test
    fun nested_primitive_list_serialization_is_equivalent() {

        @Serializable
        data class FinalExamMarks(val name: String? = null, val listOfOMarks: List<Long?>? = null)

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")
        val listOfFinalExamMarks =
            listOf(
                FinalExamMarks(),
                FinalExamMarks("x"),
                FinalExamMarks("x", listOf()),
                FinalExamMarks("x", listOf(100L)),
                FinalExamMarks("x", listOf(101L, 102L)),
                FinalExamMarks("x", listOf(null)),
                FinalExamMarks("x", listOf(null, 103L)),
                FinalExamMarks(listOfOMarks = listOf(null, 104L, 105L))
            )

        for (mark in listOfFinalExamMarks) {
            docRefKotlin.setData(mark)
            docRefPOJO.set(mark)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertThat(expected).containsExactlyEntriesIn(actual)
        }
    }

    @Test
    fun nested_array_serialization_is_supported() {
        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")

        @Serializable data class FinalExamMarksKtx(val collectionOfMarks: Array<Int?>? = null)
        // Java does not support serializing arrays (only support Lists)
        data class FinalExamMarksJava(val collectionOfMarks: List<Int>? = null)

        docRefKotlin.setData(FinalExamMarksKtx(arrayOf(1, 2, 3)))
        docRefPOJO.set(FinalExamMarksJava(listOf(1, 2, 3)))
        val expected = waitFor(docRefPOJO.get()).data
        val actual = waitFor(docRefKotlin.get()).data
        assertThat(expected).containsExactlyEntriesIn(actual)
    }

    @Test
    fun nested_custom_obj_list_serialization_is_equivalent() {

        @Serializable data class Owner(val name: String? = null)

        @Serializable
        data class Project(val name: String? = null, val listOfOwner: List<Owner?>? = null)

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")
        val listOfProjects =
            listOf(
                Project(),
                Project("x"),
                Project("x", listOf()),
                Project("x", listOf(Owner("a"), Owner("b"))),
                Project("x", listOf(Owner("a"), Owner())),
                Project("x", listOf(Owner("a"), null))
            )

        for (project in listOfProjects) {
            docRefKotlin.setData(project)
            docRefPOJO.set(project)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertThat(expected).containsExactlyEntriesIn(actual)
        }
    }

    @Test
    fun deep_nested_object_serialization_is_equivalent() {

        @Serializable data class People(val name: String? = null)

        @Serializable data class City(val name: String? = null, val people: People? = null)

        @Serializable
        data class Region(
            val name: String? = null,
            val city1: City? = null,
            val city2: City? = null
        )

        val docRefKotlin = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("456")
        val listOfTestedObject =
            listOf(
                Region(),
                Region("x"),
                Region("x", city1 = null, city2 = City("city2", People("people"))),
                Region(
                    "x",
                    city1 = City("city1", People("people")),
                    city2 = City("city2", People("people"))
                )
            )

        for (testedObject in listOfTestedObject) {
            docRefKotlin.setData(testedObject)
            docRefPOJO.set(testedObject)
            val expected = waitFor(docRefPOJO.get()).data
            val actual = waitFor(docRefKotlin.get()).data
            assertThat(expected).containsExactlyEntriesIn(actual)
        }
    }
}
