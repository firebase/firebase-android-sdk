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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.LocalDate
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import io.kotest.matchers.nulls.shouldNotBeNull
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

suspend fun FirebaseDataConnect.requestTimeAsDate(): LocalDate = requestTime().requestTimeAsDate

private suspend fun FirebaseDataConnect.requestTime(): ExprValuesQueryData.Item {
  val insertMutationRef =
    mutation("ExprValues_Insert", Unit, serializer<ExprValuesInsertData>(), serializer())
  val insertResult = insertMutationRef.execute()

  val queryVariables = ExprValuesQueryVariables(insertResult.data.key)
  val getByKeyQueryRef =
    query("ExprValues_GetByKey", queryVariables, serializer<ExprValuesQueryData>(), serializer())
  val queryResults = getByKeyQueryRef.execute()

  return queryResults.data.item.shouldNotBeNull()
}

@Serializable
private data class ExprValuesQueryData(val item: Item?) {
  @Serializable
  data class Item(
    val requestTimeAsDate: LocalDate,
  )
}

@Serializable private data class ExprValuesQueryVariables(val key: ExprValuesKey)

@Serializable private data class ExprValuesInsertData(val key: ExprValuesKey)

@Serializable
private data class ExprValuesKey(@Serializable(with = UUIDSerializer::class) val id: UUID)
