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

import com.google.firebase.firestore.model.Values.strictCompare
import com.google.firebase.firestore.model.Values.strictEquals
import com.google.firestore.v1.Value

// === Comparison Functions ===

internal val evaluateEq: EvaluateFunction = binaryFunction { p1: Value, p2: Value ->
  EvaluateResult.boolean(strictEquals(p1, p2))
}

internal val evaluateNeq: EvaluateFunction = binaryFunction { p1: Value, p2: Value ->
  EvaluateResult.boolean(strictEquals(p1, p2)?.not())
}

internal val evaluateGt: EvaluateFunction = comparison { v1, v2 ->
  (strictCompare(v1, v2) ?: return@comparison false) > 0
}

internal val evaluateGte: EvaluateFunction = comparison { v1, v2 ->
  when (strictEquals(v1, v2)) {
    true -> true
    false -> (strictCompare(v1, v2) ?: return@comparison false) > 0
    null -> null
  }
}

internal val evaluateLt: EvaluateFunction = comparison { v1, v2 ->
  (strictCompare(v1, v2) ?: return@comparison false) < 0
}

internal val evaluateLte: EvaluateFunction = comparison { v1, v2 ->
  when (strictEquals(v1, v2)) {
    true -> true
    false -> (strictCompare(v1, v2) ?: return@comparison false) < 0
    null -> null
  }
}

internal val evaluateNot: EvaluateFunction = unaryFunction { b: Boolean ->
  EvaluateResult.boolean(b.not())
}
