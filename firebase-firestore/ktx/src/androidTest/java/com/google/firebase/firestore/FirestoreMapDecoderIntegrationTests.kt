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
import com.google.firebase.firestore.testutil.getData
import com.google.firebase.firestore.testutil.getPojoData
import com.google.firebase.firestore.testutil.setData
import com.google.firebase.firestore.testutil.testCollection
import com.google.firebase.firestore.testutil.waitFor
import kotlinx.serialization.Serializable
import org.junit.Test

class FirestoreMapDecoderIntegrationTests {

    @Serializable
    private enum class Grade {
        FRESHMAN,
        SOPHOMORE,
        JUNIOR,
        SENIOR
    }

    @Serializable
    private data class Student(
        val name: String? = null,
        val id: Int? = null,
        val age: Long? = null,
        val termAvg: Double? = null,
        val accumulateAvg: Float? = null,
        val male: Boolean? = null,
        val grade: Grade? = null
    )

    @Test
    fun flat_object_deserialization_is_equivalent() {
        val docRefKotlin = testCollection("ktx").document("123")
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
            val expected = waitFor(docRefKotlin.get()).getPojoData<Student>()
            val actual = waitFor(docRefKotlin.get()).getData<Student>()
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Serializable
    private data class NonOptionalStudent(
        val name: String,
        val id: Int,
        val age: Long,
        val termAvg: Double,
        val accumulateAvg: Float,
        val male: Boolean,
        val grade: FirestoreMapEncoderIntegrationTest.Grade
    )

    @Serializable
    private data class OptionalStudent(
        val name: String? = null,
        val id: Int? = null,
        val age: Long? = null,
        val termAvg: Double? = null,
        val accumulateAvg: Float? = null,
        val male: Boolean? = null,
        val grade: FirestoreMapEncoderIntegrationTest.Grade? = null
    )

    private fun NonOptionalStudent.toOptionalStudent() = OptionalStudent(
        name = name,
        id = id,
        age = age,
        termAvg = termAvg,
        accumulateAvg = accumulateAvg,
        male = male,
        grade = grade
    )

    @Test
    fun object_without_optional_field_deserialization_is_supported() {
        // Kotlin supports encoding/decoding fields without default values; While Java POJO method does not.
        val docRefNonOptionalKotlin = testCollection("ktx").document("nonOptional")
        val nonOptionalStudent =
            NonOptionalStudent(
                "foo",
                1,
                20L,
                100.0,
                99.5F,
                true,
                FirestoreMapEncoderIntegrationTest.Grade.FRESHMAN
            )
        docRefNonOptionalKotlin.setData(nonOptionalStudent)

        val expectedNonOptionalKtx =
            waitFor(docRefNonOptionalKotlin.get()).getData<NonOptionalStudent>()
                ?.toOptionalStudent()
        val actualNonOptionalJava =
            waitFor(docRefNonOptionalKotlin.get()).getPojoData<OptionalStudent>()
        assertThat(expectedNonOptionalKtx).isEqualTo(actualNonOptionalJava)
    }

    @Serializable
    private data class Owner(val name: String? = null, val age: Int? = 100)

    @Serializable
    private data class Project(val name: String? = null, val owner: Owner? = null)

    @Test
    fun nested_object_deserialization_is_equivalent() {
        val docRefKotlin = testCollection("ktx").document("123")
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
            val expected = waitFor(docRefKotlin.get()).getPojoData<Project>()
            val actual = waitFor(docRefKotlin.get()).getData<Project>()
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Serializable
    private data class FinalExamMarks(
        val name: String? = null,
        val listOfOMarks: List<Long?>? = null
    )

    @Test
    fun nested_primitive_list_deserialization_is_equivalent() {
        val docRefKotlin = testCollection("ktx").document("123")
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
            val expected = waitFor(docRefKotlin.get()).getPojoData<FinalExamMarks>()
            val actual = waitFor(docRefKotlin.get()).getData<FinalExamMarks>()
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Serializable
    private data class ProjectWithListOwners(
        val name: String? = null,
        val listOfOwner: List<Owner?>? = null
    )

    @Test
    fun nested_custom_obj_list_serialization_is_equivalent() {
        val docRefKotlin = testCollection("ktx").document("123")
        val listOfProjects =
            listOf(
                ProjectWithListOwners(),
                ProjectWithListOwners("x"),
                ProjectWithListOwners("x", listOf()),
                ProjectWithListOwners("x", listOf(Owner("a"), Owner("b"))),
                ProjectWithListOwners("x", listOf(Owner("a"), Owner())),
                ProjectWithListOwners("x", listOf(Owner("a"), null))
            )

        for (project in listOfProjects) {
            docRefKotlin.setData(project)
            val expected = waitFor(docRefKotlin.get()).getPojoData<ProjectWithListOwners>()
            val actual = waitFor(docRefKotlin.get()).getData<ProjectWithListOwners>()
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Serializable
    private data class People(val name: String? = null)

    @Serializable
    private data class City(val name: String? = null, val people: People? = null)

    @Serializable
    private data class Region(
        val name: String? = null,
        val city1: City? = null,
        val city2: City? = null
    )

    @Test
    fun deep_nested_object_deserialization_is_equivalent() {
        val docRefKotlin = testCollection("ktx").document("123")
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
            val expected = waitFor(docRefKotlin.get()).getPojoData<Region>()
            val actual = waitFor(docRefKotlin.get()).getData<Region>()
            assertThat(actual).isEqualTo(expected)
        }
    }

    @Test
    // TODO: This feature need to be implemented at DocumentSnapshot Line 330
    fun get_field_to_object_is_equivalent() {
        val owner = Owner("foo", 10)
        val project = Project("kotlin-project", owner = owner)
        val docRefKotlin = testCollection("ktx").document("123")
        docRefKotlin.setData(project)
        val docSnapshot = waitFor(docRefKotlin.get())
        // get itself will return a map if the field is a custom class, and will return the value if it is permitive type.
        val actual = waitFor(docRefKotlin.get()).get("owner")

        // must use class.java to get the field value as an POJO object
//        val actual = docSnapshot.get("owner", Owner::class.java)
        assertThat(actual).isEqualTo("123")
    }
}
