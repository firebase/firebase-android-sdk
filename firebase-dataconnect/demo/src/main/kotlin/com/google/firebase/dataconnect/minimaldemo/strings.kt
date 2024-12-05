/*
 * Copyright 2024 Google LLC
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
package com.google.firebase.dataconnect.minimaldemo

import android.annotation.SuppressLint
import com.google.firebase.Timestamp
import com.google.firebase.dataconnect.AnyValue
import com.google.firebase.dataconnect.LocalDate
import com.google.firebase.dataconnect.OptionalVariable
import com.google.firebase.dataconnect.minimaldemo.connector.GetItemByKeyQuery
import com.google.firebase.dataconnect.minimaldemo.connector.InsertItemMutation
import com.google.firebase.dataconnect.toJavaLocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun InsertItemMutation.Variables.toDisplayString(): String =
  displayStringForItem(
    string = string,
    int = int,
    int64 = int64,
    float = float,
    boolean = boolean,
    date = date,
    timestamp = timestamp,
    any = any,
  )

fun GetItemByKeyQuery.Data.Item.toDisplayString(): String =
  displayStringForItem(
    string = OptionalVariable.Value(string),
    int = OptionalVariable.Value(int),
    int64 = OptionalVariable.Value(int64),
    float = OptionalVariable.Value(float),
    boolean = OptionalVariable.Value(boolean),
    date = OptionalVariable.Value(date),
    timestamp = OptionalVariable.Value(timestamp),
    any = OptionalVariable.Value(any),
  )

fun displayStringForItem(
  string: OptionalVariable<String?>,
  int: OptionalVariable<Int?>,
  int64: OptionalVariable<Long?>,
  float: OptionalVariable<Double?>,
  boolean: OptionalVariable<Boolean?>,
  date: OptionalVariable<LocalDate?>,
  timestamp: OptionalVariable<Timestamp?>,
  any: OptionalVariable<AnyValue?>,
) = buildString {
  append("string=").append(string).appendLine()
  append("int=").append(int).appendLine()
  append("int64=").append(int64).appendLine()
  append("float=").append(float).appendLine()
  append("boolean=").append(boolean).appendLine()
  append("date=").append(date.toDisplayString { it.toDisplayString() }).appendLine()
  append("timestamp=").append(timestamp.toDisplayString { it.toDisplayString() }).appendLine()
  append("any=").append(any)
}

private fun <T : Any> OptionalVariable<T?>.toDisplayString(stringer: (T) -> String): String =
  when (this) {
    is OptionalVariable.Undefined -> toString()
    is OptionalVariable.Value -> value?.let(stringer) ?: "null"
  }

private fun LocalDate.toDisplayString(): String =
  toJavaLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))

@SuppressLint("NewApi")
private fun Timestamp.toDisplayString(): String =
  DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
    .withLocale(Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(toInstant())
