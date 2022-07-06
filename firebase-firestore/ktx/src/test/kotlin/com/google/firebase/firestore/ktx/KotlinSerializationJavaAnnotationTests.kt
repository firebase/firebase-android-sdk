package com.google.firebase.firestore.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.ThrowOnExtraProperties
import kotlinx.serialization.Serializable
import org.junit.Test

class KotlinSerializationJavaAnnotationTests {
    @Serializable data class AnnotationOnFieldClass(@ServerTimestamp @DocumentId val foo: String)
    @Test
    fun `java annotation on field can be obtained via kotlin serialization`() {
        val descriptor = AnnotationOnFieldClass.serializer().descriptor
        val annotations = descriptor.getElementAnnotations(0)
        assertThat(annotations).hasSize(2)
    }

    @ThrowOnExtraProperties @IgnoreExtraProperties
    @Serializable data class AnnotationOnTypeClass(val foo: String)
    @Test
    fun `java annotation on type can be obtained via kotlin serialization`() {
        val descriptor = AnnotationOnTypeClass.serializer().descriptor
        val annotations = descriptor.annotations
        assertThat(annotations).hasSize(2)
    }
}
