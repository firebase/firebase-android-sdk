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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.DataConnectPathComparator
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import io.kotest.assertions.print.Print
import io.kotest.assertions.print.Printed
import io.kotest.assertions.print.Printers
import io.kotest.assertions.print.printed
import java.util.Objects

@Suppress("SpellCheckingInspection")
fun registerDataConnectKotestPrinters() {
  Printers.add(Struct::class, StructCompactPrint)
  Printers.add(ListValue::class, ListValueCompactPrint)
  Printers.add(Value::class, ValueCompactPrint)
}

private object StructCompactPrint : Print<Struct> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: Struct): Printed = a.toCompactString().printed()
}

private object ListValueCompactPrint : Print<ListValue> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: ListValue): Printed = a.toCompactString().printed()
}

private object ValueCompactPrint : Print<Value> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: Value): Printed = a.toCompactString().printed()
}

fun <T> Map<DataConnectPath, T>.toPrintFriendlyMap(): Map<PrintFriendlyDataConnectPath, T> =
  toSortedMap(DataConnectPathComparator).entries.associateTo(LinkedHashMap()) {
    PrintFriendlyDataConnectPath(it.key) to it.value
  }

class PrintFriendlyDataConnectPath(val path: DataConnectPath) :
  Comparable<PrintFriendlyDataConnectPath> {

  override fun toString() = path.toPathString()

  override fun equals(other: Any?) = other is PrintFriendlyDataConnectPath && other.path == path

  override fun hashCode() = Objects.hash(PrintFriendlyDataConnectPath::class, path)

  override fun compareTo(other: PrintFriendlyDataConnectPath): Int =
    DataConnectPathComparator.compare(path, other.path)
}
