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

package com.google.firebase.dataconnect.sqlite

import com.google.firebase.dataconnect.DataConnectPath
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoGraft.withGraftedInStructs
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.kotlinsdk.Entity
import google.firebase.dataconnect.proto.kotlinsdk.EntityOrEntityList
import google.firebase.dataconnect.proto.kotlinsdk.EntityPath
import google.firebase.dataconnect.proto.kotlinsdk.FieldOrListIndex
import google.firebase.dataconnect.proto.kotlinsdk.QueryResult

internal fun rehydrateQueryResult(
  dehydratedQueryResult: QueryResult,
  entityStructById: Map<String, Struct>
): Struct {
  val struct = dehydratedQueryResult.struct
  if (dehydratedQueryResult.entitiesCount == 0) {
    return struct
  }

  val structByPath: Map<DataConnectPath, Struct> =
    dehydratedQueryResult.entitiesList.toStructByPathMap(entityStructById)

  return struct.withGraftedInStructs(structByPath)
}

private fun List<EntityOrEntityList>.toStructByPathMap(
  entityStructById: Map<String, Struct>
): Map<DataConnectPath, Struct> = mapNotNull { it.toPathStructPair(entityStructById) }.toMap()

private fun EntityOrEntityList.toPathStructPair(
  entityStructById: Map<String, Struct>
): Pair<DataConnectPath, Struct>? {
  val dataConnectPath: DataConnectPath = path.toDataConnectPath()

  val struct =
    when (kindCase) {
      EntityOrEntityList.KindCase.ENTITY -> entity.rehydrate(dataConnectPath, entityStructById)
      EntityOrEntityList.KindCase.ENTITYLIST ->
        throw IllegalStateException(
          "internal error a5ppf5vkzc: EntityOrEntityList.KindCase.ENTITYLIST not yet supported"
        )
      EntityOrEntityList.KindCase.KIND_NOT_SET -> return null
    }

  return Pair(dataConnectPath, struct)
}

private fun Entity.rehydrate(path: DataConnectPath, entityStructById: Map<String, Struct>): Struct {
  val struct =
    entityStructById[entityId]
      ?: throw EntityIdNotFoundException(
        "entity with ID $entityId path=${path.toPathString()} not found [f9tcr9fvmg]"
      )

  val expectedFields = fieldsList.toSet()
  val fieldValueByName: Map<String, Value> = struct.fieldsMap
  if (fieldValueByName.keys.toSet() == expectedFields) {
    return struct
  }

  val structBuilder = Struct.newBuilder()
  val missingFields = lazy(LazyThreadSafetyMode.NONE) { mutableSetOf<String>() }
  expectedFields.forEach { expectedField ->
    val fieldValue = fieldValueByName[expectedField]
    if (fieldValue === null) {
      missingFields.value.add(expectedField)
    } else {
      structBuilder.putFields(expectedField, fieldValue)
    }
  }

  if (missingFields.isInitialized()) {
    missingFields.value
      .takeIf { it.isNotEmpty() }
      ?.let { missingFields ->
        throw EntityMissingFieldsException(
          "entity with ID $entityId for path=${path.toPathString()} " +
            "is missing ${missingFields.size} of ${expectedFields.size} fields: " +
            "${missingFields.sorted().joinToString()} " +
            "(got ${structBuilder.fieldsCount} fields: " +
            "${structBuilder.fieldsMap.keys.sorted().joinToString()}) [nrtmqzdfy3]"
        )
      }
  }

  return structBuilder.build()
}

private fun EntityPath.toDataConnectPath(): DataConnectPath =
  segmentsList.mapNotNull { fieldOrListIndex ->
    when (fieldOrListIndex.kindCase) {
      FieldOrListIndex.KindCase.FIELD -> DataConnectPathSegment.Field(fieldOrListIndex.field)
      FieldOrListIndex.KindCase.LIST_INDEX ->
        DataConnectPathSegment.ListIndex(fieldOrListIndex.listIndex)
      FieldOrListIndex.KindCase.KIND_NOT_SET -> null
    }
  }

internal sealed class QueryRefRehydratorException(message: String) : Exception(message)

internal class EntityIdNotFoundException(message: String) : QueryRefRehydratorException(message)

internal class EntityMissingFieldsException(message: String) : QueryRefRehydratorException(message)
