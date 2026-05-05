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

package com.google.firebase.ai

import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Citation
import com.google.firebase.ai.type.CitationMetadata
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.convertUtf8IndexToUtf16
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Test

@OptIn(PublicPreviewAPI::class, ExperimentalSerializationApi::class)
class EncodingTests {
  val testStrings =
    listOf(
      "hello world",
      "¡Sí! Tengo muchos años.",
      "🙂🤝📩",
      "速度を上げて",
      "",
    )

  @Test
  fun `UTF-8 to UFT-16 index mapping matches length`() {
    for (string in testStrings) {
      val content = content { text(string) }
      val ba = string.toByteArray(Charsets.UTF_8)
      val index = convertUtf8IndexToUtf16(content, ba.size)
      index shouldBe string.length
    }
  }

  @Test
  fun `CitationMetadata gets converted to UTF-16`() {
    val internalCandidate =
      Candidate.Internal(
        content = Content.Internal("", listOf(TextPart.Internal("í abc í"))),
        citationMetadata =
          CitationMetadata.Internal(
            listOf(
              Citation.Internal(
                startIndex = 3,
                endIndex = 6,
              )
            )
          )
      )
    val candidate = internalCandidate.toPublic()
    val start = candidate.citationMetadata!!.citations.first().startIndex
    val end = candidate.citationMetadata.citations.first().endIndex
    (candidate.content.parts.first() as TextPart).text.substring(start, end) shouldBe "abc"
    start shouldBe 2
    end shouldBe 5
  }
}
