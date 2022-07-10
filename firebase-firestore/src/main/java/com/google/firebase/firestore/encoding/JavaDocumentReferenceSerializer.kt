package com.google.firebase.firestore.encoding

import com.google.firebase.firestore.JavaDocumentReference
import com.google.firebase.firestore.JavaGeoPoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class JavaDocumentReferenceSerializer: KSerializer<JavaDocumentReference> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(
            "GeoPointSerialName",
        )

    override fun serialize(encoder: Encoder, value: JavaDocumentReference) {
        val nestedMapEncoder = encoder as FirestoreAbstractEncoder
        nestedMapEncoder.encodeDocumentReference(value)
    }

    override fun deserialize(decoder: Decoder): JavaDocumentReference {
        TODO("Not yet implemented")
    }
}