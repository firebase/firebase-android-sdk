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

package com.google.firebase.crashlytics.buildtools.gradle.tasks

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UploadMappingFileForR8MapIdTaskTest {

  @TempDir lateinit var tempDir: File

  private fun mappingFile(contents: String): File =
    File(tempDir, "mapping.txt").apply { writeText(contents) }

  @Test
  fun `extractR8MapId returns the pg_map_id from a typical R8 mapping header`() {
    val mapping =
      mappingFile(
        """
        # compiler: R8
        # compiler_version: 9.1.31
        # min_api: 24
        # common_typos_disable
        # {"id":"com.android.tools.r8.mapping","version":"2.2"}
        # pg_map_id: af97edca6a8456b027e588e6168d6310661fa953e25f5bce3730fb21041f8dfa
        # pg_map_hash: SHA-256 af97edca6a8456b027e588e6168d6310661fa953e25f5bce3730fb21041f8dfa
        com.example.Foo -> a.a:
            void bar() -> a
        """
          .trimIndent()
      )

    assertThat(UploadMappingFileForR8MapIdTask.extractR8MapId(mapping))
      .isEqualTo("af97edca6a8456b027e588e6168d6310661fa953e25f5bce3730fb21041f8dfa")
  }

  @Test
  fun `extractR8MapId ignores the similarly named pg_map_hash comment`() {
    // pg_map_hash also starts with a hex value but is prefixed by `SHA-256 `, so it must not match.
    val mapping =
      mappingFile(
        """
        # pg_map_hash: SHA-256 deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef
        # pg_map_id: 0123456789abcdef0123456789abcdef
        """
          .trimIndent()
      )

    assertThat(UploadMappingFileForR8MapIdTask.extractR8MapId(mapping))
      .isEqualTo("0123456789abcdef0123456789abcdef")
  }

  @Test
  fun `extractR8MapId returns null when no pg_map_id is present`() {
    val mapping =
      mappingFile(
        """
        # compiler: R8
        com.example.Foo -> a.a:
            void bar() -> a
        """
          .trimIndent()
      )

    assertThat(UploadMappingFileForR8MapIdTask.extractR8MapId(mapping)).isNull()
  }

  @Test
  fun `extractR8MapId returns null for an empty mapping file`() {
    assertThat(UploadMappingFileForR8MapIdTask.extractR8MapId(mappingFile(""))).isNull()
  }

  @Test
  fun `extractR8MapId returns null when the pg_map_id value is missing`() {
    assertThat(UploadMappingFileForR8MapIdTask.extractR8MapId(mappingFile("# pg_map_id:"))).isNull()
  }

  @Test
  fun `extractR8MapId returns the r8_map_id when present`() {
    val mapping =
      mappingFile(
        """
        # compiler: R8
        # r8_map_id: 1111aaaa2222bbbb3333cccc4444dddd
        com.example.Foo -> a.a:
        """
          .trimIndent()
      )

    assertThat(UploadMappingFileForR8MapIdTask.extractR8MapId(mapping))
      .isEqualTo("1111aaaa2222bbbb3333cccc4444dddd")
  }

  @Test
  fun `extractR8MapId prefers r8_map_id over pg_map_id regardless of order`() {
    val r8First = mappingFile("# r8_map_id: aaaa1111\n# pg_map_id: bbbb2222")
    val pgFirst = mappingFile("# pg_map_id: bbbb2222\n# r8_map_id: aaaa1111")

    assertThat(UploadMappingFileForR8MapIdTask.extractR8MapId(r8First)).isEqualTo("aaaa1111")
    assertThat(UploadMappingFileForR8MapIdTask.extractR8MapId(pgFirst)).isEqualTo("aaaa1111")
  }
}
