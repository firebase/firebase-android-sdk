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
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.ThrowOnExtraProperties
import kotlinx.serialization.Serializable
import org.junit.Test

class JavaLibKtxSupportTests {

    @Test
    fun `java converted Ktx ServerTimestamp annotations should be seen during Ktx serialization`() {
        @Serializable
        data class AnnotationTest(@ServerTimestamp val date: String? = null)

        val annotations = AnnotationTest.serializer().descriptor.getElementAnnotations(0)
        assertThat(annotations).hasSize(1)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(ServerTimestamp::class.java)
    }

    @Test
    fun `java converted Ktx DocumentId annotations should be seen during Ktx serialization`() {
        @Serializable
        data class AnnotationTest(@DocumentId val docId: String? = null)

        val annotations = AnnotationTest.serializer().descriptor.getElementAnnotations(0)
        assertThat(annotations).hasSize(1)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(DocumentId::class.java)
    }

    @Test
    fun `java converted Ktx ThrowOnExtraProperties annotations should be seen during Ktx serialization`() {
        @Serializable
        @ThrowOnExtraProperties
        data class AnnotationTest(val docId: String? = null)

        val annotations = AnnotationTest.serializer().descriptor.annotations
        assertThat(annotations).hasSize(1)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(ThrowOnExtraProperties::class.java)
    }

    @Test
    fun `java converted Ktx IgnoreExtraProperties annotations should be seen during Ktx serialization`() {
        @Serializable
        @IgnoreExtraProperties
        data class AnnotationTest(val docId: String? = null)

        val annotations = AnnotationTest.serializer().descriptor.annotations
        assertThat(annotations).hasSize(1)
        val annotation = annotations[0]
        assertThat(annotation::class.java).isAssignableTo(IgnoreExtraProperties::class.java)
    }
}
