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
 * The PostgREST operators of `filter()` and `filterNot()`.
 *
 * Each constant is the PostgREST operator with the same name in lowercase, for example [EQ] is
 * `eq`. The constants that end with `_ANY` or `_ALL` compare with each element of a list: [EQ_ANY]
 * is `eq(any)`, and [GT_ALL] is `gt(all)`. The filter methods, such as `eq()` and `isIn()`, are
 * easier to read; use these constants for an operator that has no dedicated method, or that is
 * chosen at run time.
 *
 * **Empty lists:** As in PostgreSQL, an empty list matches every row with an `_ALL` constant, even
 * the rows where the column is `NULL`, and no rows with an `_ANY` constant; `filterNot()` gives the
 * opposite. So in an `update()` or a `delete()`, an empty list can change every row of the table.
 * [maxAffected][com.google.firebase.sql.builders.TransformBuilder.maxAffected] makes the server
 * reject a change of more rows than expected.
 *
 * **Example: choose the operator at run time**
 * ```kotlin
 * val includeTwo = true
 * val operator = if (includeTwo) FilterOperator.LTE else FilterOperator.LT
 * val products =
 *   sql.from("products").select().filter("price", operator, 2).execute().decodeList<Product>()
 * ```
 *
 * **Example: names that match one of several regular expressions**
 * ```kotlin
 * // PostgREST: name=match(any).{^Kiwi,^Mango}
 * val products =
 *   sql
 *     .from("products")
 *     .select()
 *     .filter("name", FilterOperator.MATCH_ANY, listOf("^Kiwi", "^Mango"))
 *     .execute()
 *     .decodeList<Product>()
 * ```
 */
public class FilterOperator private constructor(internal val rawValue: String) {
  /** Returns the PostgREST operator, for example `gt(all)`. */
  override fun toString(): String = rawValue

