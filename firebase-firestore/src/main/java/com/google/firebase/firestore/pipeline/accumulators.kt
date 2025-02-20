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

import com.google.firestore.v1.Value

class AccumulatorWithAlias
internal constructor(internal val alias: String, internal val accumulator: Accumulator)

class Accumulator
private constructor(private val name: String, private val params: Array<out Expr>) {
  private constructor(name: String) : this(name, emptyArray())
  private constructor(name: String, expr: Expr) : this(name, arrayOf(expr))
  private constructor(name: String, fieldName: String) : this(name, Field.of(fieldName))

  companion object {
    @JvmStatic fun countAll() = Accumulator("count")

    @JvmStatic fun count(fieldName: String) = Accumulator("count", fieldName)

    @JvmStatic fun count(expr: Expr) = Accumulator("count", expr)

    @JvmStatic fun countIf(condition: BooleanExpr) = Accumulator("countIf", condition)

    @JvmStatic fun sum(fieldName: String) = Accumulator("sum", fieldName)

    @JvmStatic fun sum(expr: Expr) = Accumulator("sum", expr)

    @JvmStatic fun avg(fieldName: String) = Accumulator("avg", fieldName)

    @JvmStatic fun avg(expr: Expr) = Accumulator("avg", expr)

    @JvmStatic fun min(fieldName: String) = Accumulator("min", fieldName)

    @JvmStatic fun min(expr: Expr) = Accumulator("min", expr)

    @JvmStatic fun max(fieldName: String) = Accumulator("max", fieldName)

    @JvmStatic fun max(expr: Expr) = Accumulator("max", expr)
  }

  fun `as`(alias: String) = AccumulatorWithAlias(alias, this)

  fun toProto(): Value {
    val builder = com.google.firestore.v1.Function.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto())
    }
    return Value.newBuilder().setFunctionValue(builder).build()
  }
}
