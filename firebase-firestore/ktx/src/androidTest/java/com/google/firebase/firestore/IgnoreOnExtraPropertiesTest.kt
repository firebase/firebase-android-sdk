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

package com.google.firebase.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.ktx.toObject
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IgnoreOnExtraPropertiesTest {
    @Serializable private data class Cat(val miao: String? = "miao")
    @Serializable private data class Dog(val wow: String? = "wow")

    @Test
    fun default_behavior_is_equivalent() {
        // default behavior is ignore on extra properties
        val docRefKtx = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("123")

        docRefKtx.set(Cat())
        docRefPOJO.withoutCustomMappers { set(Cat()) }

        val resultKtx = waitFor(docRefKtx.get()).toObject<Dog>()
        val resultPOJO = waitFor(docRefPOJO.get()).withoutCustomMappers { toObject<Dog>() }
        assertThat(resultKtx).isEqualTo(resultPOJO)
    }

    @Serializable @ThrowOnExtraProperties private data class Mouse(val zhi: String? = "zhi")
    @Serializable
    @ThrowOnExtraProperties
    private data class Elephant(val trumpts: String? = "trumpts")
    @Test
    fun throwOnExtraProperties_is_equivalent() {
        val docRefKtx = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("123")

        docRefKtx.set(Mouse())
        docRefPOJO.withoutCustomMappers { set(Mouse()) }

        testAssertThrows<Exception> { waitFor(docRefKtx.get()).toObject<Elephant>() }
            .hasMessageThat()
            .contains("Can not match")

        testAssertThrows<Exception> {
                waitFor(docRefPOJO.get()).withoutCustomMappers { toObject<Elephant>() }
            }
            .hasMessageThat()
            .contains("No setter/field for")
    }

    @Serializable
    private data class Zoo(val name: String = "Zoo", val animal: Elephant = Elephant())
    @Serializable
    private data class Backyard(val name: String = "Backyard", val animal: Dog = Dog())
    @Test
    fun ignoreExtraProperties_on_nested_object_is_equivalent() {
        val docRefKtx = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("123")

        docRefKtx.set(Zoo())
        docRefPOJO.withoutCustomMappers { set(Zoo()) }

        val resultKtx = waitFor(docRefKtx.get()).toObject<Backyard>()
        val resultPOJO = waitFor(docRefPOJO.get()).withoutCustomMappers { toObject<Backyard>() }
        assertThat(resultKtx).isEqualTo(resultPOJO)
    }

    @Serializable
    @ThrowOnExtraProperties
    private data class ZooThrow(val name: String = "Zoo", val animal: Elephant = Elephant())

    @Serializable
    @ThrowOnExtraProperties
    private data class BackyardThrow(val name: String = "Backyard", val animal: Dog = Dog())

    @Test
    fun throwOnExtraProperties_on_nested_object_is_equivalent() {
        // throwOnExtraProperties does not work on nested Objects
        val docRefKtx = testCollection("ktx").document("123")
        val docRefPOJO = testCollection("pojo").document("123")

        docRefKtx.set(ZooThrow())
        docRefPOJO.withoutCustomMappers { set(ZooThrow()) }

        val resultKtx = waitFor(docRefKtx.get()).toObject<BackyardThrow>()
        val resultPOJO = waitFor(docRefPOJO.get()).withoutCustomMappers { toObject<BackyardThrow>() }
        assertThat(resultKtx).isEqualTo(resultPOJO)
    }
}
