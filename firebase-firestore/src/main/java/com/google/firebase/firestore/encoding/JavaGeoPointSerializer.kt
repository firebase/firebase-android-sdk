package com.google.firebase.firestore.encoding

import com.google.firebase.firestore.JavaGeoPoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class JavaGeoPointSerializer : KSerializer<JavaGeoPoint> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(
            "GeoPointSerialName",
        )

    override fun serialize(encoder: Encoder, value: JavaGeoPoint) {
        val nestedMapEncoder = encoder as FirestoreAbstractEncoder
        nestedMapEncoder.encodeGeoPoint(value)
    }

    override fun deserialize(decoder: Decoder): JavaGeoPoint {
        TODO("Not yet implemented")
    }
}
