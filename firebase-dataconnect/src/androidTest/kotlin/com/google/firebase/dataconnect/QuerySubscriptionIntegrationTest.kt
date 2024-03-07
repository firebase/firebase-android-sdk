// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.LazyPersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery
import com.google.firebase.dataconnect.testutil.skipItemsWhere
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuerySubscriptionIntegrationTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val schema: PersonSchema by LazyPersonSchema(dataConnectFactory)

  @Test
  fun lastResult_should_be_null_on_new_instance() {
    val querySubscription = schema.getPerson(id = "42").subscribe()
    assertThat(querySubscription.lastResult).isNull()
  }

  @Test
  fun lastResult_should_be_equal_to_the_last_collected_result() = runTest {
    schema.createPerson(id = "TestId", name = "Name1").execute()
    val querySubscription = schema.getPerson(id = "TestId").subscribe()

    querySubscription.resultFlow.test {
      val result1A = awaitItem()
      assertWithMessage("result1A.name").that(result1A.data.person?.name).isEqualTo("Name1")
      assertWithMessage("lastResult1").that(querySubscription.lastResult).isEqualTo(result1A)
    }

    schema.updatePerson(id = "TestId", name = "Name2", age = 2).execute()

    querySubscription.resultFlow.test {
      val result1B = awaitItem()
      assertWithMessage("result1B").that(result1B).isEqualTo(querySubscription.lastResult)
      val result2 = awaitItem()
      assertWithMessage("result2.name").that(result2.data.person?.name).isEqualTo("Name2")
      assertWithMessage("lastResult2").that(querySubscription.lastResult).isEqualTo(result2)
    }
  }

  @Test
  fun reload_should_notify_collecting_flows() = runTest {
    schema.createPerson(id = "TestId", name = "Name1").execute()
    val querySubscription = schema.getPerson(id = "TestId").subscribe()

    querySubscription.resultFlow.test {
      assertWithMessage("result1").that(awaitItem().data.person?.name).isEqualTo("Name1")

      schema.updatePerson(id = "TestId", name = "Name2").execute()
      querySubscription.reload()

      assertWithMessage("result2").that(awaitItem().data.person?.name).isEqualTo("Name2")
    }
  }

  @Test
  fun flow_collect_should_get_immediately_invoked_with_last_result() = runTest {
    schema.createPerson(id = "TestId", name = "TestName").execute()
    val querySubscription = schema.getPerson(id = "TestId").subscribe()

    val result1 = querySubscription.resultFlow.first()
    assertWithMessage("result1").that(result1.data.person?.name).isEqualTo("TestName")

    val result2 = querySubscription.resultFlow.first()
    assertWithMessage("result2").that(result2.data.person?.name).isEqualTo("TestName")
  }

  @Test
  fun flow_collect_should_get_immediately_invoked_with_last_result_from_other_subscribers() =
    runTest {
      schema.createPerson(id = "TestId", name = "TestName").execute()
      val querySubscription1 = schema.getPerson(id = "TestId").subscribe()
      val querySubscription2 = schema.getPerson(id = "TestId").subscribe()

      // Start collecting on `querySubscription1` and wait for it to get its first event.
      val subscription1ResultReceived = MutableStateFlow(false)
      backgroundScope.launch {
        querySubscription1.resultFlow.onEach { subscription1ResultReceived.value = true }.collect()
      }
      subscription1ResultReceived.filter { it }.first()

      // With `querySubscription1` still alive, start collecting on `querySubscription2`. Expect it
      // to initially get the cached result from `querySubscription1`, followed by an updated
      // result.
      schema.updatePerson(id = "TestId", name = "NewTestName").execute()
      querySubscription2.resultFlow.test {
        assertWithMessage("result1").that(awaitItem().data.person?.name).isEqualTo("TestName")
        assertWithMessage("result1").that(awaitItem().data.person?.name).isEqualTo("NewTestName")
      }
    }

  @Test
  fun slow_flows_do_not_block_fast_flows() = runTest {
    schema.createPerson(id = "TestId", name = "Name0").execute()
    val querySubscription = schema.getPerson(id = "TestId").subscribe()

    turbineScope {
      val fastFlow = querySubscription.resultFlow.testIn(backgroundScope)
      assertWithMessage("fastFlow").that(fastFlow.awaitItem().data.person?.name).isEqualTo("Name0")

      val slowFlowGate = MutableStateFlow(false)
      val slowFlow =
        querySubscription.resultFlow
          .onEach { slowFlowGate.filter { it }.first() }
          .testIn(backgroundScope)
      assertWithMessage("fastFlow").that(fastFlow.awaitItem().data.person?.name).isEqualTo("Name0")

      repeat(3) {
        schema.updatePerson(id = "TestId", name = "NewName$it").execute()
        querySubscription.reload()
      }

      fastFlow.run {
        assertWithMessage("fastFlow").that(awaitItem().data.person?.name).isEqualTo("NewName0")
        assertWithMessage("fastFlow").that(awaitItem().data.person?.name).isEqualTo("NewName1")
        assertWithMessage("fastFlow").that(awaitItem().data.person?.name).isEqualTo("NewName2")
      }

      slowFlowGate.value = true
      slowFlow.run {
        assertWithMessage("slowFlow").that(awaitItem().data.person?.name).isEqualTo("Name0")
        assertWithMessage("slowFlow").that(awaitItem().data.person?.name).isEqualTo("Name0")
        assertWithMessage("slowFlow").that(awaitItem().data.person?.name).isEqualTo("NewName0")
        assertWithMessage("slowFlow").that(awaitItem().data.person?.name).isEqualTo("NewName1")
        assertWithMessage("slowFlow").that(awaitItem().data.person?.name).isEqualTo("NewName2")
      }
    }
  }

  @Test
  fun reload_delivers_result_to_all_registered_flows_on_all_QuerySubscriptions() = runTest {
    schema.createPerson(id = "TestId", name = "OriginalName").execute()
    val querySubscription1 = schema.getPerson(id = "TestId").subscribe()
    val querySubscription2 = schema.getPerson(id = "TestId").subscribe()

    turbineScope {
      val flow1a = querySubscription1.resultFlow.testIn(backgroundScope).apply { skipItems(1) }
      val flow1b = querySubscription1.resultFlow.testIn(backgroundScope).apply { skipItems(2) }
      val flow2 = querySubscription2.resultFlow.testIn(backgroundScope).apply { skipItems(2) }
      flow1a.skipItems(2)
      flow1b.skipItems(1)

      schema.updatePerson(id = "TestId", name = "NewName").execute()
      querySubscription1.reload()

      assertWithMessage("flow1a").that(flow1a.awaitItem().data.person?.name).isEqualTo("NewName")
      assertWithMessage("flow1b").that(flow1b.awaitItem().data.person?.name).isEqualTo("NewName")
      assertWithMessage("flow2").that(flow2.awaitItem().data.person?.name).isEqualTo("NewName")
    }
  }

  @Test
  fun queryref_execute_delivers_result_to_QuerySubscriptions() = runTest {
    schema.createPerson(id = "TestId", name = "OriginalName").execute()
    val querySubscription1 = schema.getPerson(id = "TestId").subscribe()
    val querySubscription2 = schema.getPerson(id = "TestId").subscribe()

    turbineScope {
      val flow1a = querySubscription1.resultFlow.testIn(backgroundScope).apply { skipItems(1) }
      val flow1b = querySubscription1.resultFlow.testIn(backgroundScope).apply { skipItems(2) }
      val flow2 = querySubscription2.resultFlow.testIn(backgroundScope).apply { skipItems(2) }
      flow1a.skipItems(2)
      flow1b.skipItems(1)

      schema.updatePerson(id = "TestId", name = "NewName").execute()
      schema.getPerson(id = "TestId").execute()

      assertWithMessage("flow1a").that(flow1a.awaitItem().data.person?.name).isEqualTo("NewName")
      assertWithMessage("flow1b").that(flow1b.awaitItem().data.person?.name).isEqualTo("NewName")
      assertWithMessage("flow2").that(flow2.awaitItem().data.person?.name).isEqualTo("NewName")
    }
  }

  @Test
  fun reload_concurrent_invocations_get_conflated() =
    runTest(timeout = 60.seconds) {
      schema.createPerson(id = "TestId", name = "OriginalName").execute()
      val querySubscription = schema.getPerson(id = "TestId").subscribe()

      querySubscription.resultFlow.test {
        assertThat(awaitItem().data.person?.name).isEqualTo("OriginalName")
        schema.updatePerson(id = "TestId", name = "NewName").execute()

        buildList {
            repeat(25_000) {
              // Run on Dispatchers.Default to ensure some level of concurrency.
              add(backgroundScope.async(Dispatchers.Default) { querySubscription.reload() })
            }
          }
          .forEach { it.await() }

        // Flow on Dispatchers.Default so that the timeout actually works, since the default
        // dispatcher is the _test_ dispatcher, which skips delays/timeouts.
        val results =
          asChannel()
            .receiveAsFlow()
            .timeout(1.seconds)
            .flowOn(Dispatchers.Default)
            .catch { if (it !is TimeoutCancellationException) throw it }
            .toList()
        assertWithMessage("results.size").that(results.size).isGreaterThan(0)
        assertWithMessage("results.size").that(results.size).isLessThan(1000)
        results.forEachIndexed { i, result ->
          assertWithMessage("results[$i]").that(result.data.person?.name).isEqualTo("NewName")
        }
      }
    }

  @Test
  fun update_changes_variables_and_triggers_reload() = runTest {
    schema.createPerson(id = "TestId1", name = "Name1").execute()
    schema.createPerson(id = "TestId2", name = "Name2").execute()
    schema.createPerson(id = "TestId3", name = "Name3").execute()
    val query = schema.getPerson(id = "TestId1")
    val querySubscription = query.subscribe()

    querySubscription.resultFlow.test {
      Pair(assertWithMessage("result1"), awaitItem()).let { (assert, result) ->
        assert.that(result.ref).isSameInstanceAs(query)
        assert.that(result.data.person?.name).isEqualTo("Name1")
      }
      querySubscription.update(GetPersonQuery.Variables("TestId2"))
      Pair(assertWithMessage("result2"), awaitItem()).let { (assert, result) ->
        assert.that(result.ref.variables).isEqualTo(GetPersonQuery.Variables("TestId2"))
        assert.that(result.data.person?.name).isEqualTo("Name2")
      }
      querySubscription.update(GetPersonQuery.Variables("TestId3"))
      Pair(assertWithMessage("result3"), awaitItem()).let { (assert, result) ->
        assert.that(result.ref.variables).isEqualTo(GetPersonQuery.Variables("TestId3"))
        assert.that(result.data.person?.name).isEqualTo("Name3")
      }
    }
  }

  @Test
  fun reload_updates_last_result_even_if_no_active_collectors() = runTest {
    schema.createPerson(id = "TestId", name = "Name1").execute()
    val querySubscription = schema.getPerson(id = "TestId").subscribe()

    querySubscription.reload()

    Pair(assertWithMessage("lastResult"), querySubscription.lastResult).let { (assert, lastResult)
      ->
      assert.that(lastResult).isNotNull()
      assert.that(lastResult!!.data.person?.name).isEqualTo("Name1")
    }

    schema.updatePerson(id = "TestId", name = "Name2").execute()
    querySubscription.resultFlow.test {
      // Ensure that the first result comes from cache, followed by the updated result received from
      // the server when a reload was triggered by the flow's collection.
      assertThat(awaitItem().data.person?.name).isEqualTo("Name1")
      assertThat(awaitItem().data.person?.name).isEqualTo("Name2")
    }
  }

  @Test
  fun update_updates_last_result_even_if_no_active_collectors() = runTest {
    schema.createPerson(id = "TestId1", name = "Name1").execute()
    schema.createPerson(id = "TestId2", name = "Name2").execute()
    val querySubscription = schema.getPerson(id = "TestId1").subscribe()

    querySubscription.update(GetPersonQuery.Variables("TestId2"))

    Pair(assertWithMessage("lastResult"), querySubscription.lastResult).let { (assert, lastResult)
      ->
      assert.that(lastResult).isNotNull()
      assert.that(lastResult!!.data.person?.name).isEqualTo("Name2")
    }

    schema.updatePerson(id = "TestId2", name = "NewName2").execute()
    querySubscription.resultFlow.test {
      // Ensure that the first result comes from cache, followed by the updated result received from
      // the server when a reload was triggered by the flow's collection.
      assertThat(awaitItem().data.person?.name).isEqualTo("Name2")
      assertThat(awaitItem().data.person?.name).isEqualTo("NewName2")
    }
  }

  @Test
  fun collect_does_not_get_an_update_on_errors() = runTest {
    schema.createPerson(id = "TestId", name = "Name1").execute()
    val query = schema.getPerson("TestId")
    val noName2Query = query.withDataDeserializer(serializer<GetPersonDataNoName2>())

    turbineScope {
      val querySubscription = noName2Query.subscribe()
      val flow = querySubscription.resultFlow.testIn(backgroundScope)
      assertThat(flow.awaitItem().data.person?.name).isEqualTo("Name1")

      schema.updatePerson(id = "TestId", name = "Name2").execute()
      val result2 = querySubscription.runCatching { reload() }
      assertWithMessage("result2.isSuccess").that(result2.isSuccess).isFalse()

      schema.updatePerson(id = "TestId", name = "Name3").execute()
      querySubscription.reload()
      assertThat(flow.awaitItem().data.person?.name).isEqualTo("Name3")
    }
  }

  @Test
  fun collect_gets_notified_of_per_data_deserializer_successes() = runTest {
    schema.createPerson(id = "TestId", name = "Name0").execute()

    val noName1Query =
      schema.getPerson("TestId").withDataDeserializer(serializer<GetPersonDataNoName1>())
    val noName2Query =
      schema.getPerson("TestId").withDataDeserializer(serializer<GetPersonDataNoName2>())

    turbineScope {
      val flow1 = noName1Query.subscribe().resultFlow.testIn(backgroundScope)
      assertThat(flow1.awaitItem().data.person?.name).isEqualTo("Name0")
      val flow2 = noName2Query.subscribe().resultFlow.testIn(backgroundScope)
      assertThat(flow1.awaitItem().data.person?.name).isEqualTo("Name0")

      schema.updatePerson(id = "TestId", name = "Name1").execute()
      schema.getPerson("TestId").execute()
      flow2
        .skipItemsWhere { it.data.person?.name == "Name0" }
        .let { assertThat(it.data.person?.name).isEqualTo("Name1") }

      schema.updatePerson(id = "TestId", name = "Name2").execute()
      schema.getPerson("TestId").execute()
      flow1
        .skipItemsWhere { it.data.person?.name == "Name0" }
        .let { assertThat(it.data.person?.name).isEqualTo("Name2") }

      schema.updatePerson(id = "TestId", name = "Name3").execute()
      schema.getPerson("TestId").execute()
      assertThat(flow1.awaitItem().data.person?.name).isEqualTo("Name3")
      assertThat(flow2.awaitItem().data.person?.name).isEqualTo("Name3")
    }
  }

  @Test
  fun collect_gets_notified_of_previous_cached_success_even_if_most_recent_fails() = runTest {
    schema.createPerson(id = "TestId", name = "OriginalName").execute()
    keepCacheAlive(schema.getPerson("TestId").withDataDeserializer(DataConnectUntypedData))

    val noName1Query =
      schema.getPerson("TestId").withDataDeserializer(serializer<GetPersonDataNoName1>())
    noName1Query.execute()

    schema.updatePerson(id = "TestId", name = "Name1").execute()

    noName1Query.subscribe().resultFlow.test {
      assertWithMessage("result1").that(awaitItem().data.person?.name).isEqualTo("OriginalName")
      schema.updatePerson(id = "TestId", name = "UltimateName").execute()
      schema.getPerson("TestId").execute()
      assertWithMessage("result2").that(awaitItem().data.person?.name).isEqualTo("UltimateName")
    }
  }

  @Test
  fun collect_gets_cached_result_even_if_new_data_deserializer() = runTest {
    schema.createPerson(id = "TestId", name = "OriginalName").execute()
    keepCacheAlive(schema.getPerson("TestId").withDataDeserializer(DataConnectUntypedData))

    schema.updatePerson(id = "TestId", name = "UltimateName").execute()

    schema.getPerson("TestId").subscribe().resultFlow.test {
      assertWithMessage("result1").that(awaitItem().data.person?.name).isEqualTo("OriginalName")
      assertWithMessage("result2").that(awaitItem().data.person?.name).isEqualTo("UltimateName")
    }
  }

  private sealed class RejectSpecificNameKSerializer(val nameToReject: String) :
    KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("name", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder) =
      decoder.decodeString().also {
        if (it == nameToReject) {
          throw RejectedName("name rejected: $it")
        }
      }

    override fun serialize(encoder: Encoder, value: String) {
      throw UnsupportedOperationException("")
    }

    class RejectedName(message: String) : Exception(message)
  }

  /**
   * A "data" type suitable for the [GetPersonQuery] whose deserialization fails if the name happens
   * to be "Name1". This behavior is useful when testing the caching behavior when one deserializer
   * successfully decodes the data but another one does not. See [GetPersonDataNoName2].
   */
  @Serializable
  private data class GetPersonDataNoName1(val person: Person?) {
    @Serializable
    data class Person(
      @Serializable(with = NameKSerializer::class) val name: String,
      val age: Int?
    ) {
      private object NameKSerializer : RejectSpecificNameKSerializer("Name1")
    }
  }

  /**
   * A "data" type suitable for the [GetPersonQuery] whose deserialization fails if the name happens
   * to be "Name2". This behavior is useful when testing the caching behavior when one deserializer
   * successfully decodes the data but another one does not. See [GetPersonDataNoName1].
   */
  @Serializable
  private data class GetPersonDataNoName2(val person: Person?) {
    @Serializable
    data class Person(
      @Serializable(with = NameKSerializer::class) val name: String,
      val age: Int?
    ) {
      private object NameKSerializer : RejectSpecificNameKSerializer("Name2")
    }
  }

  /**
   * Starts a background coroutine that subscribes to and collects the given query with the given
   * variables. Suspends until the first result has been collected. This effectively ensures that
   * the cache for the query with the given variables never gets garbage collected.
   */
  private suspend fun <V> TestScope.keepCacheAlive(query: QueryRef<*, V>) {
    val cachePrimed = MutableStateFlow(false)
    backgroundScope.launch {
      query.subscribe().resultFlow.onEach { cachePrimed.value = true }.collect()
    }
    cachePrimed.filter { it }.first()
  }
}
