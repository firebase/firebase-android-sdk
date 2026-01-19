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

package com.google.firebase.appdistribution.gradle

import com.google.common.collect.ImmutableList
import com.google.firebase.appdistribution.gradle.AppDistributionException.Reason.APK_NOT_FOUND
import com.google.firebase.appdistribution.gradle.OptionsUtils.ensureFileExists
import com.google.firebase.appdistribution.gradle.OptionsUtils.getValueFromStringOrFile
import com.google.firebase.appdistribution.gradle.OptionsUtils.splitCommaOrNewlineSeparatedString
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Test

class OptionsUtilsTest {
  @Test
  fun testEnsureFile_returnsFileIfFound() {
    val expected = FixtureUtils.getFixtureAsFile("test.apk")
    val actual = ensureFileExists(expected.absolutePath, APK_NOT_FOUND)
    assertEquals(expected.absolutePath, actual.absolutePath)
  }

  @Test
  fun testEnsureFile_throwsExceptionIfMissing() {
    val e =
      assertFailsWith(AppDistributionException::class) {
        ensureFileExists("nonexistent.txt", APK_NOT_FOUND)
      }
    assertEquals(APK_NOT_FOUND.message, e.message)
  }

  @Test
  fun testExtractListFromCommaSeparatedString_extractsCorrectly() {
    val list = splitCommaOrNewlineSeparatedString("a@e.mail,b@e.mail,c@e.mail")
    assertEquals(listOf("a@e.mail", "b@e.mail", "c@e.mail"), list)
  }

  @Test
  fun testExtractListFromCommaSeparatedString_ignoresExtraSpacesAndNewlines() {
    val list =
      splitCommaOrNewlineSeparatedString("   \n a@e.mail,\n\n    b@e.mail,\nc@e.mail,    \n")
    assertEquals(ImmutableList.of("a@e.mail", "b@e.mail", "c@e.mail"), ImmutableList.copyOf(list))
  }

  @Test
  fun testExtractListFromCommaSeparatedString_returnsEmptyListIfNull() {
    val list = splitCommaOrNewlineSeparatedString(null)
    assertEquals(0, list.size.toLong())
  }

  @Test
  fun testExtractListFromCommaSeparatedString_returnsEmptyListIfEmptyString() {
    val list = splitCommaOrNewlineSeparatedString("")
    assertEquals(0, list.size.toLong())
  }

  @Test
  fun testGetValueFromValueOrPath_prefersValueOverPath() {
    val testersPath = FixtureUtils.getFixtureAsFile("testers.txt").absolutePath
    assertEquals(
      "value1@tester.com,value2@tester.com",
      getValueFromStringOrFile("value1@tester.com,value2@tester.com", testersPath)
    )
  }

  @Test
  fun testGetValueFromValueOrPath_usesPathIfValueIsMissing() {
    val testersPath = FixtureUtils.getFixtureAsFile("testers.txt").absolutePath
    assertEquals("a@a.com,b@b.com\n", getValueFromStringOrFile(null, testersPath))
  }

  @Test
  fun testGetValueFromValueOrPath_throwsIfTheFilePathDoesntExist() {
    val testersPath = FixtureUtils.getFixtureAsFile("nonexistent.txt").absolutePath
    assertFailsWith(IllegalArgumentException::class) { getValueFromStringOrFile(null, testersPath) }
  }
}
