/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RandomUtilTest {

  @Test
  fun `nextAlphanumericString() should return an empty string if the given 'length' is 0`() {
    val generatedStringWithLengthOf0 = Random.nextAlphanumericString(0)

    assertThat(generatedStringWithLengthOf0.length).isEqualTo(0)
  }

  @Test
  fun `nextAlphanumericString() should return a string of length 1 if the given 'length' is 1`() {
    val generatedStringWithLengthOf1 = Random.nextAlphanumericString(1)

    assertThat(generatedStringWithLengthOf1.length).isEqualTo(1)
  }

  @Test
  fun `nextAlphanumericString() should return a string of length 1000 if the given 'length' is 1000`() {
    val generatedStringWithLengthOf1000 = Random.nextAlphanumericString(1000)

    assertThat(generatedStringWithLengthOf1000.length).isEqualTo(1000)
  }

  @Test
  fun `nextAlphanumericString() should throw if 'length' is less than zero`() {
    val exception =
      assertThrows(IllegalArgumentException::class.java) { Random.nextAlphanumericString(-1) }
    assertThat(exception).hasMessageThat().containsMatch("(\\W|^)length(\\W|$)")
    assertThat(exception).hasMessageThat().containsMatch("(\\W|^)-1(\\W|$)")
  }

  @Test
  fun `nextAlphanumericString() should return a different string each time it is invoked`() {
    val generatedStrings = buildList { repeat(100) { add(Random.nextAlphanumericString(10)) } }

    assertThat(generatedStrings).containsNoDuplicates()
  }

  @Test
  fun `nextAlphanumericString() should return a string containing only alphanumeric characters`() {
    val generatedString = Random.nextAlphanumericString(10000)

    val unexpectedCharacters =
      generatedString.filter { !VALID_ALPHNUMERIC_CHARACTERS.contains(it) }.toSortedSet()
    assertWithMessage("unexpectedCharacters").that(unexpectedCharacters).isEmpty()
  }

  private companion object {
    @Suppress("SpellCheckingInspection")
    const val VALID_ALPHNUMERIC_CHARACTERS = "01234567890abcdefghijklmnopqrstuvwxyz"
  }
}
