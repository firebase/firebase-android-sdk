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

import android.database.Cursor
import android.util.Base64
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.asSample
import kotlin.random.nextInt

val Arb.Companion.sqlite: SQLiteArbs
  get() = SQLiteArbs

object SQLiteArbs {

  fun columnValue(
    nullColumnValue: Arb<NullColumnValue> = nullColumnValue(),
    intColumnValue: Arb<IntColumnValue> = intColumnValue(),
    longColumnValue: Arb<LongColumnValue> = longColumnValue(),
    floatColumnValue: Arb<FloatColumnValue> = floatColumnValue(),
    doubleColumnValue: Arb<DoubleColumnValue> = doubleColumnValue(),
    stringColumnValue: Arb<StringColumnValue> = stringColumnValue(),
    byteArrayColumnValue: Arb<ByteArrayColumnValue> = byteArrayColumnValue(),
  ): Arb<ColumnValue<*>> =
    Arb.choice(
      nullColumnValue,
      intColumnValue,
      longColumnValue,
      floatColumnValue,
      doubleColumnValue,
      stringColumnValue,
      byteArrayColumnValue,
    )

  fun nullColumnValue(): Arb<NullColumnValue> = Arb.constant(NullColumnValue)

  fun intColumnValue(int: Arb<Int> = Arb.int()): Arb<IntColumnValue> =
    int.map { IntColumnValue(it) }

  fun longColumnValue(long: Arb<Long> = Arb.long()): Arb<LongColumnValue> =
    long.map { LongColumnValue(it) }

  fun floatColumnValue(float: Arb<Float> = Arb.float()): Arb<FloatColumnValue> =
    float.map { FloatColumnValue(it) }

  fun doubleColumnValue(double: Arb<Double> = Arb.double()): Arb<DoubleColumnValue> =
    double.map { DoubleColumnValue(it) }

  fun stringColumnValue(
    string: Arb<String> = Arb.dataConnect.string(),
    containsApostropheProbability: Float = 0.5f
  ): Arb<StringColumnValue> = StringColumnValueArb(string, containsApostropheProbability)

  fun byteArrayColumnValue(
    byteArray: Arb<ByteArray> = Arb.byteArray(Arb.int(0..40), Arb.byte()),
  ): Arb<ByteArrayColumnValue> =
    byteArray.map {
      val itPrefix = it.take(20)
      val itPrefixBase64 = Base64.encodeToString(itPrefix.toByteArray(), Base64.NO_WRAP)
      val suffix = if (itPrefix.size < it.size) "..." else ""
      ByteArrayColumnValue(
        bindArgsValue = it,
        loggedValue = "byte[${it.size}]=" + itPrefixBase64 + suffix,
      )
    }

  /**
   * Stores information about a Kotlin value that can be used as a value of a sqlite column by
   * specifying it as an element of the "bindArgs" parameter to methods like execSQL() and
   * rawQuery().
   */
  sealed class ColumnValue<T>(
    /** The Kotlin value specified in the bindArgs array given to execSQL() or rawQuery() */
    val bindArgsValue: T,

    /**
     * A string that is used in logging statements when [bindArgsValue] is used in a query.
     *
     * This is normally the same as [toString] for [bindArgsValue], but can be different, such as
     * strings will have apostrophe ("'") characters escaped by turning them into a double-
     * apostrophe ("''").
     */
    val loggedValue: String,

    /**
     * The value that will be returned from the getXXX() methods of [Cursor] when reading the value
     * of a column that was set to [bindArgsValue].
     *
     * This is normally the same as [bindArgsValue], but not always. For example, storing `-0.0`
     * into a sqlite column and reading it back using [Cursor.getFloat] will return `0.0` and,
     * therefore, if [bindArgsValue] is `-0.0` then [readBackValue] will be `0.0`.
     */
    val readBackValue: T,

    /**
     * The same as [readBackValue] but will be `null` if [Cursor.isNull] would return `true` for a
     * column whose value was set to [bindArgsValue].
     *
     * For example, storing [Double.NaN] into a sqlite column and reading it back using
     * [Cursor.isNull] will return true and, therefore, if [Double.isNaN] returns `true` when given
     * [bindArgsValue] then [readBackNullableValue] will be `null`.
     */
    val readBackNullableValue: T?,

    /** The type to use in a CREATE TABLE sqlite statement for columns that store this data type. */
    val sqliteColumnType: String,

    /** A function to call to get the value of this column from a [Cursor]. */
    val getValueFromCursor: Cursor.(columnIndex: Int) -> T,
  )

