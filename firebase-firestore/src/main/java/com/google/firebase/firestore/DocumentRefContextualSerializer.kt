package com.google.firebase.firestore

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass


object DocumentRefContextualSerializer: KSerializer<DocumentReference> {

    private val serializableClass: KClass<DocumentReference> = DocumentReference::class
    private val fallbackSerializer: KSerializer<DocumentReference>?=null
    private val typeArgumentsSerializers: Array<KSerializer<*>> = arrayOf()

    private fun KClass<*>.serializerNotRegistered(): Nothing {
        throw SerializationException(
            "Serializer for class '${simpleName}' is not found.\n" +
                    "Mark the class as @Serializable or provide the serializer explicitly."
        )
    }

    private fun innerserializer(serializersModule: SerializersModule): KSerializer<DocumentReference> =
        serializersModule.getContextual(serializableClass, typeArgumentsSerializers.toList()) ?: fallbackSerializer ?: serializableClass.serializerNotRegistered()


    override val descriptor: SerialDescriptor
        get() = ContextualSerializer(DocumentReference::class).descriptor

    override fun deserialize(decoder: Decoder): DocumentReference {
        return decoder.decodeSerializableValue(innerserializer(decoder.serializersModule))
    }

    override fun serialize(encoder: Encoder, value: DocumentReference) {
        encoder.encodeSerializableValue(innerserializer(encoder.serializersModule), value)
    }

}
