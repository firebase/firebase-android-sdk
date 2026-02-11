/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.firestore.pipeline

import com.google.common.annotations.Beta
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.pipeline.evaluation.EvaluateDocument
import com.google.firebase.firestore.pipeline.evaluation.EvaluationContext
import com.google.firestore.v1.Value

/**
 * A selectable field or expression.
 *
 * This class abstracts over fields and expressions that can be selected in a pipeline.
 */
@Beta
abstract class Selectable internal constructor() : Expression() {
  internal abstract val alias: String
  internal abstract val expr: Expression
  internal abstract override fun toProto(userDataReader: UserDataReader): Value
  internal abstract override fun evaluateFunction(context: EvaluationContext): EvaluateDocument
  internal abstract override fun canonicalId(): String

  override fun alias(alias: String): AliasedExpression {
    return AliasedExpression(alias, this)
  }

  internal companion object {
    fun toSelectable(o: Any): Selectable {
      return when (o) {
        is Selectable -> o
        is String -> Expression.field(o)
        is com.google.firebase.firestore.FieldPath -> Expression.field(o.toString())
        else -> throw IllegalArgumentException("Unknown Selectable type: $o")
      }
    }
  }
}
