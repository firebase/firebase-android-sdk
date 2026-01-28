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

package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.pipeline.evaluation.*

/**
 * A registry of all built-in pipeline functions.
 *
 * This is used internally to look up the evaluation logic for a given function name when
 * deserializing a pipeline from a protobuf.
 */
internal object FunctionRegistry {
  val functions: Map<String, EvaluateFunction> =
    mapOf(
      "and" to evaluateAnd,
      "or" to evaluateOr,
      "xor" to evaluateXor,
      "not" to evaluateNot,
      "round" to evaluateRound,
      "ceil" to evaluateCeil,
      "floor" to evaluateFloor,
      "pow" to evaluatePow,
      "sqrt" to evaluateSqrt,
      "add" to evaluateAdd,
      "subtract" to evaluateSubtract,
      "multiply" to evaluateMultiply,
      "divide" to evaluateDivide,
      "mod" to evaluateMod,
      "eq_any" to evaluateEqAny,
      "not_eq_any" to evaluateNotEqAny,
      "is_nan" to evaluateIsNaN,
      "is_not_nan" to evaluateIsNotNaN,
      "is_null" to evaluateIsNull,
      "is_not_null" to evaluateIsNotNull,
      "replace_first" to evaluateReplaceFirst,
      "replace_all" to evaluateReplaceAll,
      "char_length" to evaluateCharLength,
      "byte_length" to evaluateByteLength,
      "like" to evaluateLike,
      "regex_contains" to evaluateRegexContains,
      "regex_find" to evaluateRegexFind,
      "regex_find_all" to evaluateRegexFindAll,
      "regex_match" to evaluateRegexMatch,
      "logical_max" to evaluateLogicalMaximum,
      "logical_min" to evaluateLogicalMinimum,
      "reverse" to evaluateReverse,
      "str_contains" to evaluateStrContains,
      "starts_with" to evaluateStartsWith,
      "ends_with" to evaluateEndsWith,
      "to_lowercase" to evaluateToLowercase,
      "to_uppercase" to evaluateToUppercase,
      "trim" to evaluateTrim,
      "str_concat" to evaluateStrConcat,
      "map" to evaluateMap,
      "map_get" to evaluateMapGet,

      // Functions that are in evaluation.kt but not yet in expressions.kt
      "is_error" to evaluateIsError,
      "exists" to evaluateExists,
      "cond" to evaluateCond,
      "eq" to evaluateEq,
      "neq" to evaluateNeq,
      "gt" to evaluateGt,
      "gte" to evaluateGte,
      "lt" to evaluateLt,
      "lte" to evaluateLte,
      "array" to evaluateArray,
      "array_contains" to evaluateArrayContains,
      "array_contains_any" to evaluateArrayContainsAny,
      "array_contains_all" to evaluateArrayContainsAll,
      "array_get" to evaluateArrayGet,
      "array_length" to evaluateArrayLength,
      "timestamp_add" to evaluateTimestampAdd,
      "timestamp_sub" to evaluateTimestampSub,
      "timestamp_to_unix_micros" to evaluateTimestampToUnixMicros,
      "timestamp_to_unix_millis" to evaluateTimestampToUnixMillis,
      "timestamp_to_unix_seconds" to evaluateTimestampToUnixSeconds,
      "unix_micros_to_timestamp" to evaluateUnixMicrosToTimestamp,
      "unix_millis_to_timestamp" to evaluateUnixMillisToTimestamp,
      "unix_seconds_to_timestamp" to evaluateUnixSecondsToTimestamp,

      // Functions that are not yet implemented
      "bit_and" to notImplemented,
      "bit_or" to notImplemented,
      "bit_xor" to notImplemented,
      "bit_not" to notImplemented,
      "bit_left_shift" to notImplemented,
      "bit_right_shift" to notImplemented,
      "is_absent" to notImplemented,
      "rand" to notImplemented,
      "map_merge" to notImplemented,
      "map_remove" to notImplemented,
      "cosine_distance" to notImplemented,
      "dot_product" to notImplemented,
      "timestamp_trunc" to notImplemented,
      "split" to evaluateSplit,
      "substring" to evaluateSubstring,
      "ltrim" to evaluateLTrim,
      "rtrim" to evaluateRTrim
    )
}
