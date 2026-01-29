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

package com.google.firebase.firestore.util

import com.google.firebase.firestore.model.Values
import com.google.firestore.v1.Value
import com.google.re2j.Matcher

internal class RegexUtils {
  companion object {
    /**
     * Extracts the result from a successful regex match into a Firestore [Value].
     *
     * Behavior depends on the presence of capturing groups in the pattern:
     * - **0 Capturing Groups:** Returns the entire matched substring.
     * - **1 Capturing Group:** Returns the substring captured by the group. If the group exists but
     * captured nothing (null), returns [Values.NULL_VALUE].
     *
     * @param matcher The RE2J matcher, which must be currently positioned at a valid match.
     * @return A string [Value] containing the match or captured group, or a null [Value].
     * @throws IllegalArgumentException if the pattern contains more than one capturing group.
     */
    fun handleMatch(matcher: Matcher): Value {
      if (matcher.groupCount() > 1) {
        throw IllegalArgumentException("At most one capture group is supported")
      }

      if (matcher.groupCount() == 0) {
        return Values.encodeValue(matcher.group())
      } else if (matcher.group(1) != null) {
        return Values.encodeValue(matcher.group(1))
      }

      return Values.NULL_VALUE
    }
  }
}
