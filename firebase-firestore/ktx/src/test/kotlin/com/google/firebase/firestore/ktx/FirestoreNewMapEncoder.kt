package com.google.firebase.firestore.ktx

import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

class FirestoreNewMapEncoder(val callback: (Map<String, String>) -> Unit) :AbstractEncoder() {
    private val map: MutableMap<String, Any?> = mutableMapOf()
    private var depth: Int = 0
    private val propertyNamesToBeEncoded: MutableList<String> = mutableListOf()



    private var elementIndex: Int = 0
    companion object {
        private const val MAX_DEPTH: Int = 500
    }

    init {
        if (depth == MAX_DEPTH) {
            throw IllegalArgumentException(
                "Exceeded maximum depth of $MAX_DEPTH, which likely indicates there's an object cycle"
            )
        }
    }


    override val serializersModule: SerializersModule = EmptySerializersModule
    override fun encodeValue(value: Any) {
        // TODO: Handle @DocumentId and @ServerTimestamp annotations from descriptor
        val key = propertyNamesToBeEncoded[elementIndex++]
        map.put(key, value)
    }


}