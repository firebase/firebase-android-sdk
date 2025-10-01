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

package com.google.firebase.dataconnect.sqlite

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataConnectSequenceNumberDatabaseUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  private val dbFile: File by lazy { File(temporaryFolder.newFolder(), "db.sqlite") }

  @Test
  fun `nextSequenceNumber should return distinct numbers`() = runTest {
    val db = DataConnectSequenceNumberDatabase(dbFile, Dispatchers.IO, mockk(relaxed = true))
    val sequenceNumbers: List<Long> =
      try {
        buildList { repeat(1000) { add(db.nextSequenceNumber()) } }
      } finally {
        db.close()
      }

    sequenceNumbers.distinct() shouldContainExactlyInAnyOrder sequenceNumbers
  }

  @Test
  fun `nextSequenceNumber should return montonically-increasing numbers`() = runTest {
    val db = DataConnectSequenceNumberDatabase(dbFile, Dispatchers.IO, mockk(relaxed = true))
    val sequenceNumbers: List<Long> =
      try {
        buildList { repeat(1000) { add(db.nextSequenceNumber()) } }
      } finally {
        db.close()
      }

    sequenceNumbers shouldContainExactly sequenceNumbers.sorted()
  }

  @Test
  fun `nextSequenceNumber should montonically increase even after database re-opens`() = runTest {
    val sequenceNumbers: List<Long> = buildList {
      repeat(5) {
        val db = DataConnectSequenceNumberDatabase(dbFile, Dispatchers.IO, mockk(relaxed = true))
        try {
          repeat(1000) { add(db.nextSequenceNumber()) }
        } finally {
          db.close()
        }
      }
    }

    sequenceNumbers shouldContainExactly sequenceNumbers.sorted()
  }
}
