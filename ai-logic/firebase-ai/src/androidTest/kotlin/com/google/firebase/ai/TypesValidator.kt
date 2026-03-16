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
import com.google.firebase.ai.type.TextPart

/** Performs structural validation of various API types */
class TypesValidator {

  fun validateResponse(response: GenerateContentResponse) {
    if (response.candidates.isNotEmpty() && hasText(response.candidates[0].content)) {
      assert(response.text!!.isNotEmpty())
    } else if (response.candidates.isNotEmpty()) {
      assert(!hasText(response.candidates[0].content))
    }
    response.candidates.forEach { validateCandidate(it) }
  }

  fun validateCandidate(candidate: Candidate) {
    validateContent(candidate.content)
  }

  fun validateContent(content: Content) {
    assert(content.role != "user")
  }

  fun hasText(content: Content): Boolean {
    return content.parts.filterIsInstance<TextPart>().isNotEmpty()
  }
}
