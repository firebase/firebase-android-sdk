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

open class Accumulator
protected constructor(private val name: String, private val params: Array<out Expr>) {
  protected constructor(
    name: String,
    param: Expr?
  ) : this(name, if (param == null) emptyArray() else arrayOf(param))

  companion object {
    @JvmStatic
    fun countAll(): Count {
      return Count(null)
    }

    @JvmStatic
    fun count(fieldName: String): Count {
      return Count(fieldName)
    }

    @JvmStatic
    fun count(expr: Expr): Count {
      return Count(expr)
    }

    @JvmStatic
    fun sum(fieldName: String): Accumulator {
      return Sum(fieldName)
    }

    @JvmStatic
    fun sum(expr: Expr): Accumulator {
      return Sum(expr)
    }

    @JvmStatic
    fun avg(fieldName: String): Accumulator {
      return Avg(fieldName)
    }

    @JvmStatic
    fun avg(expr: Expr): Accumulator {
      return Avg(expr)
    }

    @JvmStatic
    fun min(fieldName: String): Accumulator {
      return min(fieldName)
    }

    @JvmStatic
    fun min(expr: Expr): Accumulator {
      return min(expr)
    }

    @JvmStatic
    fun max(fieldName: String): Accumulator {
      return Max(fieldName)
    }

    @JvmStatic
    fun max(expr: Expr): Accumulator {
      return Max(expr)
    }
  }

  fun `as`(alias: String): AccumulatorWithAlias {
    return AccumulatorWithAlias(alias, this)
  }

  fun toProto(): Value {
    val builder = com.google.firestore.v1.Function.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto())
    }
    return Value.newBuilder().setFunctionValue(builder).build()
  }
}

class Count(value: Expr?) : Accumulator("count", value) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class Sum(value: Expr) : Accumulator("sum", value) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class Avg(value: Expr) : Accumulator("avg", value) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class Min(value: Expr) : Accumulator("min", value) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class Max(value: Expr) : Accumulator("max", value) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}