  /**
   * An implementation of [ColumnValue] where the [bindArgsValue] is exactly the same as the
   * [readBackValue] and [readBackNullableValue] and [loggedValue] is the return value of [toString]
   * when called on [bindArgsValue].
   */
  sealed class IdentityColumnValue<T>(
    bindArgsValue: T,
    sqliteColumnType: String,
    getValueFromCursor: Cursor.(columnIndex: Int) -> T,
  ) :
    ColumnValue<T>(
      bindArgsValue = bindArgsValue,
      loggedValue = bindArgsValue.toString(),
      readBackValue = bindArgsValue,
      readBackNullableValue = bindArgsValue,
      sqliteColumnType = sqliteColumnType,
      getValueFromCursor = getValueFromCursor,
    )

  object NullColumnValue : IdentityColumnValue<String?>(null, "BLOB", { getString(it) })

  class IntColumnValue(bindArgsValue: Int) :
    IdentityColumnValue<Int>(bindArgsValue, "INT", { getInt(it) })

  class LongColumnValue(bindArgsValue: Long) :
    IdentityColumnValue<Long>(bindArgsValue, "INT", { getLong(it) })

  class FloatColumnValue(bindArgsValue: Float) :
    ColumnValue<Float>(
      bindArgsValue = bindArgsValue,
      loggedValue = bindArgsValue.toString(),
      readBackValue = readBackValueForBindArgsValue(bindArgsValue),
      readBackNullableValue = readBackNullableValueForBindArgsValue(bindArgsValue),
      sqliteColumnType = "REAL",
      getValueFromCursor = { getFloat(it) },
    ) {
    companion object {
      fun readBackValueForBindArgsValue(bindArgsValue: Float): Float =
        if (bindArgsValue.isNaN() || bindArgsValue == -0.0f) {
          0.0f
        } else {
          bindArgsValue
        }

      fun readBackNullableValueForBindArgsValue(bindArgsValue: Float): Float? =
        if (bindArgsValue.isNaN()) {
          null
        } else if (bindArgsValue == -0.0f) {
          0.0f
        } else {
          bindArgsValue
        }
    }
  }

  class DoubleColumnValue(bindArgsValue: Double) :
    ColumnValue<Double>(
      bindArgsValue = bindArgsValue,
      loggedValue = bindArgsValue.toString(),
      readBackValue = readBackValueForBindArgsValue(bindArgsValue),
      readBackNullableValue = readBackNullableValueForBindArgsValue(bindArgsValue),
      sqliteColumnType = "REAL",
      getValueFromCursor = { getDouble(it) },
    ) {
    companion object {
      fun readBackValueForBindArgsValue(bindArgsValue: Double): Double =
        if (bindArgsValue.isNaN() || bindArgsValue == -0.0) {
          0.0
        } else {
          bindArgsValue
        }

      fun readBackNullableValueForBindArgsValue(bindArgsValue: Double): Double? =
        if (bindArgsValue.isNaN()) {
          null
        } else if (bindArgsValue == -0.0) {
          0.0
        } else {
          bindArgsValue
        }
    }
  }

  class StringColumnValue(
    bindArgsValue: String,
    loggedValue: String,
  ) :
    ColumnValue<String>(
      bindArgsValue = bindArgsValue,
      loggedValue = loggedValue,
      readBackValue = bindArgsValue,
      readBackNullableValue = bindArgsValue,
      sqliteColumnType = "TEXT",
      getValueFromCursor = { getString(it) },
    ) {
    companion object {
      fun fromValueAndEscapedValue(
        bindArgsValue: String,
        bindArgsValueEscaped: String,
      ): StringColumnValue =
        StringColumnValue(
          bindArgsValue = bindArgsValue,
          loggedValue = "'$bindArgsValueEscaped'",
        )
    }
  }

  class ByteArrayColumnValue(
    bindArgsValue: ByteArray,
    loggedValue: String,
  ) :
    ColumnValue<ByteArray>(
      bindArgsValue = bindArgsValue,
      loggedValue = loggedValue,
      readBackValue = bindArgsValue,
      readBackNullableValue = bindArgsValue,
      sqliteColumnType = "BLOB",
      getValueFromCursor = { getBlob(it) },
    )

  /**
   * The same as [String.drop] except that if the drop would break up a surrogate pair, causing a
   * lone surrogate, then one fewer characters than requested will be dropped.
   *
   * This function is _not_ safe if the receiving object contains lone surrogates and the behavior
   * of this function is undefined if that is the case.
   */
  private fun String.surrogateSafeDrop(n: Int): String =
    drop(if (n < length && get(n).isLowSurrogate()) n - 1 else n)

