// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.pipeline.evaluation

import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue

/**
 * A test case for a unary function.
 *
 * @param name The name of the test case, used for logging.
 * @param input The input to the unary function.
 */
data class UnaryTestCase(val name: String, val input: Expression)

/**
 * A test case for a binary function.
 *
 * @param name The name of the test case, used for logging.
 * @param left The left input to the binary function.
 * @param right The right input to the binary function.
 */
data class BinaryTestCase(val name: String, val left: Expression, val right: Expression)

/** A collection of test cases for functions that mirror their input for null/unset/NaN. */
object MirroringTestCases {
  val UNARY_MIRROR_TEST_CASES =
    listOf(
      UnaryTestCase("null", nullValue()),
      UnaryTestCase("unset", field("nonexistent")),
    )

  val BINARY_MIRROR_TEST_CASES =
    listOf(
      BinaryTestCase("null, null", nullValue(), nullValue()),
      BinaryTestCase("null, unset", nullValue(), field("nonexistent")),
      BinaryTestCase("unset, null", field("nonexistent"), nullValue()),
      BinaryTestCase("unset, unset", field("nonexistent"), field("nonexistent")),
    )
}
