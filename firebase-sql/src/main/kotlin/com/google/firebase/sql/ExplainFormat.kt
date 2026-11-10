/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.sql

/**
 * The format of the PostgreSQL execution plan that `explain()` returns.
 *
 * Later versions can add formats, so give a `when` expression over the format an `else` branch.
 *
 * **Example: get the plan as JSON**
 * ```kotlin
 * val plan =
 *   sql
 *     .from("products")
 *     .select()
 *     .eq("category_id", 1)
 *     .explain(analyze = true, format = ExplainFormat.JSON)
 *     .execute()
 *     .data
 * ```
 */
public class ExplainFormat private constructor(internal val rawValue: String) {
  /** Returns the format as PostgREST names it in the media type of the plan, for example `json`. */
  override fun toString(): String = rawValue

  public companion object {
    /**
     * The plan as text, as PostgreSQL's `EXPLAIN` prints it: the media type
     * `application/vnd.pgrst.plan+text`.
     */
    public val TEXT: ExplainFormat = ExplainFormat("text")

    /** The plan as a JSON array: the media type `application/vnd.pgrst.plan+json`. */
    public val JSON: ExplainFormat = ExplainFormat("json")
  }
}
