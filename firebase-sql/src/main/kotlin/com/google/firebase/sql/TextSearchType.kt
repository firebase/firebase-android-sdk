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
 * How `textSearch()` turns its query into a PostgreSQL `tsquery`.
 *
 * Each type uses a PostgreSQL function to parse the query, and a PostgREST operator.
 *
 * **Example: search like a web search engine**
 * ```kotlin
 * val articles =
 *   sql
 *     .from("articles")
 *     .select("id, title")
 *     .textSearch("fts", "fruit -banana", TextSearchType.WEBSEARCH, config = "english")
 *     .execute()
 * ```
 */
public class TextSearchType private constructor(internal val rawValue: String) {
  /** Returns the PostgREST operator, for example `wfts`. */
  override fun toString(): String = rawValue

  public companion object {
    /**
     * The query is in the `tsquery` syntax, for example `fruit & !banana`: `to_tsquery`, and the
     * PostgREST operator `fts`.
     */
    public val NONE: TextSearchType = TextSearchType("fts")

    /**
     * The query is plain text, whose words must all match: `plainto_tsquery`, and the PostgREST
     * operator `plfts`.
     */
    public val PLAINTO: TextSearchType = TextSearchType("plfts")

    /**
     * The query is plain text, whose words must match next to each other and in this order:
     * `phraseto_tsquery`, and the PostgREST operator `phfts`.
     */
    public val PHRASETO: TextSearchType = TextSearchType("phfts")

    /**
     * The query uses the syntax of web search engines: quoted phrases, `or`, and `-` to exclude a
     * word, for example `"red apple" or kiwi -banana`: `websearch_to_tsquery`, and the PostgREST
     * operator `wfts`.
     */
    public val WEBSEARCH: TextSearchType = TextSearchType("wfts")
  }
}
