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

  object Variables {

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

    @Serializable data class GetByKey(val key: Key)

    @Serializable data class GetByTag(val tag: String)

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
