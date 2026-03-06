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

package com.google.firebase.dataconnect.testutil.schemas

import com.google.firebase.dataconnect.AnyValue
import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.OptionalVariable
import com.google.firebase.dataconnect.OptionalVariable.Undefined
import com.google.firebase.dataconnect.OptionalVariable.Value
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class CachingConnector(val dataConnect: FirebaseDataConnect) {

  suspend fun insertString(string: String, tag: String? = null): Key {
    val variables = Variables.StringInsert(string = string, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingString_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateString(key: Key, string: String) {
    val variables = Variables.StringUpdate(key, string)
    val mutationRef =
      dataConnect.mutation("CachingString_Update", variables, serializer<Unit>(), serializer())
    mutationRef.execute()
  }

  suspend fun getString(key: Key, fetchPolicy: FetchPolicy?) =
    getString("CachingString_GetByKey", key, fetchPolicy)

  suspend fun getString2(key: Key, fetchPolicy: FetchPolicy?) =
    getString("CachingString_GetByKey2", key, fetchPolicy)

  private suspend fun getString(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.StringGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.StringGet>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getStringsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getStringsByTag("CachingString_GetByTag", tag, fetchPolicy)

  suspend fun getStringsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getStringsByTag("CachingString_GetByTag2", tag, fetchPolicy)

  private suspend fun getStringsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.StringGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.StringGetMany>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertNullableString(string: String?, tag: String? = null): Key {
    val variables = Variables.NullableStringInsert(string = string, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableString_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateNullableString(key: Key, string: String?) {
    val variables = Variables.NullableStringUpdate(key, string)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableString_Update",
        variables,
        serializer<Unit>(),
        serializer()
      )
    mutationRef.execute()
  }

  suspend fun getNullableString(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableString("CachingNullableString_GetByKey", key, fetchPolicy)

  suspend fun getNullableString2(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableString("CachingNullableString_GetByKey2", key, fetchPolicy)

  private suspend fun getNullableString(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableStringGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringGet>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getNullableStringsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableStringsByTag("CachingNullableString_GetByTag", tag, fetchPolicy)

  suspend fun getNullableStringsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableStringsByTag("CachingNullableString_GetByTag2", tag, fetchPolicy)

  private suspend fun getNullableStringsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableStringGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringGetMany>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertStringList(strings: List<String>, tag: String? = null): Key {
    val variables = Variables.StringListInsert(strings = strings, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingStringList_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateStringList(key: Key, strings: List<String>) {
    val variables = Variables.StringListUpdate(key, strings)
    val mutationRef =
      dataConnect.mutation("CachingStringList_Update", variables, serializer<Unit>(), serializer())
    mutationRef.execute()
  }

  suspend fun getStringList(key: Key, fetchPolicy: FetchPolicy?) =
    getStringList("CachingStringList_GetByKey", key, fetchPolicy)

  suspend fun getStringList2(key: Key, fetchPolicy: FetchPolicy?) =
    getStringList("CachingStringList_GetByKey2", key, fetchPolicy)

  private suspend fun getStringList(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.StringListGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.StringListGet>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getStringListsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getStringListsByTag("CachingStringList_GetByTag", tag, fetchPolicy)

  suspend fun getStringListsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getStringListsByTag("CachingStringList_GetByTag2", tag, fetchPolicy)

  private suspend fun getStringListsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.StringListGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.StringListGetMany>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertNullableStringList(strings: List<String?>, tag: String? = null): Key {
    val variables = Variables.NullableStringListInsert(strings = strings, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableStringList_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateNullableStringList(key: Key, strings: List<String?>) {
    val variables = Variables.NullableStringListUpdate(key, strings)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableStringList_Update",
        variables,
        serializer<Unit>(),
        serializer()
      )
    mutationRef.execute()
  }

  suspend fun getNullableStringList(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableStringList("CachingNullableStringList_GetByKey", key, fetchPolicy)

  suspend fun getNullableStringList2(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableStringList("CachingNullableStringList_GetByKey2", key, fetchPolicy)

  private suspend fun getNullableStringList(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableStringListGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringListGet>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getNullableStringListsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableStringListsByTag("CachingNullableStringList_GetByTag", tag, fetchPolicy)

  suspend fun getNullableStringListsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableStringListsByTag("CachingNullableStringList_GetByTag2", tag, fetchPolicy)

  private suspend fun getNullableStringListsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableStringListGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringListGetMany>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertStringNullableList(strings: List<String>?, tag: String? = null): Key {
    val variables = Variables.StringNullableListInsert(strings = strings, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingStringNullableList_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateStringNullableList(key: Key, strings: List<String>?) {
    val variables = Variables.StringNullableListUpdate(key, strings)
    val mutationRef =
      dataConnect.mutation(
        "CachingStringNullableList_Update",
        variables,
        serializer<Unit>(),
        serializer()
      )
    mutationRef.execute()
  }

  suspend fun getStringNullableList(key: Key, fetchPolicy: FetchPolicy?) =
    getStringNullableList("CachingStringNullableList_GetByKey", key, fetchPolicy)

  suspend fun getStringNullableList2(key: Key, fetchPolicy: FetchPolicy?) =
    getStringNullableList("CachingStringNullableList_GetByKey2", key, fetchPolicy)

  private suspend fun getStringNullableList(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.StringNullableListGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.StringNullableListGet>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getStringNullableListsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getStringNullableListsByTag("CachingStringNullableList_GetByTag", tag, fetchPolicy)

  suspend fun getStringNullableListsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getStringNullableListsByTag("CachingStringNullableList_GetByTag2", tag, fetchPolicy)

  private suspend fun getStringNullableListsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.StringNullableListGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.StringNullableListGetMany>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertNullableStringNullableList(strings: List<String?>?, tag: String? = null): Key {
    val variables = Variables.NullableStringNullableListInsert(strings = strings, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableStringNullableList_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateNullableStringNullableList(key: Key, strings: List<String?>?) {
    val variables = Variables.NullableStringNullableListUpdate(key, strings)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableStringNullableList_Update",
        variables,
        serializer<Unit>(),
        serializer()
      )
    mutationRef.execute()
  }

  suspend fun getNullableStringNullableList(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableStringNullableList("CachingNullableStringNullableList_GetByKey", key, fetchPolicy)

  suspend fun getNullableStringNullableList2(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableStringNullableList("CachingNullableStringNullableList_GetByKey2", key, fetchPolicy)

  private suspend fun getNullableStringNullableList(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableStringNullableListGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringNullableListGet>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getNullableStringNullableListsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableStringNullableListsByTag(
      "CachingNullableStringNullableList_GetByTag",
      tag,
      fetchPolicy
    )

  suspend fun getNullableStringNullableListsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableStringNullableListsByTag(
      "CachingNullableStringNullableList_GetByTag2",
      tag,
      fetchPolicy
    )

  private suspend fun getNullableStringNullableListsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableStringNullableListGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringNullableListGetMany>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertFloat(float: Double, tag: String? = null): Key {
    val variables = Variables.FloatInsert(float = float, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingFloat_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateFloat(key: Key, float: Double) {
    val variables = Variables.FloatUpdate(key, float)
    val mutationRef =
      dataConnect.mutation("CachingFloat_Update", variables, serializer<Unit>(), serializer())
    mutationRef.execute()
  }

  suspend fun getFloat(key: Key, fetchPolicy: FetchPolicy?) =
    getFloat("CachingFloat_GetByKey", key, fetchPolicy)

  suspend fun getFloat2(key: Key, fetchPolicy: FetchPolicy?) =
    getFloat("CachingFloat_GetByKey2", key, fetchPolicy)

  private suspend fun getFloat(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.FloatGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.FloatGet>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getFloatsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getFloatsByTag("CachingFloat_GetByTag", tag, fetchPolicy)

  suspend fun getFloatsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getFloatsByTag("CachingFloat_GetByTag2", tag, fetchPolicy)

  private suspend fun getFloatsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.FloatGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.FloatGetMany>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertNullableFloat(float: Double?, tag: String? = null): Key {
    val variables = Variables.NullableFloatInsert(float = float, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableFloat_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateNullableFloat(key: Key, float: Double?) {
    val variables = Variables.NullableFloatUpdate(key, float)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableFloat_Update",
        variables,
        serializer<Unit>(),
        serializer()
      )
    mutationRef.execute()
  }

  suspend fun getNullableFloat(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableFloat("CachingNullableFloat_GetByKey", key, fetchPolicy)

  suspend fun getNullableFloat2(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableFloat("CachingNullableFloat_GetByKey2", key, fetchPolicy)

  private suspend fun getNullableFloat(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableFloatGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.NullableFloatGet>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getNullableFloatsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableFloatsByTag("CachingNullableFloat_GetByTag", tag, fetchPolicy)

  suspend fun getNullableFloatsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableFloatsByTag("CachingNullableFloat_GetByTag2", tag, fetchPolicy)

  private suspend fun getNullableFloatsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableFloatGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableFloatGetMany>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertBoolean(boolean: Boolean, tag: String? = null): Key {
    val variables = Variables.BooleanInsert(boolean = boolean, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingBoolean_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateBoolean(key: Key, boolean: Boolean) {
    val variables = Variables.BooleanUpdate(key, boolean)
    val mutationRef =
      dataConnect.mutation("CachingBoolean_Update", variables, serializer<Unit>(), serializer())
    mutationRef.execute()
  }

  suspend fun getBoolean(key: Key, fetchPolicy: FetchPolicy?) =
    getBoolean("CachingBoolean_GetByKey", key, fetchPolicy)

  suspend fun getBoolean2(key: Key, fetchPolicy: FetchPolicy?) =
    getBoolean("CachingBoolean_GetByKey2", key, fetchPolicy)

  private suspend fun getBoolean(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.BooleanGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.BooleanGet>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getBooleansByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getBooleansByTag("CachingBoolean_GetByTag", tag, fetchPolicy)

  suspend fun getBooleansByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getBooleansByTag("CachingBoolean_GetByTag2", tag, fetchPolicy)

  private suspend fun getBooleansByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.BooleanGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.BooleanGetMany>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertNullableBoolean(boolean: Boolean?, tag: String? = null): Key {
    val variables = Variables.NullableBooleanInsert(boolean = boolean, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableBoolean_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateNullableBoolean(key: Key, boolean: Boolean?) {
    val variables = Variables.NullableBooleanUpdate(key, boolean)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableBoolean_Update",
        variables,
        serializer<Unit>(),
        serializer()
      )
    mutationRef.execute()
  }

  suspend fun getNullableBoolean(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableBoolean("CachingNullableBoolean_GetByKey", key, fetchPolicy)

  suspend fun getNullableBoolean2(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableBoolean("CachingNullableBoolean_GetByKey2", key, fetchPolicy)

  private suspend fun getNullableBoolean(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableBooleanGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableBooleanGet>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getNullableBooleansByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableBooleansByTag("CachingNullableBoolean_GetByTag", tag, fetchPolicy)

  suspend fun getNullableBooleansByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableBooleansByTag("CachingNullableBoolean_GetByTag2", tag, fetchPolicy)

  private suspend fun getNullableBooleansByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableBooleanGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableBooleanGetMany>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertAnyValue(any: AnyValue, tag: String? = null): Key {
    val variables = Variables.AnyValueInsert(any = any, tag = tag)
    val mutationRef =
      dataConnect.mutation("CachingAny_Insert", variables, serializer<Data.Insert>(), serializer())

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateAnyValue(key: Key, any: AnyValue) {
    val variables = Variables.AnyValueUpdate(key, any)
    val mutationRef =
      dataConnect.mutation("CachingAny_Update", variables, serializer<Unit>(), serializer())
    mutationRef.execute()
  }

  suspend fun getAnyValue(key: Key, fetchPolicy: FetchPolicy?) =
    getAnyValue("CachingAny_GetByKey", key, fetchPolicy)

  suspend fun getAnyValue2(key: Key, fetchPolicy: FetchPolicy?) =
    getAnyValue("CachingAny_GetByKey2", key, fetchPolicy)

  private suspend fun getAnyValue(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.AnyValueGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.AnyValueGet>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getAnyValuesByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getAnyValuesByTag("CachingAny_GetByTag", tag, fetchPolicy)

  suspend fun getAnyValuesByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getAnyValuesByTag("CachingAny_GetByTag2", tag, fetchPolicy)

  private suspend fun getAnyValuesByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.AnyValueGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.AnyValueGetMany>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertNullableAnyValue(any: AnyValue?, tag: String? = null): Key {
    val variables = Variables.NullableAnyValueInsert(any = any, tag = tag)
    val mutationRef =
      dataConnect.mutation(
        "CachingNullableAny_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateNullableAnyValue(key: Key, any: AnyValue?) {
    val variables = Variables.NullableAnyValueUpdate(key, any)
    val mutationRef =
      dataConnect.mutation("CachingNullableAny_Update", variables, serializer<Unit>(), serializer())
    mutationRef.execute()
  }

  suspend fun getNullableAnyValue(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableAnyValue("CachingNullableAny_GetByKey", key, fetchPolicy)

  suspend fun getNullableAnyValue2(key: Key, fetchPolicy: FetchPolicy?) =
    getNullableAnyValue("CachingNullableAny_GetByKey2", key, fetchPolicy)

  private suspend fun getNullableAnyValue(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableAnyValueGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableAnyValueGet>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getNullableAnyValuesByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableAnyValuesByTag("CachingNullableAny_GetByTag", tag, fetchPolicy)

  suspend fun getNullableAnyValuesByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getNullableAnyValuesByTag("CachingNullableAny_GetByTag2", tag, fetchPolicy)

  private suspend fun getNullableAnyValuesByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.NullableAnyValueGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableAnyValueGetMany>(),
        serializer()
      )
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun insertMixed(variables: Variables.MixedInsert): Key {
    val mutationRef =
      dataConnect.mutation(
        "CachingMixed_Insert",
        variables,
        serializer<Data.Insert>(),
        serializer()
      )

    val result = mutationRef.execute()

    return result.data.key
  }

  suspend fun updateMixed(key: Key, block: Variables.MixedUpdate.Builder.() -> Unit) {
    val variables = Variables.MixedUpdate.build(key, block)
    val mutationRef =
      dataConnect.mutation("CachingMixed_Update", variables, serializer<Unit>(), serializer())
    mutationRef.execute()
  }

  suspend fun getMixed(key: Key, fetchPolicy: FetchPolicy?) =
    getMixed("CachingMixed_GetByKey", key, fetchPolicy)

  suspend fun getMixed2(key: Key, fetchPolicy: FetchPolicy?) =
    getMixed("CachingMixed_GetByKey2", key, fetchPolicy)

  private suspend fun getMixed(
    operationName: String,
    key: Key,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.MixedGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.MixedGet>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  suspend fun getMixedsByTag(tag: String, fetchPolicy: FetchPolicy?) =
    getMixedsByTag("CachingMixed_GetByTag", tag, fetchPolicy)

  suspend fun getMixedsByTag2(tag: String, fetchPolicy: FetchPolicy?) =
    getMixedsByTag("CachingMixed_GetByTag2", tag, fetchPolicy)

  private suspend fun getMixedsByTag(
    operationName: String,
    tag: String,
    fetchPolicy: FetchPolicy?,
  ): QueryResult<Data.MixedGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.MixedGetMany>(), serializer())
    return queryRef.executeWithFetchPolicyIfNotNull(fetchPolicy)
  }

  object Variables {

    @Serializable data class GetByKey(val key: Key)

    @Serializable data class GetByTag(val tag: String)

    @Serializable data class StringUpdate(val key: Key, val string: String)

    @Serializable
    data class StringInsert(val string: String, val tag: OptionalVariable<String?>) {
      constructor(
        string: String,
        tag: String?
      ) : this(
        string = string,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class NullableStringUpdate(val key: Key, val string: String?)

    @Serializable
    data class NullableStringInsert(val string: String?, val tag: OptionalVariable<String?>) {
      constructor(
        string: String?,
        tag: String?
      ) : this(
        string = string,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class StringListUpdate(val key: Key, val strings: List<String>)

    @Serializable
    data class StringListInsert(val strings: List<String>, val tag: OptionalVariable<String?>) {
      constructor(
        strings: List<String>,
        tag: String?
      ) : this(
        strings = strings,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class NullableStringListUpdate(val key: Key, val strings: List<String?>)

    @Serializable
    data class NullableStringListInsert(
      val strings: List<String?>,
      val tag: OptionalVariable<String?>
    ) {
      constructor(
        strings: List<String?>,
        tag: String?
      ) : this(
        strings = strings,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class StringNullableListUpdate(val key: Key, val strings: List<String>?)

    @Serializable
    data class StringNullableListInsert(
      val strings: List<String>?,
      val tag: OptionalVariable<String?>
    ) {
      constructor(
        strings: List<String>?,
        tag: String?
      ) : this(
        strings = strings,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable
    data class NullableStringNullableListUpdate(val key: Key, val strings: List<String?>?)

    @Serializable
    data class NullableStringNullableListInsert(
      val strings: List<String?>?,
      val tag: OptionalVariable<String?>
    ) {
      constructor(
        strings: List<String?>?,
        tag: String?
      ) : this(
        strings = strings,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class FloatUpdate(val key: Key, val float: Double)

    @Serializable
    data class FloatInsert(val float: Double, val tag: OptionalVariable<String?>) {
      constructor(
        float: Double,
        tag: String?
      ) : this(
        float = float,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class NullableFloatUpdate(val key: Key, val float: Double?)

    @Serializable
    data class NullableFloatInsert(val float: Double?, val tag: OptionalVariable<String?>) {
      constructor(
        float: Double?,
        tag: String?
      ) : this(
        float = float,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class BooleanUpdate(val key: Key, val boolean: Boolean)

    @Serializable
    data class BooleanInsert(val boolean: Boolean, val tag: OptionalVariable<String?>) {
      constructor(
        boolean: Boolean,
        tag: String?
      ) : this(
        boolean = boolean,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class NullableBooleanUpdate(val key: Key, val boolean: Boolean?)

    @Serializable
    data class NullableBooleanInsert(val boolean: Boolean?, val tag: OptionalVariable<String?>) {
      constructor(
        boolean: Boolean?,
        tag: String?
      ) : this(
        boolean = boolean,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class AnyValueUpdate(val key: Key, val any: AnyValue)

    @Serializable
    data class AnyValueInsert(val any: AnyValue, val tag: OptionalVariable<String?>) {
      constructor(
        any: AnyValue,
        tag: String?
      ) : this(
        any = any,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable data class NullableAnyValueUpdate(val key: Key, val any: AnyValue?)

    @Serializable
    data class NullableAnyValueInsert(val any: AnyValue?, val tag: OptionalVariable<String?>) {
      constructor(
        any: AnyValue?,
        tag: String?
      ) : this(
        any = any,
        tag = if (tag === null) Undefined else Value(tag),
      )
    }

    @Serializable
    data class MixedUpdate(
      val key: Key,
      val string: OptionalVariable<String>,
      val stringNullable: OptionalVariable<String?>,
      val float: OptionalVariable<Double>,
      val floatNullable: OptionalVariable<Double?>,
      val boolean: OptionalVariable<Boolean>,
      val booleanNullable: OptionalVariable<Boolean?>,
      val any: OptionalVariable<AnyValue>,
      val anyNullable: OptionalVariable<AnyValue?>,
      val stringList: OptionalVariable<List<String?>?>,
      val floatList: OptionalVariable<List<Double?>?>,
      val booleanList: OptionalVariable<List<Boolean?>?>,
      val anyList: OptionalVariable<List<AnyValue?>?>,
    ) {

      @DslMarker annotation class BuilderDsl

      @BuilderDsl
      interface Builder {
        var string: String
        var stringNullable: String?
        var float: Double
        var floatNullable: Double?
        var boolean: Boolean
        var booleanNullable: Boolean?
        var any: AnyValue
        var anyNullable: AnyValue?
        var stringList: List<String?>?
        var floatList: List<Double?>?
        var booleanList: List<Boolean?>?
        var anyList: List<AnyValue?>?
      }

      companion object {

        fun build(key: Key, block: Builder.() -> Unit): MixedUpdate {
          var string: OptionalVariable<String> = Undefined
          var stringNullable: OptionalVariable<String?> = Undefined
          var float: OptionalVariable<Double> = Undefined
          var floatNullable: OptionalVariable<Double?> = Undefined
          var boolean: OptionalVariable<Boolean> = Undefined
          var booleanNullable: OptionalVariable<Boolean?> = Undefined
          var any: OptionalVariable<AnyValue> = Undefined
          var anyNullable: OptionalVariable<AnyValue?> = Undefined
          var stringList: OptionalVariable<List<String?>?> = Undefined
          var floatList: OptionalVariable<List<Double?>?> = Undefined
          var booleanList: OptionalVariable<List<Boolean?>?> = Undefined
          var anyList: OptionalVariable<List<AnyValue?>?> = Undefined

          return object : Builder {
              override var string: String
                get() = string.valueOrThrow()
                set(value) {
                  string = Value(value)
                }

              override var stringNullable: String?
                get() = stringNullable.valueOrThrow()
                set(value) {
                  stringNullable = Value(value)
                }

              override var float: Double
                get() = float.valueOrThrow()
                set(value) {
                  float = Value(value)
                }

              override var floatNullable: Double?
                get() = floatNullable.valueOrThrow()
                set(value) {
                  floatNullable = Value(value)
                }

              override var boolean: Boolean
                get() = boolean.valueOrThrow()
                set(value) {
                  boolean = Value(value)
                }

              override var booleanNullable: Boolean?
                get() = booleanNullable.valueOrThrow()
                set(value) {
                  booleanNullable = Value(value)
                }

              override var any: AnyValue
                get() = any.valueOrThrow()
                set(value) {
                  any = Value(value)
                }

              override var anyNullable: AnyValue?
                get() = anyNullable.valueOrThrow()
                set(value) {
                  anyNullable = Value(value)
                }

              override var stringList: List<String?>?
                get() = stringList.valueOrThrow()
                set(value) {
                  stringList = Value(value)
                }

              override var floatList: List<Double?>?
                get() = floatList.valueOrThrow()
                set(value) {
                  floatList = Value(value)
                }

              override var booleanList: List<Boolean?>?
                get() = booleanList.valueOrThrow()
                set(value) {
                  booleanList = Value(value)
                }

              override var anyList: List<AnyValue?>?
                get() = anyList.valueOrThrow()
                set(value) {
                  anyList = Value(value)
                }
            }
            .apply(block)
            .let {
              MixedUpdate(
                key = key,
                string = string,
                stringNullable = stringNullable,
                float = float,
                floatNullable = floatNullable,
                boolean = boolean,
                booleanNullable = booleanNullable,
                any = any,
                anyNullable = anyNullable,
                stringList = stringList,
                floatList = floatList,
                booleanList = booleanList,
                anyList = anyList,
              )
            }
        }
      }
    }

    @Serializable
    data class MixedInsert(
      val string: String,
      val stringNullable: String?,
      val float: Double,
      val floatNullable: Double?,
      val boolean: Boolean,
      val booleanNullable: Boolean?,
      val any: AnyValue,
      val anyNullable: AnyValue?,
      val stringList: List<String?>?,
      val floatList: List<Double?>?,
      val booleanList: List<Boolean?>?,
      val anyList: List<AnyValue?>?,
      val tag: OptionalVariable<String>,
    )
  }

  object Data {

    @Serializable data class Insert(val key: Key)

    @Serializable
    data class StringGet(val item: Item?) {
      @Serializable data class Item(val string: String)
    }

    @Serializable
    data class StringGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val string: String,
      )
    }

    @Serializable
    data class NullableStringGet(val item: Item?) {
      @Serializable data class Item(val string: String?)
    }

    @Serializable
    data class NullableStringGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val string: String?,
      )
    }

    @Serializable
    data class StringListGet(val item: Item?) {
      @Serializable data class Item(val strings: List<String>)
    }

    @Serializable
    data class StringListGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val strings: List<String>,
      )
    }

    @Serializable
    data class NullableStringListGet(val item: Item?) {
      @Serializable data class Item(val strings: List<String?>)
    }

    @Serializable
    data class NullableStringListGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val strings: List<String?>,
      )
    }

    @Serializable
    data class StringNullableListGet(val item: Item?) {
      @Serializable data class Item(val strings: List<String>?)
    }

    @Serializable
    data class StringNullableListGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val strings: List<String>?,
      )
    }

    @Serializable
    data class NullableStringNullableListGet(val item: Item?) {
      @Serializable data class Item(val strings: List<String?>?)
    }

    @Serializable
    data class NullableStringNullableListGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val strings: List<String?>?,
      )
    }

    @Serializable
    data class FloatGet(val item: Item?) {
      @Serializable data class Item(val float: Double)
    }

    @Serializable
    data class FloatGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val float: Double,
      )
    }

    @Serializable
    data class NullableFloatGet(val item: Item?) {
      @Serializable data class Item(val float: Double?)
    }

    @Serializable
    data class NullableFloatGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val float: Double?,
      )
    }

    @Serializable
    data class BooleanGet(val item: Item?) {
      @Serializable data class Item(val boolean: Boolean)
    }

    @Serializable
    data class BooleanGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val boolean: Boolean,
      )
    }

    @Serializable
    data class NullableBooleanGet(val item: Item?) {
      @Serializable data class Item(val boolean: Boolean?)
    }

    @Serializable
    data class NullableBooleanGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val boolean: Boolean?,
      )
    }

    @Serializable
    data class AnyValueGet(val item: Item?) {
      @Serializable data class Item(val any: AnyValue)
    }

    @Serializable
    data class AnyValueGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val any: AnyValue,
      )
    }

    @Serializable
    data class NullableAnyValueGet(val item: Item?) {
      @Serializable data class Item(val any: AnyValue?)
    }

    @Serializable
    data class NullableAnyValueGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val any: AnyValue?,
      )
    }

    @Serializable
    data class MixedGet(val item: Item?) {
      @Serializable
      data class Item(
        val string: String,
        val stringNullable: String?,
        val float: Double,
        val floatNullable: Double?,
        val boolean: Boolean,
        val booleanNullable: Boolean?,
        val any: AnyValue,
        val anyNullable: AnyValue?,
        val stringList: List<String?>?,
        val floatList: List<Double?>?,
        val booleanList: List<Boolean?>?,
        val anyList: List<AnyValue?>?,
      )
    }

    @Serializable
    data class MixedGetMany(val items: List<Item>) {
      @Serializable
      data class Item(
        val id: @Serializable(with = UUIDSerializer::class) UUID,
        val string: String,
        val stringNullable: String?,
        val float: Double,
        val floatNullable: Double?,
        val boolean: Boolean,
        val booleanNullable: Boolean?,
        val any: AnyValue,
        val anyNullable: AnyValue?,
        val stringList: List<String?>?,
        val floatList: List<Double?>?,
        val booleanList: List<Boolean?>?,
        val anyList: List<AnyValue?>?,
      )
    }
  }

  @Serializable data class Key(val id: @Serializable(with = UUIDSerializer::class) UUID)

  companion object {
    const val CONNECTOR_NAME = "caching"
  }
}

private suspend fun <Data, Variables> QueryRef<Data, Variables>.executeWithFetchPolicyIfNotNull(
  fetchPolicy: FetchPolicy?
): QueryResult<Data, Variables> =
  if (fetchPolicy !== null) {
    execute(fetchPolicy)
  } else {
    execute()
  }

@JvmName("QueryResult_StringGet_shouldBe")
fun QueryResult<CachingConnector.Data.StringGet, *>.shouldBe(
  string: String,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().string shouldBe string
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.StringGetMany, *>.shouldBe(
  strings: Collection<String>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.string } shouldContainExactlyInAnyOrder strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringGet, *>.shouldBe(
  string: String?,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().string shouldBe string
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringGetMany, *>.shouldBe(
  strings: Collection<String?>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.string } shouldContainExactlyInAnyOrder strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringListGet_shouldBe")
fun QueryResult<CachingConnector.Data.StringListGet, *>.shouldBe(
  strings: List<String>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().strings shouldBe strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringListGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.StringListGetMany, *>.shouldBe(
  stringLists: Collection<List<String>>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.strings } shouldContainExactlyInAnyOrder stringLists
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringListGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringListGet, *>.shouldBe(
  strings: List<String?>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().strings shouldBe strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringListGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringListGetMany, *>.shouldBe(
  stringLists: Collection<List<String?>>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.strings } shouldContainExactlyInAnyOrder stringLists
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringNullableListGet_shouldBe")
fun QueryResult<CachingConnector.Data.StringNullableListGet, *>.shouldBe(
  strings: List<String>?,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().strings shouldBe strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringNullableListGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.StringNullableListGetMany, *>.shouldBe(
  stringLists: Collection<List<String>?>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.strings } shouldContainExactlyInAnyOrder stringLists
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringNullableListGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringNullableListGet, *>.shouldBe(
  strings: List<String?>?,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().strings shouldBe strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringNullableListGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringNullableListGetMany, *>.shouldBe(
  stringLists: Collection<List<String?>?>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.strings } shouldContainExactlyInAnyOrder stringLists
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_FloatGet_shouldBe")
fun QueryResult<CachingConnector.Data.FloatGet, *>.shouldBe(float: Double, dataSource: DataSource) {
  assertSoftly {
    this.data.item.shouldNotBeNull().float shouldBe float
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_FloatGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.FloatGetMany, *>.shouldBe(
  floats: Collection<Double>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.float } shouldContainExactlyInAnyOrder floats
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableFloatGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableFloatGet, *>.shouldBe(
  float: Double?,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().float shouldBe float
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableFloatGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableFloatGetMany, *>.shouldBe(
  floats: Collection<Double?>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.float } shouldContainExactlyInAnyOrder floats
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_BooleanGet_shouldBe")
fun QueryResult<CachingConnector.Data.BooleanGet, *>.shouldBe(
  boolean: Boolean,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().boolean shouldBe boolean
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_BooleanGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.BooleanGetMany, *>.shouldBe(
  booleans: Collection<Boolean>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.boolean } shouldContainExactlyInAnyOrder booleans
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableBooleanGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableBooleanGet, *>.shouldBe(
  boolean: Boolean?,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().boolean shouldBe boolean
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableBooleanGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableBooleanGetMany, *>.shouldBe(
  booleans: Collection<Boolean?>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.boolean } shouldContainExactlyInAnyOrder booleans
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_AnyValueGet_shouldBe")
fun QueryResult<CachingConnector.Data.AnyValueGet, *>.shouldBe(
  any: AnyValue,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().any shouldBe any
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_AnyValueGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.AnyValueGetMany, *>.shouldBe(
  anys: Collection<AnyValue>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.any } shouldContainExactlyInAnyOrder anys
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableAnyValueGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableAnyValueGet, *>.shouldBe(
  any: AnyValue?,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().any shouldBe any
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableAnyValueGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableAnyValueGetMany, *>.shouldBe(
  anys: Collection<AnyValue?>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.any } shouldContainExactlyInAnyOrder anys
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_MixedGet_shouldBe")
fun QueryResult<CachingConnector.Data.MixedGet, *>.shouldBe(
  item: CachingConnector.Data.MixedGet.Item,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull() shouldBe item
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_MixedGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.MixedGetMany, *>.shouldBe(
  items: Collection<CachingConnector.Data.MixedGet.Item>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.toMixedGetItem() } shouldContainExactlyInAnyOrder items
    this.dataSource shouldBe dataSource
  }
}

private fun CachingConnector.Data.MixedGetMany.Item.toMixedGetItem() =
  CachingConnector.Data.MixedGet.Item(
    string = string,
    stringNullable = stringNullable,
    float = float,
    floatNullable = floatNullable,
    boolean = boolean,
    booleanNullable = booleanNullable,
    any = any,
    anyNullable = anyNullable,
    stringList = stringList,
    floatList = floatList,
    booleanList = booleanList,
    anyList = anyList,
  )
