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
package com.google.firebase.dataconnect.sqlite

/**
 * Helper class for building up SQLite statements with automatic numbering of the bindings.
 *
 * The class as a whole mimics the API surface of [StringBuilder] and, indeed, can almost be used as
 * a drop-in replacement for a [StringBuilder], especially if the statement doesn't have any "?"
 * placeholders for bindings.
 *
 * However, for statements that _do_ have bindings, this class can be very helpful because it looks
 * after the binding numbering and enables re-use of the binding numbers. It uses the "?1", "?2",
 * etc. syntax supported by SQLite to explicitly number the bindings. In order to add bindings use
 * the [appendBinding] methods.
 *
 * Instances of this class are NOT thread-safe; concurrent use of this class from multiple threads
 * and/or coroutines must be synchronized externally or else the behavior is undefined.
 *
 * This class intentionally does _not_ implement [hashCode] nor [equals] because, as a mutable
 * object, it is not appropriate for use as a key in a hash-based collection and equality amongst
 * various instances has no known use case.
 */
internal class SQLiteStatementBuilder : Appendable, CharSequence {

  private val sb = StringBuilder()
  private val bindings = mutableListOf<Any>()

  class Result(
    val sql: String,
    val bindings: Array<Any>,
  )

  fun build(): Result = Result(sql = sb.toString(), bindings = bindings.toTypedArray())

  inline fun build(block: SQLiteStatementBuilder.() -> Unit): Result = apply(block).build()

  private fun appendObjectBinding(value: Any): Int {
    bindings.add(value)
    val bindingIndex = bindings.size
    append('?')
    append(bindingIndex)
    return bindingIndex
  }

  fun appendBinding(value: ByteArray): SQLiteStatementBuilder = apply { appendObjectBinding(value) }

  fun appendBinding(value: CharSequence): SQLiteStatementBuilder = appendBinding(value)

  fun appendBinding(value: Int): SQLiteStatementBuilder = apply { appendObjectBinding(value) }

  fun appendBinding(value: Long): SQLiteStatementBuilder = apply { appendObjectBinding(value) }

  fun appendBinding(value: Float): SQLiteStatementBuilder = apply { appendObjectBinding(value) }

  fun appendBinding(value: Double): SQLiteStatementBuilder = apply { appendObjectBinding(value) }

  override fun append(csq: CharSequence?): SQLiteStatementBuilder = apply { sb.append(csq) }

  override fun append(csq: CharSequence?, start: Int, end: Int): SQLiteStatementBuilder = apply {
    sb.append(csq, start, end)
  }

  override fun append(c: Char): SQLiteStatementBuilder = apply { sb.append(c) }

  override val length: Int
    get() = sb.length

  override fun get(index: Int): Char = sb.get(index)

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
    sb.subSequence(startIndex, endIndex)

  fun append(value: Any?): SQLiteStatementBuilder = apply { sb.append(value) }

  fun append(value: String): SQLiteStatementBuilder = apply { sb.append(value) }

  fun append(value: StringBuffer): SQLiteStatementBuilder = apply { sb.append(value) }

  fun append(value: CharArray): SQLiteStatementBuilder = apply { sb.append(value) }

  fun append(value: CharArray, offset: Int, len: Int): SQLiteStatementBuilder = apply {
    sb.append(value, offset, len)
  }

  fun append(value: Boolean): SQLiteStatementBuilder = apply { sb.append(value) }

  fun append(value: Int): SQLiteStatementBuilder = apply { sb.append(value) }

  fun append(value: Long): SQLiteStatementBuilder = apply { sb.append(value) }

  fun append(value: Float): SQLiteStatementBuilder = apply { sb.append(value) }

  fun append(value: Double): SQLiteStatementBuilder = apply { sb.append(value) }

  fun appendCodePoint(codePoint: Int): SQLiteStatementBuilder = apply {
    sb.appendCodePoint(codePoint)
  }

  fun indexOf(str: String): Int = sb.indexOf(str)

  fun indexOf(str: String, fromIndex: Int): Int = sb.indexOf(str, fromIndex)

  fun lastIndexOf(str: String): Int = sb.lastIndexOf(str)

  fun lastIndexOf(str: String, fromIndex: Int): Int = sb.lastIndexOf(str, fromIndex)

  override fun toString(): String {
    return sb.toString()
  }

  companion object {

    inline fun build(block: SQLiteStatementBuilder.() -> Unit): Result =
      SQLiteStatementBuilder().build(block)
  }
}