  private class StringColumnValueArb(
    private val string: Arb<String>,
    private val containsApostropheProbability: Float,
  ) : Arb<StringColumnValue>() {

    init {
      require(!containsApostropheProbability.isNaN()) {
        "containsApostropheProbability cannot be NaN"
      }
      require(containsApostropheProbability >= 0.0f) {
        "containsApostropheProbability must be at least 0.0, but got: $containsApostropheProbability"
      }
      require(containsApostropheProbability <= 1.0f) {
        "containsApostropheProbability must be at most 1.0, but got: $containsApostropheProbability"
      }
    }

    private val stringWithoutApostrophes =
      string.map { if (it.contains('\'')) it.replace("'", "") else it }
    private val nonEmptyStringWithoutApostrophes =
      stringWithoutApostrophes.filter { it.isNotEmpty() }

    override fun sample(rs: RandomSource): Sample<StringColumnValue> = normalCase(rs).asSample()

    private fun normalCase(rs: RandomSource): StringColumnValue {
      val originalString = stringWithoutApostrophes.next(rs)

      if (
        originalString.isEmpty() ||
          containsApostropheProbability == 0.0f ||
          rs.random.nextFloat() > containsApostropheProbability
      ) {
        return StringColumnValue.fromValueAndEscapedValue(originalString, originalString)
      }

      val apostropheCount = rs.random.nextInt(1..originalString.length)
      val bindArgsValue = StringBuilder(originalString)
      val loggedValue = StringBuilder(originalString)
      val indices = originalString.indices.shuffled(rs.random).take(apostropheCount)
      indices.sortedDescending().forEach { index ->
        // Avoid splitting up a surrogate pair because that yields an invalid sequence of
        // UTF-16 code units and is undefined how sqlite would persist such a value.
        val insertIndex =
          if (index > 0 && bindArgsValue[index].isLowSurrogate()) {
            index - 1
          } else {
            index
          }
        bindArgsValue.insert(insertIndex, "'")
        loggedValue.insert(insertIndex, "''")
      }

      return StringColumnValue.fromValueAndEscapedValue(
        bindArgsValue = bindArgsValue.toString(),
        bindArgsValueEscaped = loggedValue.toString(),
      )
    }

    override fun edgecase(rs: RandomSource): StringColumnValue =
      when (EdgeCase.entries.random(rs.random)) {
        EdgeCase.EmptyString ->
          StringColumnValue.fromValueAndEscapedValue(
            bindArgsValue = "",
            bindArgsValueEscaped = "",
          )
        EdgeCase.OnlyApostrophes ->
          string.next(rs).let {
            StringColumnValue.fromValueAndEscapedValue(
              bindArgsValue = "'".repeat(it.length),
              bindArgsValueEscaped = "''".repeat(it.length),
            )
          }
        EdgeCase.BeginsWithApostrophes ->
          nonEmptyStringWithoutApostrophes.next(rs).let {
            val leadingApostropheCount = rs.random.nextInt(1..it.length)
            StringColumnValue.fromValueAndEscapedValue(
              bindArgsValue =
                "'".repeat(leadingApostropheCount) + it.surrogateSafeDrop(leadingApostropheCount),
              bindArgsValueEscaped =
                "''".repeat(leadingApostropheCount) + it.surrogateSafeDrop(leadingApostropheCount),
            )
          }
        EdgeCase.EndsWithApostrophes ->
          nonEmptyStringWithoutApostrophes.next(rs).let {
            val trailingApostropheCount = rs.random.nextInt(1..it.length)
            StringColumnValue.fromValueAndEscapedValue(
              bindArgsValue =
                it.surrogateSafeDrop(trailingApostropheCount) + "'".repeat(trailingApostropheCount),
              bindArgsValueEscaped =
                it.surrogateSafeDrop(trailingApostropheCount) +
                  "''".repeat(trailingApostropheCount),
            )
          }
        EdgeCase.BeginsAndEndsWithApostrophes ->
          nonEmptyStringWithoutApostrophes.next(rs).let {
            if (it.length <= 2) {
              StringColumnValue.fromValueAndEscapedValue(
                bindArgsValue = "'".repeat(it.length),
                bindArgsValueEscaped = "''".repeat(it.length),
              )
            } else {
              val leadingApostropheCount = rs.random.nextInt(1..it.length - 2)
              val trailingApostropheCount =
                rs.random.nextInt(1..it.length - 1 - leadingApostropheCount)
              StringColumnValue.fromValueAndEscapedValue(
                bindArgsValue =
                  "'".repeat(leadingApostropheCount) +
                    it.surrogateSafeDrop(leadingApostropheCount + trailingApostropheCount) +
                    "'".repeat(trailingApostropheCount),
                bindArgsValueEscaped =
                  "''".repeat(leadingApostropheCount) +
                    it.surrogateSafeDrop(leadingApostropheCount + trailingApostropheCount) +
                    "''".repeat(trailingApostropheCount),
              )
            }
          }
      }

    private enum class EdgeCase {
      EmptyString,
      OnlyApostrophes,
      BeginsWithApostrophes,
      EndsWithApostrophes,
      BeginsAndEndsWithApostrophes,
    }
  }
}
