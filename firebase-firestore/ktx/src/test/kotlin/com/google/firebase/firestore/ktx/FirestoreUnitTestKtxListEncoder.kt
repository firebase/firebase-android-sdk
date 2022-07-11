package com.google.firebase.firestore.ktx

import com.google.firebase.firestore.encoding.FirestoreAbstractDecoder
import com.google.firebase.firestore.encoding.FirestoreAbstractEncoder
import com.google.firebase.firestore.encoding.FirestoreSerializersModule
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * An encoder that encodes a @[Serializable] object to a plain list of Firestore supported types. This encoder is only supposed to be used for test purpose.
 */
private class FirestoreUnitTestKtxListEncoder() : AbstractEncoder(),
    FirestoreAbstractEncoder {

    val list = mutableListOf<Any>()

    override fun encodeFirestoreNativeDataType(value: Any) {
        list.add(value)
    }

    override val serializersModule: SerializersModule = FirestoreSerializersModule.getFirestoreSerializersModule()

    override fun encodeValue(value: Any) {
        list.add(value)
    }
}

/**
 * A decoder that decodes a plain list of Firestore supported types to a @[Serializable] object. This decoder is only supposed to be used for test purpose.
 *
 * @param list A plain list of Firestore supported types that needs to be decoded.
 */
private class FirestoreUnitTestKtxListDecoder(val list: ArrayDeque<Any>) : AbstractDecoder(),
    FirestoreAbstractDecoder {
    private var elementIndex = 0

    override val serializersModule: SerializersModule = FirestoreSerializersModule.getFirestoreSerializersModule()

    override fun decodeValue(): Any = list.removeFirst()

    override fun decodeFirestoreNativeDataType(): Any = list.removeFirst()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        FirestoreUnitTestKtxListDecoder(list)
}

/**
 * Encodes a [Serializable] object of type T to a list of Firestore supported primitives
 */
fun <T> encodeToList(serializer: SerializationStrategy<T>, value: T): List<Any> {
    val encoder = FirestoreUnitTestKtxListEncoder()
    encoder.encodeSerializableValue(serializer, value)
    return encoder.list
}

/**
 * Decodes a list of Firestore supported types to a [Serializable] object
 */
fun <T> decodeFromList(deserializer: DeserializationStrategy<T>, list: List<Any>): T {
    val decoder = FirestoreUnitTestKtxListDecoder(ArrayDeque(list))
    return decoder.decodeSerializableValue(deserializer)
}

/**
 * Encodes a [Serializable] object of type T to a list of Firestore supported types
 */
inline fun <reified T> encodeToList(value: T) = encodeToList(serializer(), value)

/**
 * Decodes a list of Firestore supported primitives to a [Serializable] object
 */
inline fun <reified T> decodeFromList(list: List<Any>): T = decodeFromList(serializer(), list)
