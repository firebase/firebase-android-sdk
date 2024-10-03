package com.google.firebase.vertexai.common

import com.google.firebase.vertexai.internal.util.toInternal
import com.google.firebase.vertexai.type.HarmBlockMethod
import com.google.firebase.vertexai.type.HarmBlockThreshold
import com.google.firebase.vertexai.type.HarmCategory
import org.junit.Test

/**
 * Fetches all the `@JvmStatic` properties of a class that are instances of the class itself.
 *
 * For example, given the following class:
 * ```kt
 * public class HarmCategory private constructor(public val ordinal: Int) {
 *   public companion object {
 *     @JvmField public val UNKNOWN: HarmCategory = HarmCategory(0)
 *     @JvmField public val HARASSMENT: HarmCategory = HarmCategory(1)
 *   }
 * }
 * ```
 * This function will yield:
 * ```kt
 * [UNKNOWN, HARASSMENT]
 * ```
 */
internal inline fun <reified T : Any> getEnumValues(): List<T> {
    return T::class.java.declaredFields
        .filter { it.type == T::class.java }
        .mapNotNull { it.get(null) as? T }
}

/**
 * Ensures that whenever any of our "pseudo-enums" are updated, that the conversion layer is also
 * updated.
 */
internal class EnumUpdateTests {
    @Test
    fun `HarmCategory#toInternal() covers all values`() {
        val values = getEnumValues<HarmCategory>()
        values.forEach { it.toInternal() }
    }

    @Test
    fun `HarmBlockMethod#toInternal() covers all values`() {
        val values = getEnumValues<HarmBlockMethod>()
        values.forEach { it.toInternal() }
    }

    @Test
    fun `HarmBlockThreshold#toInternal() covers all values`() {
        val values = getEnumValues<HarmBlockThreshold>()
        values.forEach { it.toInternal() }
    }
}