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
import com.google.firebase.dataconnect.util.ProtoGraft.withGraftedInStructs
import com.google.protobuf.Struct
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
  val struct =
    when (kindCase) {
      EntityOrEntityList.KindCase.ENTITY -> entity.rehydrateStruct(entityStructById)
      EntityOrEntityList.KindCase.ENTITYLIST ->
        throw IllegalStateException(
          "internal error a5ppf5vkzc: EntityOrEntityList.KindCase.ENTITYLIST not yet supported"
        )
      EntityOrEntityList.KindCase.KIND_NOT_SET -> return null
    }

  val dataConnectPath: DataConnectPath = path.toDataConnectPath()
  return Pair(dataConnectPath, struct)
}

private fun Entity.rehydrateStruct(entityStructById: Map<String, Struct>): Struct {}

private fun EntityPath.toDataConnectPath(): DataConnectPath =
  segmentsList.mapNotNull { fieldOrListIndex ->
    when (fieldOrListIndex.kindCase) {
      FieldOrListIndex.KindCase.FIELD -> DataConnectPathSegment.Field(fieldOrListIndex.field)
      FieldOrListIndex.KindCase.LIST_INDEX ->
        DataConnectPathSegment.ListIndex(fieldOrListIndex.listIndex)
      FieldOrListIndex.KindCase.KIND_NOT_SET -> null
    }
  }
