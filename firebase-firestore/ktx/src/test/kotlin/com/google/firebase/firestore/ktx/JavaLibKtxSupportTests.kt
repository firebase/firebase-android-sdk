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

package com.google.firebase.firestore.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.ThrowOnExtraProperties
import com.google.firebase.firestore.documentReference
import java.util.Date
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.Test

class JavaLibKtxSupportTests {

    @Test
    fun `ServerTimestamp annotations should be seen during Ktx serialization`() {
        @Serializable
        data class AnnotationTest(@ServerTimestamp val date: String? = null)

        val annotations = AnnotationTest.serializer().descriptor.getElementAnnotations(0)
        assertThat(annotations).hasSize(1)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(ServerTimestamp::class.java)
    }

    @Test
    fun `DocumentId annotations should be seen during Ktx serialization`() {
        @Serializable
        data class AnnotationTest(@DocumentId val docId: String? = null)

        val annotations = AnnotationTest.serializer().descriptor.getElementAnnotations(0)
        assertThat(annotations).hasSize(1)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(DocumentId::class.java)
    }

    @Test
    fun `ThrowOnExtraProperties annotations should be seen during Ktx serialization`() {
        @Serializable
        @ThrowOnExtraProperties
        data class AnnotationTest(val docId: String? = null)

        val annotations = AnnotationTest.serializer().descriptor.annotations
        assertThat(annotations).hasSize(1)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(ThrowOnExtraProperties::class.java)
    }

    @Test
    fun `IgnoreExtraProperties annotations should be seen during Ktx serialization`() {
        @Serializable
        @IgnoreExtraProperties
        data class AnnotationTest(val docId: String? = null)

        val annotations = AnnotationTest.serializer().descriptor.annotations
        assertThat(annotations).hasSize(1)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(IgnoreExtraProperties::class.java)
    }

    @Test
    fun `java GeoPoint Ktx's serializer should be seen during Ktx serialization`() {
        @Serializable
        data class JavaGeoPoint(val geoPoint: GeoPoint)

        val geoPointObj = JavaGeoPoint.serializer().descriptor.getElementDescriptor(0)
        assertThat(geoPointObj.serialName).isEqualTo("__GeoPointSerializer__")
    }

    @Test
    fun `java Timestamp Ktx's serializer should be seen during Ktx serialization`() {
        @Serializable
        data class JavaTimestamp(val timestamp: Timestamp)

        val timestampObj = JavaTimestamp.serializer().descriptor.getElementDescriptor(0)
        assertThat(timestampObj.serialName).isEqualTo("__TimestampSerializer__")
    }

    @Test
    fun `java DocumentReference Ktx's serializer should be seen during Ktx serialization`() {
        @Serializable
        data class JavaDocRef(val docRef: DocumentReference)

        val docRefObj = JavaDocRef.serializer().descriptor.getElementDescriptor(0)
        assertThat(docRefObj.serialName).isEqualTo("__DocumentReferenceSerializer__")
    }

    @Test
    fun `serializers for firestore supported types are working during encoding`() {

        @Serializable
        data class Project(
            val name: String,
            val geoPoints: GeoPoint,
            val timestamp: Timestamp,
            val docRef: DocumentReference,
            @Contextual
            val time: Date
        )

        val dateObj = Date(10000)
        val geoPointObj = GeoPoint(10.0, 11.0)
        val timeStampObj = Timestamp(dateObj)
        val docRefObj = documentReference("foo/bar")

        val actual =
            encodeToList(Project("list serialization", geoPointObj, timeStampObj, docRefObj, dateObj))
        val expected = listOf("list serialization", geoPointObj, timeStampObj, docRefObj, dateObj)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `serializers for firestore supported types are working during decoding`() {

        @Serializable
        data class Project(
            val name: String,
            val geoPoints: GeoPoint,
            val timestamp: Timestamp,
            val docRef: DocumentReference,
            @Contextual
            val time: Date
        )

        val dateObj = Date(10000)
        val geoPointObj = GeoPoint(10.0, 11.0)
        val timeStampObj = Timestamp(dateObj)
        val docRefObj = documentReference("foo/bar")
        val expected = Project("list serialization", geoPointObj, timeStampObj, docRefObj, dateObj)
        // Note that the last field in Project is converted from Date to Timestamp by Firestore server:
        val list = listOf("list serialization", geoPointObj, timeStampObj, docRefObj, timeStampObj)
        val actual = decodeFromList<Project>(list)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `serializers for firestore supported types is working with round trip`() {
        // TODO: test field of Date with integration test,
        //  this data type's round trip cannot be done with unit test
        @Serializable
        data class Project(
            val name: String,
            val geoPoints: GeoPoint,
            val timestamp: Timestamp,
            val docRef: DocumentReference
        )

        val geoPointObj = GeoPoint(10.0, 11.0)
        val timeStampObj = Timestamp(Date(10000))
        val docRefObj = documentReference("foo/bar")
        val expected = Project("list serialization", geoPointObj, timeStampObj, docRefObj)

        val list = encodeToList(expected)
        val actual = decodeFromList<Project>(list)
        assertThat(actual).isEqualTo(expected)
    }
}
