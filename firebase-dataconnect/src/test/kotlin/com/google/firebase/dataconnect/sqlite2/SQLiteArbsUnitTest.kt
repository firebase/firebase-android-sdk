/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.sqlite2

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.sequences.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SQLiteArbsUnitTest {

  @Test
  fun `stringWithApostrophes should generate strings with apostrophes`() = runTest {
    val strings = listOf("", "jjpr5vt2c5", "h6ve394y6f", "mqf4q8cve6", "tty5xj523r", "yvk8tbrjg7")
    val stringArb = Arb.of(strings)
    val apostropheCountArb = 1..10
    checkAll(200, Arb.sqlite.stringWithApostrophes(stringArb, apostropheCountArb)) { testCase ->
      testCase.run {
        assertSoftly {
          withClue("stringWithApostrophes apostrophe count") {
            stringWithApostrophes.count { it == '\'' } shouldBe testCase.apostropheCount
          }
          withClue("stringWithApostrophesEscaped apostrophe count") {
            val regex = Regex.fromLiteral("''")
            regex.findAll(stringWithApostrophesEscaped) shouldHaveSize testCase.apostropheCount
          }
          val stringWithApostrophesWithoutApostrophes =
            stringWithApostrophes.filterNot { it == '\'' }
          val stringWithApostrophesEscapedWithoutApostrophes =
            stringWithApostrophesEscaped.filterNot { it == '\'' }
          withClue("stringWithApostrophesWithoutApostrophes") {
            strings shouldContain stringWithApostrophesWithoutApostrophes
          }
          withClue("stringWithApostrophesEscapedWithoutApostrophes") {
            stringWithApostrophesEscapedWithoutApostrophes shouldBe
              stringWithApostrophesWithoutApostrophes
          }
        }
      }
    }
  }
}
