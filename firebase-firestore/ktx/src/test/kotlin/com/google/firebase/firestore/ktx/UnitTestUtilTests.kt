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
import com.google.firebase.firestore.assertThrows
import org.junit.Test

class UnitTestUtilTests {
    @Test
    fun `assertThrows extension function return assertThat if runnable generate correct type of exception`() {
        assertThrows<ArrayIndexOutOfBoundsException> { listOf(1, 2, 3).get(100) }.isNotNull()
        assertThrows<ArrayIndexOutOfBoundsException> { listOf(1, 2, 3).get(100) }
            .hasMessageThat()
            .contains("Index 100 out of bounds for length 3")
    }

    @Test
    fun `assertThrows extension function throws if runnable generate a wrong type of exception`() {
        try {
            // throw wrong type of exception
            assertThrows<IllegalArgumentException> { listOf(1, 2, 3).get(100) }
                .hasMessageThat()
                .contains("foobar")
        } catch (error: AssertionError) {
            assertThat(error)
                .hasMessageThat()
                .contains(
                    "expected:<java.lang.IllegalArgumentException> but was:<java.lang.ArrayIndexOutOfBoundsException>"
                )
        }
    }

    @Test
    fun `assertThrows extension function throws if runnable does not throw an exception`() {
        try {
            // does not throw any exception
            assertThrows<IllegalArgumentException> { listOf(1, 2, 3).get(0) }
                .hasMessageThat()
                .contains("foobar")
        } catch (error: AssertionError) {
            assertThat(error)
                .hasMessageThat()
                .contains(
                    "expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown"
                )
        }
    }
}
