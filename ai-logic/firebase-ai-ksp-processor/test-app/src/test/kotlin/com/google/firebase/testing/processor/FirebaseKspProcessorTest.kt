/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.testing.processor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FirebaseKspProcessorTest {

  @Test
  fun kspProcessorGeneratesCorrectSchema() {
    val rootSchema = RootSchemaTestClass.firebaseAISchema()

    assertThat(rootSchema.clazz).isEqualTo(RootSchemaTestClass::class)
    assertThat(rootSchema.description).isEqualTo("A test kdoc")

    val properties = checkNotNull(rootSchema.properties)

    val intSchema = checkNotNull(properties["integerTest"])
    assertThat(intSchema.title).isEqualTo("integerTest")
    assertThat(intSchema.nullable).isTrue()

    val longSchema = checkNotNull(properties["longTest"])
    assertThat(longSchema.title).isEqualTo("longTest")
    assertThat(longSchema.description).isEqualTo("a test long that takes up multiple lines")
    assertThat(longSchema.nullable).isFalse()

    val floatSchema = checkNotNull(properties["floatTest"])
    assertThat(floatSchema.title).isEqualTo("floatTest")
    assertThat(floatSchema.nullable).isFalse()

    val doubleSchema = checkNotNull(properties["doubleTest"])
    assertThat(doubleSchema.title).isEqualTo("doubleTest")
    assertThat(doubleSchema.minimum).isEqualTo(5.0)
    assertThat(doubleSchema.nullable).isTrue()

    val listSchema = checkNotNull(properties["listTest"])
    assertThat(listSchema.title).isEqualTo("listTest")
    assertThat(listSchema.nullable).isFalse()
    assertThat(listSchema.items?.type).isEqualTo("INTEGER")

    val booleanSchema = checkNotNull(properties["booleanTest"])
    assertThat(booleanSchema.title).isEqualTo("booleanTest")
    assertThat(booleanSchema.description).isEqualTo("most likely true, very rarely false")
    assertThat(booleanSchema.nullable).isFalse()

    val stringSchema = checkNotNull(properties["stringTest"])
    assertThat(stringSchema.title).isEqualTo("stringTest")
    assertThat(stringSchema.nullable).isFalse()

    val enumSchema = checkNotNull(properties["enumTest"])
    assertThat(enumSchema.clazz).isEqualTo(EnumTest::class)
    assertThat(enumSchema.enum).isEqualTo(listOf("A", "B", "C"))
    assertThat(enumSchema.title).isEqualTo("enumTest")
    assertThat(enumSchema.nullable).isFalse()

    val nestedSchema = checkNotNull(properties["compositeSchemaTest"])
    assertThat(nestedSchema.clazz).isEqualTo(SecondarySchemaTestClass::class)
    assertThat(nestedSchema.properties).isNotNull()
    assertThat(nestedSchema.description).isNull()
    assertThat(nestedSchema.title).isEqualTo("compositeSchemaTest")
    assertThat(nestedSchema.nullable).isFalse()

    val nestedProperties = checkNotNull(nestedSchema.properties)
    val nestedStringSchema = checkNotNull(nestedProperties["testString"])
    assertThat(nestedStringSchema.title).isEqualTo("testString")
    assertThat(nestedStringSchema.description).isEqualTo("A nested string")
    assertThat(nestedStringSchema.nullable).isFalse()

    val stringEnumSchema = checkNotNull(properties["stringEnumTest"])
    assertThat(stringEnumSchema.enum).isEqualTo(listOf("NORTH", "SOUTH", "EAST", "WEST"))
    assertThat(stringEnumSchema.title).isEqualTo("stringEnumTest")
    assertThat(stringEnumSchema.nullable).isFalse()

    val lexicallyNestedSchema = checkNotNull(properties["nestedSchemaTest"])
    assertThat(lexicallyNestedSchema.clazz)
      .isEqualTo(RootSchemaTestClass.NestedSchemaTestClass::class)
    assertThat(lexicallyNestedSchema.properties).isNotNull()
    assertThat(lexicallyNestedSchema.title).isEqualTo("nestedSchemaTest")

    val lexicallyNestedProperties = checkNotNull(lexicallyNestedSchema.properties)
    val deeplyNestedStringSchema = checkNotNull(lexicallyNestedProperties["deeplyNestedString"])
    assertThat(deeplyNestedStringSchema.title).isEqualTo("deeplyNestedString")
  }
}
