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

import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.util.Assert
import com.google.firestore.v1.Value

// === Map Functions ===

internal val evaluateMapGet = binaryFunction { map: Map<String, Value>, key: String ->
  EvaluateResultValue(map[key] ?: return@binaryFunction EvaluateResultUnset)
}

internal val evaluateMap: EvaluateFunction = { params ->
  if (params.size % 2 != 0)
    throw Assert.fail("Function should have even number of params, but %d were given.", params.size)
  else
    block@{ input: MutableDocument ->
      val map: MutableMap<String, Value> = HashMap(params.size / 2)
      for (i in params.indices step 2) {
        val k = params[i](input).value ?: return@block EvaluateResultError
        if (!k.hasStringValue()) return@block EvaluateResultError
        val v = params[i + 1](input).value ?: return@block EvaluateResultError
        // It is against the API contract to include a key more than once.
        if (map.put(k.stringValue, v) != null) return@block EvaluateResultError
      }
      EvaluateResultValue(encodeValue(map))
    }
}