  public companion object {
    /** Equal to: `eq`. */
    public val EQ: FilterOperator = FilterOperator("eq")

    /** Not equal to: `neq`. */
    public val NEQ: FilterOperator = FilterOperator("neq")

    /** Greater than: `gt`. */
    public val GT: FilterOperator = FilterOperator("gt")

    /** Greater than or equal to: `gte`. */
    public val GTE: FilterOperator = FilterOperator("gte")

    /** Less than: `lt`. */
    public val LT: FilterOperator = FilterOperator("lt")

    /** Less than or equal to: `lte`. */
    public val LTE: FilterOperator = FilterOperator("lte")

    /** Matches a `LIKE` pattern, which is case-sensitive: `like`. */
    public val LIKE: FilterOperator = FilterOperator("like")

    /** Matches a POSIX regular expression, which is case-sensitive: `match`. */
    public val MATCH: FilterOperator = FilterOperator("match")

    /** Matches a `LIKE` pattern, ignoring case: `ilike`. */
    public val ILIKE: FilterOperator = FilterOperator("ilike")

    /** Matches a POSIX regular expression, ignoring case: `imatch`. */
    public val IMATCH: FilterOperator = FilterOperator("imatch")

    /** Is `NULL`, `true`, or `false`: `is`. */
    public val IS: FilterOperator = FilterOperator("is")

    /** Is one of the values of a list: `in`. */
    public val IN: FilterOperator = FilterOperator("in")

    /** Contains every element of an array or a range, or the keys of a JSON object: `cs`. */
    public val CS: FilterOperator = FilterOperator("cs")

    /** Is contained in an array or a range: `cd`. */
    public val CD: FilterOperator = FilterOperator("cd")

    /** Is a range strictly left of another range: `sl`. */
    public val SL: FilterOperator = FilterOperator("sl")

    /** Is a range strictly right of another range: `sr`. */
    public val SR: FilterOperator = FilterOperator("sr")

    /** Is a range that does not extend to the left of another range: `nxl`. */
    public val NXL: FilterOperator = FilterOperator("nxl")

    /** Is a range that does not extend to the right of another range: `nxr`. */
    public val NXR: FilterOperator = FilterOperator("nxr")

    /** Is a range adjacent to another range: `adj`. */
    public val ADJ: FilterOperator = FilterOperator("adj")

    /** Has an element or a value in common with an array or a range: `ov`. */
    public val OV: FilterOperator = FilterOperator("ov")

    /** Matches a full-text query in the `tsquery` syntax: `fts`. */
    public val FTS: FilterOperator = FilterOperator("fts")

    /** Matches a full-text query in plain text: `plfts`. */
    public val PLFTS: FilterOperator = FilterOperator("plfts")

    /** Matches a full-text query as a phrase: `phfts`. */
    public val PHFTS: FilterOperator = FilterOperator("phfts")

    /** Matches a full-text query in the syntax of web search engines: `wfts`. */
    public val WFTS: FilterOperator = FilterOperator("wfts")

    /** Is distinct from a value, where `NULL` is a value like any other: `isdistinct`. */
    public val ISDISTINCT: FilterOperator = FilterOperator("isdistinct")

    // The quantified operators: the value is a list, sent as a PostgreSQL array literal.

    /** Equal to at least one element of a list: `eq(any)`. */
    public val EQ_ANY: FilterOperator = FilterOperator("eq(any)")

    /** Equal to every element of a list: `eq(all)`. */
    public val EQ_ALL: FilterOperator = FilterOperator("eq(all)")

    /** Greater than at least one element of a list: `gt(any)`. */
    public val GT_ANY: FilterOperator = FilterOperator("gt(any)")

    /** Greater than every element of a list: `gt(all)`. */
    public val GT_ALL: FilterOperator = FilterOperator("gt(all)")

    /** Greater than or equal to at least one element of a list: `gte(any)`. */
    public val GTE_ANY: FilterOperator = FilterOperator("gte(any)")

    /** Greater than or equal to every element of a list: `gte(all)`. */
    public val GTE_ALL: FilterOperator = FilterOperator("gte(all)")

    /** Less than at least one element of a list: `lt(any)`. */
    public val LT_ANY: FilterOperator = FilterOperator("lt(any)")

    /** Less than every element of a list: `lt(all)`. */
    public val LT_ALL: FilterOperator = FilterOperator("lt(all)")

    /** Less than or equal to at least one element of a list: `lte(any)`. */
    public val LTE_ANY: FilterOperator = FilterOperator("lte(any)")

    /** Less than or equal to every element of a list: `lte(all)`. */
    public val LTE_ALL: FilterOperator = FilterOperator("lte(all)")

    /** Matches at least one `LIKE` pattern of a list, which is case-sensitive: `like(any)`. */
    public val LIKE_ANY: FilterOperator = FilterOperator("like(any)")

    /** Matches every `LIKE` pattern of a list, which is case-sensitive: `like(all)`. */
    public val LIKE_ALL: FilterOperator = FilterOperator("like(all)")

    /** Matches at least one `LIKE` pattern of a list, ignoring case: `ilike(any)`. */
    public val ILIKE_ANY: FilterOperator = FilterOperator("ilike(any)")

    /** Matches every `LIKE` pattern of a list, ignoring case: `ilike(all)`. */
    public val ILIKE_ALL: FilterOperator = FilterOperator("ilike(all)")

    /** Matches at least one POSIX regular expression of a list, case-sensitive: `match(any)`. */
    public val MATCH_ANY: FilterOperator = FilterOperator("match(any)")

    /** Matches every POSIX regular expression of a list, case-sensitive: `match(all)`. */
    public val MATCH_ALL: FilterOperator = FilterOperator("match(all)")

    /** Matches at least one POSIX regular expression of a list, ignoring case: `imatch(any)`. */
    public val IMATCH_ANY: FilterOperator = FilterOperator("imatch(any)")

    /** Matches every POSIX regular expression of a list, ignoring case: `imatch(all)`. */
    public val IMATCH_ALL: FilterOperator = FilterOperator("imatch(all)")
  }
}
