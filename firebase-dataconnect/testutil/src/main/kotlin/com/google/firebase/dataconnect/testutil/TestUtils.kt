package com.google.firebase.dataconnect.testutil

import com.google.common.truth.StringSubject
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Asserts that a string contains another string, verifying that the character immediately preceding
 * the text, if any, is a non-word character, and that the character immediately following the text,
 * if any, is also a non-word character. This effectively verifies that the given string is included
 * in the string being checked without being "mashed" into adjacent text.
 */
fun StringSubject.containsWithNonAdjacentText(text: String) =
  containsMatch("(^|\\W)${Pattern.quote(text)}($|\\W)")

/**
 * Generates and returns a string containing random alphanumeric characters.
 *
 * @param length the number of random characters to generate and include in the returned string; if
 * `null`, then a length of 20 is used.
 * @return a string containing the given (or default) number of random alphanumeric characters.
 */
fun Random.nextAlphanumericString(length: Int? = null): String = buildString {
  var numCharactersRemaining =
    if (length === null) 20 else length.also { require(it >= 0) { "invalid length: $it" } }

  while (numCharactersRemaining > 0) {
    // Ignore the first character of the alphanumeric string because its distribution is not random.
    val randomCharacters = abs(nextLong()).toAlphaNumericString()
    val numCharactersToAppend = min(numCharactersRemaining, randomCharacters.length - 1)
    append(randomCharacters, 1, numCharactersToAppend)
    numCharactersRemaining -= numCharactersToAppend
  }
}

/**
 * Converts this number to a base-36 string, which uses the 26 letters from the English alphabet and
 * the 10 numeric digits.
 */
fun Long.toAlphaNumericString(): String = toString(36)
