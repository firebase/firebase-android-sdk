/*
 * Copyright 2025 Google LLC
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
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GroundingSupport
import com.google.firebase.ai.type.TextPart
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf

/** Performs structural validation of various API types */
class TypesValidator {

  fun validateResponse(response: GenerateContentResponse) {
    if (response.candidates.isNotEmpty() && hasText(response.candidates[0].content)) {
      response.text.shouldNotBeNull().shouldNotBeEmpty()
    } else if (response.candidates.isNotEmpty()) {
      hasText(response.candidates[0].content) shouldBe false
    }
    response.candidates.forEach { validateCandidate(it) }
  }

  fun validateCandidate(candidate: Candidate) {
    validateContent(candidate.content)
    if (candidate.groundingMetadata != null) {
      for (grounding in candidate.groundingMetadata.groundingSupports) {
        validateGroundingSupport(candidate, grounding)
      }
    }
  }

  fun validateGroundingSupport(candidate: Candidate, grounding: GroundingSupport) {
    val segment = grounding.segment
    segment.partIndex.shouldBeBetween(0, candidate.content.parts.size)
    val part = candidate.content.parts[segment.partIndex]
    part.shouldBeInstanceOf<TextPart>()
    val text = part.text
    segment.startIndex.shouldBeBetween(0, segment.endIndex)
    segment.endIndex shouldBeLessThanOrEqual text.length
    segment.text shouldBe text.substring(segment.startIndex, segment.endIndex)
  }

  fun validateContent(content: Content) {
    content.role shouldNotBe "user"
  }

  fun hasText(content: Content): Boolean {
    return content.parts.filterIsInstance<TextPart>().isNotEmpty()
  }
}
