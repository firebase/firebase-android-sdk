package com.google.firebase.testing.processor

import com.google.firebase.ai.annotations.Guide
import com.google.firebase.ai.annotations.Generable

/**
 * A test kdoc
 * @property longTest a test long
 * that takes up multiple lines
 */
@Generable
data class RootSchemaTestClass(
    val integerTest: Int?,
    val longTest: Long,
    val floatTest: Float,
    @Guide(minimum = 5.0)
    val doubleTest: Double?,
    val listTest: List<Int>,
    @Guide(description = "most likely true, very rarely false")
    val booleanTest: Boolean,
    val stringTest: String,
    val objTest: SecondarySchemaTestClass,
    val enumTest: EnumTest,
) {
    companion object
}

/**
 * class kdoc should be used if property kdocs aren't present
 */
data class SecondarySchemaTestClass(val testInt: Int)

enum class EnumTest{
    A,B,C
}