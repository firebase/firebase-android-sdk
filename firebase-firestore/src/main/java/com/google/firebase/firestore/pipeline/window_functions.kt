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
import com.google.firestore.v1.Function as ProtoFunction
import com.google.firestore.v1.Value

class AliasedWindowFunction
internal constructor(internal val alias: String, internal val expr: WindowFunction)

/** A class that represents a window function. */
class WindowFunction
private constructor(
  private val name: String,
  private val params: Array<out Expression> = emptyArray()
) {
  companion object {
    /**
     * Creates a window function that assigns a unique rank to each row based on the sort order.
     */
    @JvmStatic fun rank() = WindowFunction("rank")

    /**
     * Creates a window function that assigns a dense rank to each row based on the sort order.
     */
    @JvmStatic fun denseRank() = WindowFunction("dense_rank")

    /**
     * Creates a window function that assigns the row number to each row based on the sort order.
     */
    @JvmStatic fun rowNumber() = WindowFunction("row_number")
  }

  fun alias(alias: String) = AliasedWindowFunction(alias, this)

  internal fun toProto(userDataReader: UserDataReader): Value {
    val builder = ProtoFunction.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto(userDataReader))
    }
    return Value.newBuilder().setFunctionValue(builder).build()
  }
}
