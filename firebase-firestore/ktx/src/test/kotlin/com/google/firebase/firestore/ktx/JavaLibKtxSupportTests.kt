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
    fun `serializers for firestore supported types are working during encoding`() {

        @Serializable
        data class Project(
            val name: String,
            val docRef: DocumentReference,
            @Contextual
            val time: Date
        )

        val dateObj = Date(10000)
        val docRefObj = documentReference("foo/bar")

        val actual =
            encodeToList(Project("list serialization",  docRefObj, dateObj))
        val expected = listOf("list serialization", docRefObj, dateObj)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `serializers for firestore supported types are working during decoding`() {

        @Serializable
        data class Project(
            val name: String,
            val docRef: DocumentReference,
            @Contextual
            val time: Date
        )

        val dateObj = Date(10000)
        val timeStampObj = Timestamp(dateObj)
        val docRefObj = documentReference("foo/bar")
        val expected = Project("list serialization", docRefObj, dateObj)
        // Note that the last field in Project is converted from Date to Timestamp by Firestore server:
        val list = listOf("list serialization", docRefObj, timeStampObj)
        val actual = decodeFromList<Project>(list)
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `serializers for firestore supported types is working with round trip`() {

        @Serializable
        data class Project(
            val name: String,
            val docRef: DocumentReference
        )
        val docRefObj = documentReference("foo/bar")
        val expected = Project("list serialization", docRefObj)

        val list = encodeToList(expected)
        val actual = decodeFromList<Project>(list)
        assertThat(actual).isEqualTo(expected)
    }
}
