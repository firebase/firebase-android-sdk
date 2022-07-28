// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.ktx

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.documentReference
import com.google.firebase.firestore.ktx.serialization.decodeFromMap
import com.google.firebase.firestore.ktx.serialization.encodeToMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CustomSerializerSupportTests {

    @Serializable(with = ColorAsStringSerializer::class) private data class Color(val rgb: Int)

    @Serializable private data class House(val name: String, val color: Color)

    private object ColorAsStringSerializer : KSerializer<Color> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("Color", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Color) {
            val string: String =
                if (value.rgb == 0x00ff00) {
                    "GREEN"
                } else {
                    "NOT GREEN"
                }
            encoder.encodeString(string)
        }

        override fun deserialize(decoder: Decoder): Color {
            val string = decoder.decodeString()
            return if (string == "GREEN") {
                Color(0X00ff00)
            } else {
                Color(0x000000)
            }
        }
    }

    private val green = Color(0x00ff00)
    private val brt1 = House("BRT-1", green)

    @Test
    fun `custom serializer round Trip Test`() {
        // serialize Color to String
        val expectedEncodedMap = mapOf<String, Any>("name" to "BRT-1", "color" to "GREEN")
        assertThat(encodeToMap(brt1)).containsExactlyEntriesIn(expectedEncodedMap)
        assertThat(decodeFromMap<House>(expectedEncodedMap, firestoreDocument)).isEqualTo(brt1)
    }

    @Serializable
    private data class City(
        val cityName: String,
        @Serializable(with = HouseAsStringSerializer::class) val building: House
    )
    private val waterloo = City("waterloo", brt1)
    private object HouseAsStringSerializer : KSerializer<House> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("House", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): House {
            val string = decoder.decodeString()
            val (name, color) = string.split("/")
            return House(name, Color(color.toInt()))
        }

        override fun serialize(encoder: Encoder, value: House) {
            encoder.encodeString(value.name + "/" + value.color.rgb)
        }
    }

    @Test
    fun `nested custom object serializer round trip test`() {
        // serialize City to String by connecting all its name and its color's rgb value with slash
        val expectedEncodedMap =
            mapOf<String, Any>("cityName" to "waterloo", "building" to "BRT-1/65280")
        assertThat(encodeToMap(waterloo)).containsExactlyEntriesIn(expectedEncodedMap)
        assertThat(decodeFromMap<City>(expectedEncodedMap, firestoreDocument)).isEqualTo(waterloo)
    }
}

private val firestoreDocument: DocumentReference = documentReference("abc/1234")
