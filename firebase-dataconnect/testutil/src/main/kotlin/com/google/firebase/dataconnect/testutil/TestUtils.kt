package com.google.firebase.dataconnect.testutil

import com.google.common.truth.StringSubject
import java.util.regex.Pattern

/**
 * Asserts that a string contains another string, verifying that the character immediately preceding
 * the text, if any, is a non-word character, and that the character immediately following the text,
 * if any, is also a non-word character. This effectively verifies that the given string is included
 * in the string being checked without being "mashed" into adjacent text.
 */
fun StringSubject.containsWithNonAdjacentText(text: String) =
  containsMatch("(^|\\W)${Pattern.quote(text)}($|\\W)")
