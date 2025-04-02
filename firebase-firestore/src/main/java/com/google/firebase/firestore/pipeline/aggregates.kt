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
internal constructor(internal val alias: String, internal val expr: AggregateExpr)

class AggregateExpr
private constructor(private val name: String, private val params: Array<out Expr>) {
  private constructor(name: String) : this(name, emptyArray())
  private constructor(name: String, expr: Expr) : this(name, arrayOf(expr))
  private constructor(name: String, fieldName: String) : this(name, Field.of(fieldName))

  companion object {
    @JvmStatic fun countAll() = AggregateExpr("count")

    @JvmStatic fun count(fieldName: String) = AggregateExpr("count", fieldName)

    @JvmStatic fun count(expr: Expr) = AggregateExpr("count", expr)

    @JvmStatic fun countIf(condition: BooleanExpr) = AggregateExpr("countIf", condition)

    @JvmStatic fun sum(fieldName: String) = AggregateExpr("sum", fieldName)

    @JvmStatic fun sum(expr: Expr) = AggregateExpr("sum", expr)

    @JvmStatic fun avg(fieldName: String) = AggregateExpr("avg", fieldName)

    @JvmStatic fun avg(expr: Expr) = AggregateExpr("avg", expr)

    @JvmStatic fun min(fieldName: String) = AggregateExpr("min", fieldName)

    @JvmStatic fun min(expr: Expr) = AggregateExpr("min", expr)

    @JvmStatic fun max(fieldName: String) = AggregateExpr("max", fieldName)

    @JvmStatic fun max(expr: Expr) = AggregateExpr("max", expr)
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
