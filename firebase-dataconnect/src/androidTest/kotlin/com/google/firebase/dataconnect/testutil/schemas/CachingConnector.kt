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
import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
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

  suspend fun getString(key: Key) = getString("CachingString_GetByKey", key)

  suspend fun getString2(key: Key) = getString("CachingString_GetByKey2", key)

  private suspend fun getString(
    operationName: String,
    key: Key
  ): QueryResult<Data.StringGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.StringGet>(), serializer())
    return queryRef.execute()
  }

  suspend fun getStringsByTag(tag: String) = getStringsByTag("CachingString_GetByTag", tag)

  suspend fun getStringsByTag2(tag: String) = getStringsByTag("CachingString_GetByTag2", tag)

  private suspend fun getStringsByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.StringGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.StringGetMany>(), serializer())
    return queryRef.execute()
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

  suspend fun getNullableString(key: Key) = getNullableString("CachingNullableString_GetByKey", key)

  suspend fun getNullableString2(key: Key) =
    getNullableString("CachingNullableString_GetByKey2", key)

  private suspend fun getNullableString(
    operationName: String,
    key: Key
  ): QueryResult<Data.NullableStringGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringGet>(),
        serializer()
      )
    return queryRef.execute()
  }

  suspend fun getNullableStringsByTag(tag: String) =
    getNullableStringsByTag("CachingNullableString_GetByTag", tag)

  suspend fun getNullableStringsByTag2(tag: String) =
    getNullableStringsByTag("CachingNullableString_GetByTag2", tag)

  private suspend fun getNullableStringsByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.NullableStringGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringGetMany>(),
        serializer()
      )
    return queryRef.execute()
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

  suspend fun getStringList(key: Key) = getStringList("CachingStringList_GetByKey", key)

  suspend fun getStringList2(key: Key) = getStringList("CachingStringList_GetByKey2", key)

  private suspend fun getStringList(
    operationName: String,
    key: Key
  ): QueryResult<Data.StringListGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.StringListGet>(), serializer())
    return queryRef.execute()
  }

  suspend fun getStringListsByTag(tag: String) =
    getStringListsByTag("CachingStringList_GetByTag", tag)

  suspend fun getStringListsByTag2(tag: String) =
    getStringListsByTag("CachingStringList_GetByTag2", tag)

  private suspend fun getStringListsByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.StringListGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.StringListGetMany>(),
        serializer()
      )
    return queryRef.execute()
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

  suspend fun getNullableStringList(key: Key) =
    getNullableStringList("CachingNullableStringList_GetByKey", key)

  suspend fun getNullableStringList2(key: Key) =
    getNullableStringList("CachingNullableStringList_GetByKey2", key)

  private suspend fun getNullableStringList(
    operationName: String,
    key: Key
  ): QueryResult<Data.NullableStringListGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringListGet>(),
        serializer()
      )
    return queryRef.execute()
  }

  suspend fun getNullableStringListsByTag(tag: String) =
    getNullableStringListsByTag("CachingNullableStringList_GetByTag", tag)

  suspend fun getNullableStringListsByTag2(tag: String) =
    getNullableStringListsByTag("CachingNullableStringList_GetByTag2", tag)

  private suspend fun getNullableStringListsByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.NullableStringListGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringListGetMany>(),
        serializer()
      )
    return queryRef.execute()
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

  suspend fun getStringNullableList(key: Key) =
    getStringNullableList("CachingStringNullableList_GetByKey", key)

  suspend fun getStringNullableList2(key: Key) =
    getStringNullableList("CachingStringNullableList_GetByKey2", key)

  private suspend fun getStringNullableList(
    operationName: String,
    key: Key
  ): QueryResult<Data.StringNullableListGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.StringNullableListGet>(),
        serializer()
      )
    return queryRef.execute()
  }

  suspend fun getStringNullableListsByTag(tag: String) =
    getStringNullableListsByTag("CachingStringNullableList_GetByTag", tag)

  suspend fun getStringNullableListsByTag2(tag: String) =
    getStringNullableListsByTag("CachingStringNullableList_GetByTag2", tag)

  private suspend fun getStringNullableListsByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.StringNullableListGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.StringNullableListGetMany>(),
        serializer()
      )
    return queryRef.execute()
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

  suspend fun getNullableStringNullableList(key: Key) =
    getNullableStringNullableList("CachingNullableStringNullableList_GetByKey", key)

  suspend fun getNullableStringNullableList2(key: Key) =
    getNullableStringNullableList("CachingNullableStringNullableList_GetByKey2", key)

  private suspend fun getNullableStringNullableList(
    operationName: String,
    key: Key
  ): QueryResult<Data.NullableStringNullableListGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringNullableListGet>(),
        serializer()
      )
    return queryRef.execute()
  }

  suspend fun getNullableStringNullableListsByTag(tag: String) =
    getNullableStringNullableListsByTag("CachingNullableStringNullableList_GetByTag", tag)

  suspend fun getNullableStringNullableListsByTag2(tag: String) =
    getNullableStringNullableListsByTag("CachingNullableStringNullableList_GetByTag2", tag)

  private suspend fun getNullableStringNullableListsByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.NullableStringNullableListGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableStringNullableListGetMany>(),
        serializer()
      )
    return queryRef.execute()
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

  suspend fun getFloat(key: Key) = getFloat("CachingFloat_GetByKey", key)

  suspend fun getFloat2(key: Key) = getFloat("CachingFloat_GetByKey2", key)

  private suspend fun getFloat(
    operationName: String,
    key: Key
  ): QueryResult<Data.FloatGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.FloatGet>(), serializer())
    return queryRef.execute()
  }

  suspend fun getFloatsByTag(tag: String) = getFloatsByTag("CachingFloat_GetByTag", tag)

  suspend fun getFloatsByTag2(tag: String) = getFloatsByTag("CachingFloat_GetByTag2", tag)

  private suspend fun getFloatsByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.FloatGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.FloatGetMany>(), serializer())
    return queryRef.execute()
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

  suspend fun getNullableFloat(key: Key) = getNullableFloat("CachingNullableFloat_GetByKey", key)

  suspend fun getNullableFloat2(key: Key) = getNullableFloat("CachingNullableFloat_GetByKey2", key)

  private suspend fun getNullableFloat(
    operationName: String,
    key: Key
  ): QueryResult<Data.NullableFloatGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.NullableFloatGet>(), serializer())
    return queryRef.execute()
  }

  suspend fun getNullableFloatsByTag(tag: String) =
    getNullableFloatsByTag("CachingNullableFloat_GetByTag", tag)

  suspend fun getNullableFloatsByTag2(tag: String) =
    getNullableFloatsByTag("CachingNullableFloat_GetByTag2", tag)

  private suspend fun getNullableFloatsByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.NullableFloatGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableFloatGetMany>(),
        serializer()
      )
    return queryRef.execute()
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

  suspend fun getBoolean(key: Key) = getBoolean("CachingBoolean_GetByKey", key)

  suspend fun getBoolean2(key: Key) = getBoolean("CachingBoolean_GetByKey2", key)

  private suspend fun getBoolean(
    operationName: String,
    key: Key
  ): QueryResult<Data.BooleanGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.BooleanGet>(), serializer())
    return queryRef.execute()
  }

  suspend fun getBooleansByTag(tag: String) = getBooleansByTag("CachingBoolean_GetByTag", tag)

  suspend fun getBooleansByTag2(tag: String) = getBooleansByTag("CachingBoolean_GetByTag2", tag)

  private suspend fun getBooleansByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.BooleanGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.BooleanGetMany>(), serializer())
    return queryRef.execute()
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

  suspend fun getNullableBoolean(key: Key) =
    getNullableBoolean("CachingNullableBoolean_GetByKey", key)

  suspend fun getNullableBoolean2(key: Key) =
    getNullableBoolean("CachingNullableBoolean_GetByKey2", key)

  private suspend fun getNullableBoolean(
    operationName: String,
    key: Key
  ): QueryResult<Data.NullableBooleanGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableBooleanGet>(),
        serializer()
      )
    return queryRef.execute()
  }

  suspend fun getNullableBooleansByTag(tag: String) =
    getNullableBooleansByTag("CachingNullableBoolean_GetByTag", tag)

  suspend fun getNullableBooleansByTag2(tag: String) =
    getNullableBooleansByTag("CachingNullableBoolean_GetByTag2", tag)

  private suspend fun getNullableBooleansByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.NullableBooleanGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableBooleanGetMany>(),
        serializer()
      )
    return queryRef.execute()
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

  suspend fun getAnyValue(key: Key) = getAnyValue("CachingAny_GetByKey", key)

  suspend fun getAnyValue2(key: Key) = getAnyValue("CachingAny_GetByKey2", key)

  private suspend fun getAnyValue(
    operationName: String,
    key: Key
  ): QueryResult<Data.AnyValueGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.AnyValueGet>(), serializer())
    return queryRef.execute()
  }

  suspend fun getAnyValuesByTag(tag: String) = getAnyValuesByTag("CachingAny_GetByTag", tag)

  suspend fun getAnyValuesByTag2(tag: String) = getAnyValuesByTag("CachingAny_GetByTag2", tag)

  private suspend fun getAnyValuesByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.AnyValueGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(operationName, variables, serializer<Data.AnyValueGetMany>(), serializer())
    return queryRef.execute()
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

  suspend fun getNullableAnyValue(key: Key) =
    getNullableAnyValue("CachingNullableAny_GetByKey", key)

  suspend fun getNullableAnyValue2(key: Key) =
    getNullableAnyValue("CachingNullableAny_GetByKey2", key)

  private suspend fun getNullableAnyValue(
    operationName: String,
    key: Key
  ): QueryResult<Data.NullableAnyValueGet, Variables.GetByKey> {
    val variables = Variables.GetByKey(key)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableAnyValueGet>(),
        serializer()
      )
    return queryRef.execute()
  }

  suspend fun getNullableAnyValuesByTag(tag: String) =
    getNullableAnyValuesByTag("CachingNullableAny_GetByTag", tag)

  suspend fun getNullableAnyValuesByTag2(tag: String) =
    getNullableAnyValuesByTag("CachingNullableAny_GetByTag2", tag)

  private suspend fun getNullableAnyValuesByTag(
    operationName: String,
    tag: String
  ): QueryResult<Data.NullableAnyValueGetMany, Variables.GetByTag> {
    val variables = Variables.GetByTag(tag)
    val queryRef =
      dataConnect.query(
        operationName,
        variables,
        serializer<Data.NullableAnyValueGetMany>(),
        serializer()
      )
    return queryRef.execute()
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
  }

  @Serializable data class Key(val id: @Serializable(with = UUIDSerializer::class) UUID)

  companion object {
    const val CONNECTOR_NAME = "caching"
  }
}

