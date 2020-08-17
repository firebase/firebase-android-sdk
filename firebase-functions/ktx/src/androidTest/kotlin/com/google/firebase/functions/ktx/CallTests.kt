// Copyright 2019 Google LLC
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

package com.google.firebase.functions.ktx

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

const val APP_ID = "APP_ID"
const val API_KEY = "API_KEY"

@RunWith(AndroidJUnit4::class)
class CallTests {
    companion object {
        lateinit var app: FirebaseApp

        @BeforeClass @JvmStatic fun setup() {
            app = Firebase.initialize(InstrumentationRegistry.getContext())!!
        }

        @AfterClass @JvmStatic fun cleanup() {
            app.delete()
        }
    }

    @Test
    fun testDataCall() {
        val functions = Firebase.functions(app)
        val input = hashMapOf(
            "bool" to true,
            "int" to 2,
            "long" to 3L,
            "string" to "four",
            "array" to listOf(5, 6),
            "null" to null
        )

        var function = functions.getHttpsCallable("dataTest")
        val actual = Tasks.await(function.call(input)).getData()

        assertThat(actual).isInstanceOf(Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val map = actual as Map<String, *>
        assertThat(map["message"]).isEqualTo("stub response")
        assertThat(map["code"]).isEqualTo(42)
        assertThat(map["long"]).isEqualTo(420L)
    }

    @Test
    fun testNullDataCall() {
        val functions = Firebase.functions(app)
        var function = functions.getHttpsCallable("nullTest")
        val actual = Tasks.await(function.call(null)).getData()

        assertThat(actual).isNull()
    }

    @Test
    fun testEmptyDataCall() {
        val functions = Firebase.functions(app)
        var function = functions.getHttpsCallable("nullTest")
        val actual = Tasks.await(function.call()).getData()

        assertThat(actual).isNull()
    }
}
