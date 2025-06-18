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
