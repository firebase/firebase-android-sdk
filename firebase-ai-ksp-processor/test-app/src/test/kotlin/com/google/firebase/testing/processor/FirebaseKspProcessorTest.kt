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

    assertThat(rootSchema.properties?.get("integerTest")).isNotNull()
    val intSchema = rootSchema.properties?.get("integerTest")!!
    assertThat(intSchema.title).isEqualTo("integerTest")
    assertThat(intSchema.nullable).isEqualTo(true)

    assertThat(rootSchema.properties?.get("longTest")).isNotNull()
    val longSchema = rootSchema.properties?.get("longTest")!!
    assertThat(longSchema.title).isEqualTo("longTest")
    assertThat(longSchema.description).isEqualTo("a test long that takes up multiple lines")
    assertThat(longSchema.nullable).isEqualTo(false)

    assertThat(rootSchema.properties?.get("floatTest")).isNotNull()
    val floatSchema = rootSchema.properties?.get("floatTest")!!
    assertThat(floatSchema.title).isEqualTo("floatTest")
    assertThat(floatSchema.nullable).isEqualTo(false)

    assertThat(rootSchema.properties?.get("doubleTest")).isNotNull()
    val doubleSchema = rootSchema.properties?.get("doubleTest")!!
    assertThat(doubleSchema.title).isEqualTo("doubleTest")
    assertThat(doubleSchema.minimum).isEqualTo(5.0)
    assertThat(doubleSchema.nullable).isEqualTo(true)

    assertThat(rootSchema.properties?.get("listTest")).isNotNull()
    val listSchema = rootSchema.properties?.get("listTest")!!
    assertThat(listSchema.title).isEqualTo("listTest")
    assertThat(listSchema.nullable).isEqualTo(false)
    assertThat(listSchema.items?.type).isEqualTo("INTEGER")

    assertThat(rootSchema.properties?.get("booleanTest")).isNotNull()
    val booleanSchema = rootSchema.properties?.get("booleanTest")!!
    assertThat(booleanSchema.title).isEqualTo("booleanTest")
    assertThat(booleanSchema.description).isEqualTo("most likely true, very rarely false")
    assertThat(booleanSchema.nullable).isEqualTo(false)

    assertThat(rootSchema.properties?.get("stringTest")).isNotNull()
    val stringSchema = rootSchema.properties?.get("stringTest")!!
    assertThat(stringSchema.title).isEqualTo("stringTest")
    assertThat(stringSchema.nullable).isEqualTo(false)

    assertThat(rootSchema.properties?.get("enumTest")).isNotNull()
    val enumSchema = rootSchema.properties?.get("enumTest")!!
    assertThat(enumSchema.clazz).isEqualTo(EnumTest::class)
    assertThat(enumSchema.enum).isEqualTo(listOf("A", "B", "C"))
    assertThat(enumSchema.title).isEqualTo("enumTest")
    assertThat(enumSchema.nullable).isEqualTo(false)

    assertThat(rootSchema.properties?.get("objTest")).isNotNull()
    val objSchema = rootSchema.properties?.get("objTest")!!
    assertThat(objSchema.clazz).isEqualTo(SecondarySchemaTestClass::class)
    assertThat(objSchema.properties).isNotNull()
    assertThat(objSchema.description).isEqualTo("class kdoc should be used if property kdocs aren't present")
    assertThat(objSchema.title).isEqualTo("objTest")
    assertThat(objSchema.nullable).isEqualTo(false)
  }
}
