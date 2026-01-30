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
import com.google.firebase.dataconnect.util.ProtoPrune
import com.google.firebase.dataconnect.util.ProtoPrune.withPrunedDescendants
import com.google.firebase.dataconnect.util.WithPrunedDescendantsPredicate
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.kotlinsdk.Entity
import google.firebase.dataconnect.proto.kotlinsdk.EntityOrEntityList
import google.firebase.dataconnect.proto.kotlinsdk.EntityPath
import google.firebase.dataconnect.proto.kotlinsdk.FieldOrListIndex
import google.firebase.dataconnect.proto.kotlinsdk.QueryResult

internal data class DehydratedQueryResult(
  val proto: QueryResult,
  val entities: List<Entity>,
) {
  data class Entity(val entityId: String, val struct: Struct)
}

internal fun dehydrateQueryResult(
  queryResult: Struct,
  getEntityIdForPath: GetEntityIdForPathFunction? = null
): DehydratedQueryResult {
  val protoBuilder = QueryResult.newBuilder()
  val entities = protoBuilder.initialize(queryResult, getEntityIdForPath)
  return DehydratedQueryResult(protoBuilder.build(), entities)
}

private fun QueryResult.Builder.initialize(
  queryResult: Struct,
  getEntityIdForPath: GetEntityIdForPathFunction?
): List<DehydratedQueryResult.Entity> {
  val entityByPath = run {
    val pruneResult = pruneEntities(queryResult, getEntityIdForPath)
    if (pruneResult === null) {
      setStruct(queryResult)
      return emptyList()
    }

    setStruct(pruneResult.prunedStruct)
    pruneResult.entityByPath
  }

  entityByPath.entries.forEach { (path, entity) ->
    addEntities(entity.toEntityOrEntityListProto(path))
  }

  return entityByPath.values.toList()
}

private fun DehydratedQueryResult.Entity.toEntityOrEntityListProto(
  path: DataConnectPath
): EntityOrEntityList {
  val entityProto =
    Entity.newBuilder().run {
      setEntityId(entityId)
      struct.fieldsMap.keys.forEach { fieldName -> addFields(fieldName) }
      build()
    }

  return EntityOrEntityList.newBuilder().run {
    setPath(path.toEntityPathProto())
    setEntity(entityProto)
    build()
  }
}

private fun DataConnectPath.toEntityPathProto(): EntityPath {
  val builder = EntityPath.newBuilder()
  forEach { pathSegment -> builder.addSegments(pathSegment.toFieldOrListIndexProto()) }
  return builder.build()
}

private fun DataConnectPathSegment.toFieldOrListIndexProto(): FieldOrListIndex {
  val builder = FieldOrListIndex.newBuilder()
  when (this) {
    is DataConnectPathSegment.Field -> builder.setField(field)
    is DataConnectPathSegment.ListIndex -> builder.setListIndex(index)
  }
  return builder.build()
}

private data class PruneEntitiesResult(
  val prunedStruct: Struct,
  val entityByPath: Map<DataConnectPath, DehydratedQueryResult.Entity>,
)

private fun pruneEntities(
  queryResult: Struct,
  getEntityIdForPath: GetEntityIdForPathFunction?
): PruneEntitiesResult? {
  if (getEntityIdForPath === null) {
    return null
  }

  val entityIdByPath = mutableMapOf<DataConnectPath, String?>()

  val prunePredicate: WithPrunedDescendantsPredicate = { path, listSize ->
    if (listSize !== null) {
      false
    } else {
      val entityId =
        if (path in entityIdByPath) {
          entityIdByPath[path]!!
        } else {
          getEntityIdForPath(path).also { entityIdByPath[path] = it }
        }
      entityId !== null
    }
  }

  val dehydratedQueryResult = queryResult.withPrunedDescendants(prunePredicate)
  if (dehydratedQueryResult === null) {
    return null
  }

  val entityByPath =
    dehydratedQueryResult.prunedValueByPath.mapValues { (path, prunedValue) ->
      val entityId = entityIdByPath[path]
      checkNotNull(entityId) {
        "internal error namtm83dqx: entityIdByPath[path=${path.toPathString()}] returned null"
      }
      check(prunedValue is ProtoPrune.PrunedStruct) {
        "internal error a5pst76bz8: prunedValue is ${prunedValue::class.qualifiedName}, " +
          "which is not yet supported (prunedValue=$prunedValue)"
      }
      DehydratedQueryResult.Entity(entityId, prunedValue.struct)
    }

  return PruneEntitiesResult(dehydratedQueryResult.prunedStruct, entityByPath)
}
