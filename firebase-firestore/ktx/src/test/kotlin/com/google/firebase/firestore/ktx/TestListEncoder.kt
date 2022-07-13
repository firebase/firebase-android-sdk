package com.google.firebase.firestore.ktx

import com.google.firebase.firestore.FirestoreSerializersModule
import com.google.firebase.firestore.encoding.FirestoreAbstractDecoder
import com.google.firebase.firestore.encoding.FirestoreAbstractEncoder
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * An encoder of @[Serializable] objects to plain lists of Firestore supported types. Test only.
 */
private class TestListEncoder() : AbstractEncoder(),
    FirestoreAbstractEncoder {

    val list = mutableListOf<Any>()

    override fun encodeFirestoreNativeDataType(value: Any): Unit = list.let { it.add(value) }

    override val serializersModule: SerializersModule = FirestoreSerializersModule

    override fun encodeValue(value: Any): Unit = list.let { it.add(value) }
}

/**
 * A decoder of plain lists of Firestore supported types to @[Serializable] objects. Test only.
 *
 * @param list A plain list of Firestore supported types that needs to be decoded.
 */
private class TestListDecoder(val list: ArrayDeque<Any>) : AbstractDecoder(),
    FirestoreAbstractDecoder {
    private var elementIndex = 0

    override val serializersModule: SerializersModule = FirestoreSerializersModule

    override fun decodeValue(): Any = list.removeFirst()

    override fun decodeFirestoreNativeDataType(): Any = list.removeFirst()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == descriptor.elementsCount) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        TestListDecoder(list)
}

/**
 * Encodes a @[Serializable] object of type T to a list of Firestore supported primitives
 */
fun <T> encodeToList(serializer: SerializationStrategy<T>, value: T): List<Any> {
    val encoder = TestListEncoder()
    encoder.encodeSerializableValue(serializer, value)
    return encoder.list
}

/**
 * Decodes a list of Firestore supported types to a @[Serializable] object
 */
fun <T> decodeFromList(deserializer: DeserializationStrategy<T>, list: List<Any>): T {
    val decoder = TestListDecoder(ArrayDeque(list))
    return decoder.decodeSerializableValue(deserializer)
}

/**
 * Encodes a @[Serializable] object of type T to a list of Firestore supported types
 */
inline fun <reified T> encodeToList(value: T) = encodeToList(serializer(), value)

/**
 * Decodes a list of Firestore supported primitives to a @[Serializable] object
 */
inline fun <reified T> decodeFromList(list: List<Any>): T = decodeFromList(serializer(), list)
