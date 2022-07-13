package com.google.firebase.firestore.ktx.annotations

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.encoding.FirestoreAbstractDecoder
import com.google.firebase.firestore.encoding.FirestoreAbstractEncoder
import java.util.Date
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

open class BearStoreCustomSerialziers<T : Any>() : KSerializer<T> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(
            // get the serializer's subclass simple name without the package name
            "__${javaClass.simpleName}__"
        )

    override fun serialize(encoder: Encoder, value: T) {
        val encoder = encoder as FirestoreAbstractEncoder
        encoder.encodeFirestoreNativeDataType(value)
    }

    override fun deserialize(decoder: Decoder): T {
        val decoder = decoder as FirestoreAbstractDecoder
        val decodeValue = decoder.decodeFirestoreNativeDataType()
        return when (descriptor.serialName) {
            "__DateSerializer__" ->
                (decodeValue as Timestamp).let { it.toDate() }
                    as T // Date is saved as Timestamp in firestore
            else -> decodeValue as T
        }
    }

    object DateSerializer : BearStoreCustomSerialziers<Date>()
    object GeoPointSerializer : BearStoreCustomSerialziers<GeoPoint>()
    object TimestampSerializer : BearStoreCustomSerialziers<Timestamp>()
    object DocumentReferenceSerializer : BearStoreCustomSerialziers<DocumentReference>()
}

val FirestoreSerializersModule = SerializersModule {
    contextual(BearStoreCustomSerialziers.GeoPointSerializer)
    contextual(BearStoreCustomSerialziers.DocumentReferenceSerializer)
    contextual(BearStoreCustomSerialziers.TimestampSerializer)
    contextual(BearStoreCustomSerialziers.DateSerializer)
}
