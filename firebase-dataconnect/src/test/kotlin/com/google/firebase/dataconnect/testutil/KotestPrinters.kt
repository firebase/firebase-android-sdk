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
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.sqlite.DehydratedQueryResult
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toListValueProto
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.kotlinsdk.Entity as EntityProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityList as EntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityOrEntityList as EntityOrEntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityPath as EntityPathProto
import google.firebase.dataconnect.proto.kotlinsdk.FieldOrListIndex as FieldOrListIndexProto
import google.firebase.dataconnect.proto.kotlinsdk.QueryResult as QueryResultProto
import io.kotest.assertions.print.Print
import io.kotest.assertions.print.Printed
import io.kotest.assertions.print.Printers
import io.kotest.assertions.print.print
import io.kotest.assertions.print.printed
import java.util.Objects

@Suppress("SpellCheckingInspection")
fun registerDataConnectKotestPrinters() {
  Printers.add(Struct::class, StructCompactPrint)
  Printers.add(ListValue::class, ListValueCompactPrint)
  Printers.add(Value::class, ValueCompactPrint)
  Printers.add(QueryResultProto::class, QueryResultProtoPrint)
  Printers.add(EntityOrEntityListProto::class, EntityOrEntityListProtoPrint)
  Printers.add(EntityProto::class, EntityProtoPrint)
  Printers.add(EntityListProto::class, EntityListProtoPrint)
  Printers.add(EntityPathProto::class, EntityPathProtoPrint)
  Printers.add(DehydratedQueryResult::class, DehydratedQueryResultPrint)
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

private object QueryResultProtoPrint : Print<QueryResultProto> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: QueryResultProto): Printed = toStruct(a).toCompactString().printed()

  fun toStruct(proto: QueryResultProto): Struct {
    val builder = Struct.newBuilder()
    builder.putFields("struct", proto.struct.toValueProto())
    builder.putFields("entities", toListValue(proto.entitiesList).toValueProto())
    return builder.build()
  }

  fun toListValue(protos: Iterable<EntityOrEntityListProto>): ListValue {
    val builder = ListValue.newBuilder()
    protos.map(EntityOrEntityListProtoPrint::toStruct).forEach {
      builder.addValues(it.toValueProto())
    }
    return builder.build()
  }
}

private object EntityOrEntityListProtoPrint : Print<EntityOrEntityListProto> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: EntityOrEntityListProto): Printed = toStruct(a).toCompactString().printed()

  fun toStruct(proto: EntityOrEntityListProto): Struct {
    val builder = Struct.newBuilder()

    builder.putFields("path", EntityPathProtoPrint.toValueProto(proto.path))

    when (proto.kindCase) {
      EntityOrEntityListProto.KindCase.ENTITY -> {
        val struct = EntityProtoPrint.toStruct(proto.entity)
        builder.putFields("entity", struct.toValueProto())
      }
      EntityOrEntityListProto.KindCase.ENTITYLIST -> {
        val listValue = EntityListProtoPrint.toListValue(proto.entityList)
        builder.putFields("entities", listValue.toValueProto())
      }
      EntityOrEntityListProto.KindCase.KIND_NOT_SET -> {}
    }

    return builder.build()
  }
}

private object EntityPathProtoPrint : Print<EntityPathProto> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: EntityPathProto): Printed = toValueProto(a).toCompactString().printed()

  fun toValueProto(proto: EntityPathProto): Value {
    val path: DataConnectPath =
      proto.segmentsList.mapNotNull {
        when (it.kindCase) {
          FieldOrListIndexProto.KindCase.FIELD -> DataConnectPathSegment.Field(it.field)
          FieldOrListIndexProto.KindCase.LIST_INDEX ->
            DataConnectPathSegment.ListIndex(it.listIndex)
          FieldOrListIndexProto.KindCase.KIND_NOT_SET -> null
        }
      }
    return path.toPathString().toValueProto()
  }
}

private object EntityProtoPrint : Print<EntityProto> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: EntityProto): Printed = toStruct(a).toCompactString().printed()

  fun toStruct(proto: EntityProto): Struct {
    val builder = Struct.newBuilder()
    builder.putFields("id", proto.entityId.toValueProto())
    builder.putFields("fields", proto.fieldsList.toListValueProto().toValueProto())
    return builder.build()
  }
}

private object EntityListProtoPrint : Print<EntityListProto> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: EntityListProto): Printed = toListValue(a).toCompactString().printed()

  fun toListValue(proto: EntityListProto): ListValue {
    val builder = ListValue.newBuilder()
    proto.entitiesList.map(EntityProtoPrint::toStruct).forEach {
      builder.addValues(it.toValueProto())
    }
    return builder.build()
  }
}

private object DehydratedQueryResultPrint : Print<DehydratedQueryResult> {

  @Suppress("OVERRIDE_DEPRECATION")
  override fun print(a: DehydratedQueryResult): Printed = toString(a).printed()

  fun toString(a: DehydratedQueryResult): String {
    return "DehydratedQueryResult(" +
      "proto=${a.proto.print().value}, " +
      "entityById.size=${a.entityById.size}, " +
      "entityById=${a.entityById.toSortedMap().print().value})"
  }
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

fun DataConnectPath.toPrintable(): PrintFriendlyDataConnectPath = PrintFriendlyDataConnectPath(this)
