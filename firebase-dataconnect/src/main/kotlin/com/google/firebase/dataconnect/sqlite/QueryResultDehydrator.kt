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
import com.google.firebase.dataconnect.toEntityPathProto
import com.google.firebase.dataconnect.toPathString
import com.google.firebase.dataconnect.util.ProtoPrune
import com.google.firebase.dataconnect.util.ProtoPrune.withPrunedDescendants
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.withAddedListIndex
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.kotlinsdk.Entity as EntityProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityList as EntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.EntityOrEntityList as EntityOrEntityListProto
import google.firebase.dataconnect.proto.kotlinsdk.QueryResult as QueryResultProto

internal data class DehydratedQueryResult(
  val proto: QueryResultProto,
  val entityById: Map<String, Struct>,
)

internal fun dehydrateQueryResult(
  queryResult: Struct,
  getEntityIdForPath: GetEntityIdForPathFunction? = null
): DehydratedQueryResult {
  val protoBuilder = QueryResultProto.newBuilder()
  val entityById = protoBuilder.initialize(queryResult, getEntityIdForPath)
  return DehydratedQueryResult(protoBuilder.build(), entityById)
}

private fun QueryResultProto.Builder.initialize(
  queryResult: Struct,
  getEntityIdForPath: GetEntityIdForPathFunction?
): Map<String, Struct> {
  val pruneResult =
    if (getEntityIdForPath === null) {
      null
    } else {
      pruneEntities(queryResult, EntityIdMemoizer(getEntityIdForPath))
    }

  if (pruneResult === null) {
    setStruct(queryResult)
    return emptyMap()
  }

  setStruct(pruneResult.prunedStruct)

  pruneResult.entityByPath.entries.forEach { (path, entity) ->
    addEntities(entity.toEntityOrEntityListProto(path))
  }
  pruneResult.entityListByPath.entries.forEach { (path, entityList) ->
    addEntities(entityList.toEntityOrEntityListProto(path))
  }

  val entities =
    pruneResult.entityByPath.values.toList() + pruneResult.entityListByPath.values.flatten()
  val entityStructsById = entities.groupBy { it.entityId }.mapValues { it.value.map { it.struct } }

  // TODO: merge together entities that occur multiple times; that is, when single() throws.
  return entityStructsById.mapValues { it.value.single() }
}

private fun EntityIdStructPair.toEntityOrEntityListProto(
  path: DataConnectPath
): EntityOrEntityListProto =
  EntityOrEntityListProto.newBuilder().run {
    setPath(path.toEntityPathProto())
    setEntity(toEntityProto())
    build()
  }

private fun EntityIdStructPair.toEntityProto(): EntityProto =
  EntityProto.newBuilder().run {
    setEntityId(this@toEntityProto.entityId)
    addAllFields(this@toEntityProto.struct.fieldsMap.keys)
    build()
  }

private fun List<EntityIdStructPair>.toEntityOrEntityListProto(
  path: DataConnectPath
): EntityOrEntityListProto =
  EntityOrEntityListProto.newBuilder().run {
    setPath(path.toEntityPathProto())
    setEntityList(toEntityListProto())
    build()
  }

private fun List<EntityIdStructPair>.toEntityListProto(): EntityListProto =
  EntityListProto.newBuilder().run {
    forEach { addEntities(it.toEntityProto()) }
    build()
  }

internal data class EntityIdStructPair(
  val entityId: String,
  val struct: Struct,
) {
  override fun toString() =
    "EntityIdStructPair(entityId=$entityId, struct=${struct.toCompactString()})"
}

private data class PruneEntitiesResult(
  val prunedStruct: Struct,
  val entityByPath: Map<DataConnectPath, EntityIdStructPair>,
  val entityListByPath: Map<DataConnectPath, List<EntityIdStructPair>>,
)

private fun pruneEntities(
  queryResult: Struct,
  entityIdByPath: EntityIdMemoizer,
): PruneEntitiesResult? {
  val dehydratedQueryResult =
    queryResult.withPrunedDescendants { path, listSize ->
      if (listSize === null) {
        entityIdByPath.isEntity(path)
      } else {
        var entityCount = 0
        var nonEntityCount = 0
        repeat(listSize) {
          val listElementPath = path.withAddedListIndex(it)
          if (entityIdByPath.isEntity(listElementPath)) {
            entityCount++
          } else {
            nonEntityCount++
          }
        }
        entityCount > 0 && nonEntityCount == 0
      }
    }

  if (dehydratedQueryResult === null) {
    return null
  }

  val entityByPath: Map<DataConnectPath, EntityIdStructPair>
  val entityListByPath: Map<DataConnectPath, List<EntityIdStructPair>>

  run {
    val entityByPathBuilder = mutableMapOf<DataConnectPath, EntityIdStructPair>()
    val entityListByPathBuilder = mutableMapOf<DataConnectPath, List<EntityIdStructPair>>()

    dehydratedQueryResult.prunedValueByPath.entries.forEach { (path, prunedValue) ->
      when (prunedValue) {
        is ProtoPrune.PrunedStruct -> {
          val entityId = entityIdByPath.getInitializedEntityId(path)
          val entity = EntityIdStructPair(entityId, prunedValue.struct)
          entityByPathBuilder[path] = entity
        }
        is ProtoPrune.PrunedListValue -> {
          val entities =
            prunedValue.structs.mapIndexed { index, struct ->
              val entityPath = path.withAddedListIndex(index)
              val entityId = entityIdByPath.getInitializedEntityId(entityPath)
              EntityIdStructPair(entityId, struct)
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

private class EntityIdMemoizer(private val getEntityIdForPath: GetEntityIdForPathFunction) {

  sealed interface EntityId {

    val entityIdOrNull: String?

    object NotAnEntity : EntityId {
      override val entityIdOrNull: Nothing? = null
    }

    @JvmInline
    value class IsAnEntity(val entityId: String) : EntityId {
      override val entityIdOrNull: String
        get() = entityId
    }
  }

  private val entityIdByPath = mutableMapOf<DataConnectPath, EntityId>()

  private fun ensureInitialized(path: DataConnectPath): EntityId =
    entityIdByPath.getOrPut(path) {
      getEntityIdForPath(path)?.let(EntityId::IsAnEntity) ?: EntityId.NotAnEntity
    }

  fun isEntity(path: DataConnectPath): Boolean =
    when (ensureInitialized(path)) {
      is EntityId.IsAnEntity -> true
      EntityId.NotAnEntity -> false
    }

  fun getInitializedEntityId(path: DataConnectPath): String {
    val memoizedValue =
      entityIdByPath[path]
        ?: throw IllegalArgumentException(
          "entity ID for path=${path.toPathString()} is not initialized [d8a8k6bkzh]"
        )
    return when (memoizedValue) {
      is EntityId.IsAnEntity -> memoizedValue.entityId
      EntityId.NotAnEntity ->
        throw IllegalArgumentException("path=${path.toPathString()} is not an entity [jserv86jpk]")
    }
  }
}