@JvmName("QueryResult_StringGet_shouldBe")
fun QueryResult<CachingConnector.Data.StringGet, CachingConnector.Variables.GetByKey>.shouldBe(
  string: String,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().string shouldBe string
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.StringGetMany, CachingConnector.Variables.GetByTag>.shouldBe(
  strings: Collection<String>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.string } shouldContainExactlyInAnyOrder strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringGet, CachingConnector.Variables.GetByKey>
  .shouldBe(string: String?, dataSource: DataSource) {
  assertSoftly {
    this.data.item.shouldNotBeNull().string shouldBe string
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringGetMany, CachingConnector.Variables.GetByTag>
  .shouldBe(strings: Collection<String?>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.string } shouldContainExactlyInAnyOrder strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringListGet_shouldBe")
fun QueryResult<CachingConnector.Data.StringListGet, CachingConnector.Variables.GetByKey>.shouldBe(
  strings: List<String>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().strings shouldBe strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringListGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.StringListGetMany, CachingConnector.Variables.GetByTag>
  .shouldBe(stringLists: Collection<List<String>>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.strings } shouldContainExactlyInAnyOrder stringLists
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringListGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableStringListGet, CachingConnector.Variables.GetByKey>
  .shouldBe(strings: List<String?>, dataSource: DataSource) {
  assertSoftly {
    this.data.item.shouldNotBeNull().strings shouldBe strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringListGetMany_shouldBe")
fun QueryResult<
  CachingConnector.Data.NullableStringListGetMany, CachingConnector.Variables.GetByTag
>
  .shouldBe(stringLists: Collection<List<String?>>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.strings } shouldContainExactlyInAnyOrder stringLists
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringNullableListGet_shouldBe")
fun QueryResult<CachingConnector.Data.StringNullableListGet, CachingConnector.Variables.GetByKey>
  .shouldBe(strings: List<String>?, dataSource: DataSource) {
  assertSoftly {
    this.data.item.shouldNotBeNull().strings shouldBe strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_StringNullableListGetMany_shouldBe")
fun QueryResult<
  CachingConnector.Data.StringNullableListGetMany, CachingConnector.Variables.GetByTag
>
  .shouldBe(stringLists: Collection<List<String>?>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.strings } shouldContainExactlyInAnyOrder stringLists
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringNullableListGet_shouldBe")
fun QueryResult<
  CachingConnector.Data.NullableStringNullableListGet, CachingConnector.Variables.GetByKey
>
  .shouldBe(strings: List<String?>?, dataSource: DataSource) {
  assertSoftly {
    this.data.item.shouldNotBeNull().strings shouldBe strings
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableStringNullableListGetMany_shouldBe")
fun QueryResult<
  CachingConnector.Data.NullableStringNullableListGetMany, CachingConnector.Variables.GetByTag
>
  .shouldBe(stringLists: Collection<List<String?>?>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.strings } shouldContainExactlyInAnyOrder stringLists
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_FloatGet_shouldBe")
fun QueryResult<CachingConnector.Data.FloatGet, CachingConnector.Variables.GetByKey>.shouldBe(
  float: Double,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().float shouldBe float
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_FloatGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.FloatGetMany, CachingConnector.Variables.GetByTag>.shouldBe(
  floats: Collection<Double>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.float } shouldContainExactlyInAnyOrder floats
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableFloatGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableFloatGet, CachingConnector.Variables.GetByKey>
  .shouldBe(float: Double?, dataSource: DataSource) {
  assertSoftly {
    this.data.item.shouldNotBeNull().float shouldBe float
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableFloatGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableFloatGetMany, CachingConnector.Variables.GetByTag>
  .shouldBe(floats: Collection<Double?>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.float } shouldContainExactlyInAnyOrder floats
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_BooleanGet_shouldBe")
fun QueryResult<CachingConnector.Data.BooleanGet, CachingConnector.Variables.GetByKey>.shouldBe(
  boolean: Boolean,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().boolean shouldBe boolean
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_BooleanGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.BooleanGetMany, CachingConnector.Variables.GetByTag>.shouldBe(
  booleans: Collection<Boolean>,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.items.map { it.boolean } shouldContainExactlyInAnyOrder booleans
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableBooleanGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableBooleanGet, CachingConnector.Variables.GetByKey>
  .shouldBe(boolean: Boolean?, dataSource: DataSource) {
  assertSoftly {
    this.data.item.shouldNotBeNull().boolean shouldBe boolean
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableBooleanGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableBooleanGetMany, CachingConnector.Variables.GetByTag>
  .shouldBe(booleans: Collection<Boolean?>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.boolean } shouldContainExactlyInAnyOrder booleans
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_AnyValueGet_shouldBe")
fun QueryResult<CachingConnector.Data.AnyValueGet, CachingConnector.Variables.GetByKey>.shouldBe(
  any: AnyValue,
  dataSource: DataSource
) {
  assertSoftly {
    this.data.item.shouldNotBeNull().any shouldBe any
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_AnyValueGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.AnyValueGetMany, CachingConnector.Variables.GetByTag>
  .shouldBe(anys: Collection<AnyValue>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.any } shouldContainExactlyInAnyOrder anys
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableAnyValueGet_shouldBe")
fun QueryResult<CachingConnector.Data.NullableAnyValueGet, CachingConnector.Variables.GetByKey>
  .shouldBe(any: AnyValue?, dataSource: DataSource) {
  assertSoftly {
    this.data.item.shouldNotBeNull().any shouldBe any
    this.dataSource shouldBe dataSource
  }
}

@JvmName("QueryResult_NullableAnyValueGetMany_shouldBe")
fun QueryResult<CachingConnector.Data.NullableAnyValueGetMany, CachingConnector.Variables.GetByTag>
  .shouldBe(anys: Collection<AnyValue?>, dataSource: DataSource) {
  assertSoftly {
    this.data.items.map { it.any } shouldContainExactlyInAnyOrder anys
    this.dataSource shouldBe dataSource
  }
}

suspend fun CachingConnector.verifyGetString(
  key: CachingConnector.Key,
  clue: String,
  expectedString: String,
  expectedDataSource: DataSource
) {
  withClue(clue) { getString(key).shouldBe(expectedString, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetString2(
  key: CachingConnector.Key,
  clue: String,
  expectedString: String,
  expectedDataSource: DataSource
) {
  withClue(clue) { getString2(key).shouldBe(expectedString, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetStringsByTag(
  tag: String,
  clue: String,
  expectedString: String,
  expectedDataSource: DataSource
) {
  withClue(clue) { getStringsByTag(tag).shouldBe(listOf(expectedString), expectedDataSource) }
}

suspend fun CachingConnector.verifyGetStringsByTag2(
  tag: String,
  clue: String,
  expectedString: String,
  expectedDataSource: DataSource
) {
  verifyGetStringsByTag2(tag, clue, listOf(expectedString), expectedDataSource)
}

suspend fun CachingConnector.verifyGetStringsByTag2(
  tag: String,
  clue: String,
  expectedStrings: Collection<String>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getStringsByTag2(tag).shouldBe(expectedStrings, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableString(
  key: CachingConnector.Key,
  clue: String,
  expectedString: String?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableString(key).shouldBe(expectedString, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableString2(
  key: CachingConnector.Key,
  clue: String,
  expectedString: String?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableString2(key).shouldBe(expectedString, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableStringsByTag(
  tag: String,
  clue: String,
  expectedString: String?,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableStringsByTag(tag).shouldBe(listOf(expectedString), expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetNullableStringsByTag2(
  tag: String,
  clue: String,
  expectedString: String?,
  expectedDataSource: DataSource
) {
  verifyGetNullableStringsByTag2(tag, clue, listOf(expectedString), expectedDataSource)
}

suspend fun CachingConnector.verifyGetNullableStringsByTag2(
  tag: String,
  clue: String,
  expectedStrings: Collection<String?>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableStringsByTag2(tag).shouldBe(expectedStrings, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetStringList(
  key: CachingConnector.Key,
  clue: String,
  expectedStringList: List<String>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getStringList(key).shouldBe(expectedStringList, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetStringList2(
  key: CachingConnector.Key,
  clue: String,
  expectedStringList: List<String>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getStringList2(key).shouldBe(expectedStringList, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetStringListsByTag(
  tag: String,
  clue: String,
  expectedStringList: List<String>,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getStringListsByTag(tag).shouldBe(listOf(expectedStringList), expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetStringListsByTag2(
  tag: String,
  clue: String,
  expectedStringList: List<String>,
  expectedDataSource: DataSource
) {
  verifyGetStringListsByTag2(tag, clue, listOf(expectedStringList), expectedDataSource)
}

suspend fun CachingConnector.verifyGetStringListsByTag2(
  tag: String,
  clue: String,
  expectedStringLists: Collection<List<String>>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getStringListsByTag2(tag).shouldBe(expectedStringLists, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableStringList(
  key: CachingConnector.Key,
  clue: String,
  expectedStringList: List<String?>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableStringList(key).shouldBe(expectedStringList, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableStringList2(
  key: CachingConnector.Key,
  clue: String,
  expectedStringList: List<String?>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableStringList2(key).shouldBe(expectedStringList, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableStringListsByTag(
  tag: String,
  clue: String,
  expectedStringList: List<String?>,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableStringListsByTag(tag).shouldBe(listOf(expectedStringList), expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetNullableStringListsByTag2(
  tag: String,
  clue: String,
  expectedStringList: List<String?>,
  expectedDataSource: DataSource
) {
  verifyGetNullableStringListsByTag2(tag, clue, listOf(expectedStringList), expectedDataSource)
}

suspend fun CachingConnector.verifyGetNullableStringListsByTag2(
  tag: String,
  clue: String,
  expectedStringLists: Collection<List<String?>>,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableStringListsByTag2(tag).shouldBe(expectedStringLists, expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetStringNullableList(
  key: CachingConnector.Key,
  clue: String,
  expectedStringList: List<String>?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getStringNullableList(key).shouldBe(expectedStringList, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetStringNullableList2(
  key: CachingConnector.Key,
  clue: String,
  expectedStringList: List<String>?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getStringNullableList2(key).shouldBe(expectedStringList, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetStringNullableListsByTag(
  tag: String,
  clue: String,
  expectedStringList: List<String>?,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getStringNullableListsByTag(tag).shouldBe(listOf(expectedStringList), expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetStringNullableListsByTag2(
  tag: String,
  clue: String,
  expectedStringList: List<String>?,
  expectedDataSource: DataSource
) {
  verifyGetStringNullableListsByTag2(tag, clue, listOf(expectedStringList), expectedDataSource)
}

suspend fun CachingConnector.verifyGetStringNullableListsByTag2(
  tag: String,
  clue: String,
  expectedStringLists: Collection<List<String>?>,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getStringNullableListsByTag2(tag).shouldBe(expectedStringLists, expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetNullableStringNullableList(
  key: CachingConnector.Key,
  clue: String,
  expectedNullableStringNullableList: List<String?>?,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableStringNullableList(key)
      .shouldBe(expectedNullableStringNullableList, expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetNullableStringNullableList2(
  key: CachingConnector.Key,
  clue: String,
  expectedNullableStringNullableList: List<String?>?,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableStringNullableList2(key)
      .shouldBe(expectedNullableStringNullableList, expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetNullableStringNullableListsByTag(
  tag: String,
  clue: String,
  expectedNullableStringNullableList: List<String?>?,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableStringNullableListsByTag(tag)
      .shouldBe(listOf(expectedNullableStringNullableList), expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetNullableStringNullableListsByTag2(
  tag: String,
  clue: String,
  expectedNullableStringNullableList: List<String?>?,
  expectedDataSource: DataSource
) {
  verifyGetNullableStringNullableListsByTag2(
    tag,
    clue,
    listOf(expectedNullableStringNullableList),
    expectedDataSource
  )
}

suspend fun CachingConnector.verifyGetNullableStringNullableListsByTag2(
  tag: String,
  clue: String,
  expectedNullableStringNullableLists: Collection<List<String?>?>,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableStringNullableListsByTag2(tag)
      .shouldBe(expectedNullableStringNullableLists, expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetFloat(
  key: CachingConnector.Key,
  clue: String,
  expectedFloat: Double,
  expectedDataSource: DataSource
) {
  withClue(clue) { getFloat(key).shouldBe(expectedFloat, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetFloat2(
  key: CachingConnector.Key,
  clue: String,
  expectedFloat: Double,
  expectedDataSource: DataSource
) {
  withClue(clue) { getFloat2(key).shouldBe(expectedFloat, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetFloatsByTag(
  tag: String,
  clue: String,
  expectedFloat: Double,
  expectedDataSource: DataSource
) {
  withClue(clue) { getFloatsByTag(tag).shouldBe(listOf(expectedFloat), expectedDataSource) }
}

suspend fun CachingConnector.verifyGetFloatsByTag2(
  tag: String,
  clue: String,
  expectedFloat: Double,
  expectedDataSource: DataSource
) {
  verifyGetFloatsByTag2(tag, clue, listOf(expectedFloat), expectedDataSource)
}

suspend fun CachingConnector.verifyGetFloatsByTag2(
  tag: String,
  clue: String,
  expectedFloats: Collection<Double>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getFloatsByTag2(tag).shouldBe(expectedFloats, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableFloat(
  key: CachingConnector.Key,
  clue: String,
  expectedFloat: Double?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableFloat(key).shouldBe(expectedFloat, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableFloat2(
  key: CachingConnector.Key,
  clue: String,
  expectedFloat: Double?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableFloat2(key).shouldBe(expectedFloat, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableFloatsByTag(
  tag: String,
  clue: String,
  expectedFloat: Double?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableFloatsByTag(tag).shouldBe(listOf(expectedFloat), expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableFloatsByTag2(
  tag: String,
  clue: String,
  expectedFloat: Double?,
  expectedDataSource: DataSource
) {
  verifyGetNullableFloatsByTag2(tag, clue, listOf(expectedFloat), expectedDataSource)
}

suspend fun CachingConnector.verifyGetNullableFloatsByTag2(
  tag: String,
  clue: String,
  expectedFloats: Collection<Double?>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableFloatsByTag2(tag).shouldBe(expectedFloats, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetBoolean(
  key: CachingConnector.Key,
  clue: String,
  expectedBoolean: Boolean,
  expectedDataSource: DataSource
) {
  withClue(clue) { getBoolean(key).shouldBe(expectedBoolean, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetBoolean2(
  key: CachingConnector.Key,
  clue: String,
  expectedBoolean: Boolean,
  expectedDataSource: DataSource
) {
  withClue(clue) { getBoolean2(key).shouldBe(expectedBoolean, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetBooleansByTag(
  tag: String,
  clue: String,
  expectedBoolean: Boolean,
  expectedDataSource: DataSource
) {
  withClue(clue) { getBooleansByTag(tag).shouldBe(listOf(expectedBoolean), expectedDataSource) }
}

suspend fun CachingConnector.verifyGetBooleansByTag2(
  tag: String,
  clue: String,
  expectedBoolean: Boolean,
  expectedDataSource: DataSource
) {
  verifyGetBooleansByTag2(tag, clue, listOf(expectedBoolean), expectedDataSource)
}

suspend fun CachingConnector.verifyGetBooleansByTag2(
  tag: String,
  clue: String,
  expectedBooleans: Collection<Boolean>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getBooleansByTag2(tag).shouldBe(expectedBooleans, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableBoolean(
  key: CachingConnector.Key,
  clue: String,
  expectedBoolean: Boolean?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableBoolean(key).shouldBe(expectedBoolean, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableBoolean2(
  key: CachingConnector.Key,
  clue: String,
  expectedBoolean: Boolean?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableBoolean2(key).shouldBe(expectedBoolean, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableBooleansByTag(
  tag: String,
  clue: String,
  expectedBoolean: Boolean?,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableBooleansByTag(tag).shouldBe(listOf(expectedBoolean), expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetNullableBooleansByTag2(
  tag: String,
  clue: String,
  expectedBoolean: Boolean?,
  expectedDataSource: DataSource
) {
  verifyGetNullableBooleansByTag2(tag, clue, listOf(expectedBoolean), expectedDataSource)
}

suspend fun CachingConnector.verifyGetNullableBooleansByTag2(
  tag: String,
  clue: String,
  expectedBooleans: Collection<Boolean?>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableBooleansByTag2(tag).shouldBe(expectedBooleans, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetAnyValue(
  key: CachingConnector.Key,
  clue: String,
  expectedAnyValue: AnyValue,
  expectedDataSource: DataSource
) {
  withClue(clue) { getAnyValue(key).shouldBe(expectedAnyValue, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetAnyValue2(
  key: CachingConnector.Key,
  clue: String,
  expectedAnyValue: AnyValue,
  expectedDataSource: DataSource
) {
  withClue(clue) { getAnyValue2(key).shouldBe(expectedAnyValue, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetAnyValuesByTag(
  tag: String,
  clue: String,
  expectedAnyValue: AnyValue,
  expectedDataSource: DataSource
) {
  withClue(clue) { getAnyValuesByTag(tag).shouldBe(listOf(expectedAnyValue), expectedDataSource) }
}

suspend fun CachingConnector.verifyGetAnyValuesByTag2(
  tag: String,
  clue: String,
  expectedAnyValue: AnyValue,
  expectedDataSource: DataSource
) {
  verifyGetAnyValuesByTag2(tag, clue, listOf(expectedAnyValue), expectedDataSource)
}

suspend fun CachingConnector.verifyGetAnyValuesByTag2(
  tag: String,
  clue: String,
  expectedAnyValues: Collection<AnyValue>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getAnyValuesByTag2(tag).shouldBe(expectedAnyValues, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableAnyValue(
  key: CachingConnector.Key,
  clue: String,
  expectedAnyValue: AnyValue?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableAnyValue(key).shouldBe(expectedAnyValue, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableAnyValue2(
  key: CachingConnector.Key,
  clue: String,
  expectedAnyValue: AnyValue?,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableAnyValue2(key).shouldBe(expectedAnyValue, expectedDataSource) }
}

suspend fun CachingConnector.verifyGetNullableAnyValuesByTag(
  tag: String,
  clue: String,
  expectedAnyValue: AnyValue?,
  expectedDataSource: DataSource
) {
  withClue(clue) {
    getNullableAnyValuesByTag(tag).shouldBe(listOf(expectedAnyValue), expectedDataSource)
  }
}

suspend fun CachingConnector.verifyGetNullableAnyValuesByTag2(
  tag: String,
  clue: String,
  expectedAnyValue: AnyValue?,
  expectedDataSource: DataSource
) {
  verifyGetNullableAnyValuesByTag2(tag, clue, listOf(expectedAnyValue), expectedDataSource)
}

suspend fun CachingConnector.verifyGetNullableAnyValuesByTag2(
  tag: String,
  clue: String,
  expectedAnyValues: Collection<AnyValue?>,
  expectedDataSource: DataSource
) {
  withClue(clue) { getNullableAnyValuesByTag2(tag).shouldBe(expectedAnyValues, expectedDataSource) }
}
