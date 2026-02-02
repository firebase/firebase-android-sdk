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
import com.google.firebase.dataconnect.sqlite.DehydratedQueryResult.Entity
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoPrune
import com.google.firebase.dataconnect.util.ProtoPrune.withPrunedDescendants
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.WithPrunedDescendantsPredicate
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.kotlinsdk.Entity as EntityProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityList as EntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityOrEntityList as EntityOrEntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityPath as EntityPathProto
import google.firebase.dataconnect.proto.kotlinsdk.FieldOrListIndex as FieldOrListIndexProto
import google.firebase.dataconnect.proto.kotlinsdk.QueryResult as QueryResultProto

internal data class DehydratedQueryResult(
  val proto: QueryResultProto,
  val entities: List<Entity>,
) {
  data class Entity(val entityId: String, val struct: Struct) {
    override fun toString() = "Entity(entityId=$entityId, struct=${struct.toCompactString()})"
  }
}

internal fun dehydrateQueryResult(
  queryResult: Struct,
  getEntityIdForPath: GetEntityIdForPathFunction? = null
): DehydratedQueryResult {
  val protoBuilder = QueryResultProto.newBuilder()
  val entities = protoBuilder.initialize(queryResult, getEntityIdForPath)
  return DehydratedQueryResult(protoBuilder.build(), entities)
}

private fun QueryResultProto.Builder.initialize(
  queryResult: Struct,
  getEntityIdForPath: GetEntityIdForPathFunction?
): List<Entity> {
  val (entityByPath, entityListByPath) =
    run {
      val pruneResult = pruneEntities(queryResult, getEntityIdForPath)
      if (pruneResult === null) {
        setStruct(queryResult)
        return emptyList()
      }

      setStruct(pruneResult.prunedStruct)
      Pair(pruneResult.entityByPath, pruneResult.entityListByPath)
    }

  entityByPath.entries.forEach { (path, entity) ->
    addEntities(entity.toEntityOrEntityListProto(path))
  }
  entityListByPath.entries.forEach { (path, entityList) ->
    addEntities(entityList.toEntityOrEntityListProto(path))
  }

  return entityByPath.values.toList()
}

private fun Entity.toEntityOrEntityListProto(path: DataConnectPath): EntityOrEntityListProto =
  EntityOrEntityListProto.newBuilder().run {
    setPath(path.toEntityPathProto())
    setEntity(toEntityProto())
    build()
  }

private fun Entity.toEntityProto(): EntityProto =
  EntityProto.newBuilder().run {
    setEntityId(entityId)
    struct.fieldsMap.keys.forEach { fieldName -> addFields(fieldName) }
    build()
  }

private fun List<Entity>.toEntityOrEntityListProto(path: DataConnectPath): EntityOrEntityListProto =
  EntityOrEntityListProto.newBuilder().run {
    setPath(path.toEntityPathProto())
    setEntityList(toEntityListProto())
    build()
  }

private fun List<Entity>.toEntityListProto(): EntityListProto =
  EntityListProto.newBuilder().run {
    forEach { addEntities(it.toEntityProto()) }
    build()
  }

private fun DataConnectPath.toEntityPathProto(): EntityPathProto {
  val builder = EntityPathProto.newBuilder()
  forEach { pathSegment -> builder.addSegments(pathSegment.toFieldOrListIndexProto()) }
  return builder.build()
}

private fun DataConnectPathSegment.toFieldOrListIndexProto(): FieldOrListIndexProto {
  val builder = FieldOrListIndexProto.newBuilder()
  when (this) {
    is DataConnectPathSegment.Field -> builder.setField(field)
    is DataConnectPathSegment.ListIndex -> builder.setListIndex(index)
  }
  return builder.build()
}

private data class PruneEntitiesResult(
  val prunedStruct: Struct,
  val entityByPath: Map<DataConnectPath, Entity>,
  val entityListByPath: Map<DataConnectPath, List<Entity>>,
)

private fun pruneEntities(
  queryResult: Struct,
  getEntityIdForPath: GetEntityIdForPathFunction?
): PruneEntitiesResult? {
  if (getEntityIdForPath === null) {
    return null
  }

  val entityIdByPath = mutableMapOf<DataConnectPath, String?>()

  fun getOrCalculateEntityId(path: DataConnectPath): String? =
    if (path in entityIdByPath) {
      entityIdByPath[path]!!
    } else {
      getEntityIdForPath(path).also { entityIdByPath[path] = it }
    }

  val prunePredicate: WithPrunedDescendantsPredicate = { path, listSize ->
    if (listSize !== null) {
      var entityCount = 0
      var nonEntityCount = 0
      repeat(listSize) {
        val listElementPath = path.withAddedListIndex(it)
        val listElementEntityId = getOrCalculateEntityId(listElementPath)
        if (listElementEntityId !== null) {
          entityCount++
        } else {
          nonEntityCount++
        }
      }
      entityCount > 0 && nonEntityCount == 0
    } else {
      val entityId = getOrCalculateEntityId(path)
      entityId !== null
    }
  }

  val dehydratedQueryResult = queryResult.withPrunedDescendants(prunePredicate)
  if (dehydratedQueryResult === null) {
    return null
  }

  val entityByPath: Map<DataConnectPath, Entity>
  val entityListByPath: Map<DataConnectPath, List<Entity>>

  run {
    val entityByPathBuilder = mutableMapOf<DataConnectPath, Entity>()
    val entityListByPathBuilder = mutableMapOf<DataConnectPath, List<Entity>>()

    dehydratedQueryResult.prunedValueByPath.entries.forEach { (path, prunedValue) ->
      when (prunedValue) {
        is ProtoPrune.PrunedStruct -> {
          val entityId = entityIdByPath[path]
          checkNotNull(entityId) {
            "internal error namtm83dqx: entityIdByPath[path=${path.toPathString()}] returned null"
          }
          val entity = Entity(entityId, prunedValue.struct)
          entityByPathBuilder[path] = entity
        }
        is ProtoPrune.PrunedListValue -> {
          val entities =
            prunedValue.structs.mapIndexed { index, struct ->
              val entityPath = path.withAddedListIndex(index)
              val entityId = entityIdByPath[entityPath]
              checkNotNull(entityId) {
                "internal error yx2293sbrs: " +
                  "entityIdByPath[path=${entityPath.toPathString()}] returned null"
              }
              Entity(entityId, struct)
            }
          entityListByPathBuilder[path] = entities
        }
      }
    }

    entityByPath = entityByPathBuilder.toMap()
    entityListByPath = entityListByPathBuilder.toMap()
  }

  return PruneEntitiesResult(dehydratedQueryResult.prunedStruct, entityByPath, entityListByPath)
}
