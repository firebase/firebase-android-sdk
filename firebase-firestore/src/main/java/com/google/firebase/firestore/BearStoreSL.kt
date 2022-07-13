package com.google.firebase.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.encoding.FirestoreNativeDataTypeSerializer
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.util.Date
import kotlin.reflect.KClass

class BearStoreSL {

}

val FirestoreSerializersModule = SerializersModule {
    contextual(FirestoreNativeDataTypeSerializer.GeoPointSerializer)
    contextual(FirestoreNativeDataTypeSerializer.DocumentReferenceSerializer)
    contextual(FirestoreNativeDataTypeSerializer.TimestampSerializer)
    contextual(FirestoreNativeDataTypeSerializer.DateSerializer)
}

//val contextrualDes = ContextualSerializer(DocumentReference::class) as KSerializer<DocumentReference>
//@Serializable(with = ContextualSerializer(DocumentReference::class))
//data class Test(val var1: String)