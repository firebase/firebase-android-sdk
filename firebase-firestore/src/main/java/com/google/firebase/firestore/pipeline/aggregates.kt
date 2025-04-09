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

import com.google.firebase.firestore.UserDataReader
import com.google.firestore.v1.Value

class AggregateWithAlias
internal constructor(internal val alias: String, internal val expr: AggregateFunction)

class AggregateFunction
private constructor(private val name: String, private val params: Array<out Expr>) {
  private constructor(name: String) : this(name, emptyArray())
  private constructor(name: String, expr: Expr) : this(name, arrayOf(expr))
  private constructor(name: String, fieldName: String) : this(name, Field.of(fieldName))

  companion object {

    @JvmStatic fun generic(name: String, vararg expr: Expr) = AggregateFunction(name, expr)

    @JvmStatic fun countAll() = AggregateFunction("count")

    @JvmStatic fun count(fieldName: String) = AggregateFunction("count", fieldName)

    @JvmStatic fun count(expr: Expr) = AggregateFunction("count", expr)

    @JvmStatic fun countIf(condition: BooleanExpr) = AggregateFunction("countIf", condition)

    @JvmStatic fun sum(fieldName: String) = AggregateFunction("sum", fieldName)

    @JvmStatic fun sum(expr: Expr) = AggregateFunction("sum", expr)

    @JvmStatic fun avg(fieldName: String) = AggregateFunction("avg", fieldName)

    @JvmStatic fun avg(expr: Expr) = AggregateFunction("avg", expr)

    @JvmStatic fun min(fieldName: String) = AggregateFunction("min", fieldName)

    @JvmStatic fun min(expr: Expr) = AggregateFunction("min", expr)

    @JvmStatic fun max(fieldName: String) = AggregateFunction("max", fieldName)

    @JvmStatic fun max(expr: Expr) = AggregateFunction("max", expr)
  }

  fun alias(alias: String) = AggregateWithAlias(alias, this)

  internal fun toProto(userDataReader: UserDataReader): Value {
    val builder = com.google.firestore.v1.Function.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto(userDataReader))
    }
    return Value.newBuilder().setFunctionValue(builder).build()
  }
}
