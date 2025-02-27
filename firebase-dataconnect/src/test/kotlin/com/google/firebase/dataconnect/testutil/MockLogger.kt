/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import io.mockk.Matcher
import io.mockk.MockKMatcherScope
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.spyk
import io.mockk.verify
import java.util.regex.Pattern

internal fun newMockLogger(key: String, emit: (String) -> Unit = {}): Logger {
  val name = "mockLogger-$key"
  return spyk(Logger(name), name = name) {
    every { log(any(), any(), any()) } answers
      {
        val exception: Throwable? = firstArg()
        val level: LogLevel = secondArg()
        val message: String = thirdArg()
        if (exception === null) {
          emit("$name [$level] $message")
        } else {
          emit("$name [$level] $message ($exception)")
        }
      }
    excludeRecords {
      this@spyk.name
      this@spyk.nameWithId
      this@spyk.toString()
    }
  }
}

private data class LoggedMessageContainsMatcher(val text: String, val ignoreCase: Boolean) :
  Matcher<String> {
  private val pattern = "(^|\\W)${Pattern.quote(text)}($|\\W)"
  private val expr = Pattern.compile(pattern, if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)

  override fun match(arg: String?) = if (arg === null) false else expr.matcher(arg).find()

  override fun toString(): String = "loggedMessageContains(\"$text\", ignoreCase=$ignoreCase)"
}

private fun MockKMatcherScope.matchStringWithNonAdjacentText(
  text: String,
  ignoreCase: Boolean = false
) = match(LoggedMessageContainsMatcher(text, ignoreCase))

internal fun Logger.shouldHaveLoggedAtLeastOneMessageContaining(
  text: String,
  ignoreCase: Boolean = false
) {
  verify(atLeast = 1) { log(any(), any(), matchStringWithNonAdjacentText(text, ignoreCase)) }
}

internal fun Logger.shouldHaveLoggedExactlyOneMessageContaining(
  text: String,
  ignoreCase: Boolean = false
) {
  verify(exactly = 1) { log(any(), any(), matchStringWithNonAdjacentText(text, ignoreCase)) }
}

internal fun Logger.shouldHaveLoggedExactlyOneMessageContaining(
  text: String,
  exception: Throwable,
  ignoreCase: Boolean = false
) {
  verify(exactly = 1) {
    log(refEq(exception), any(), matchStringWithNonAdjacentText(text, ignoreCase))
  }
}

internal fun Logger.shouldNotHaveLoggedAnyMessagesContaining(
  text: String,
  ignoreCase: Boolean = false
) {
  verify(inverse = true) { log(any(), any(), matchStringWithNonAdjacentText(text, ignoreCase)) }
}
