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

@file:OptIn(FlowPreview::class, ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.firebase.dataconnect.core.QuerySubscriptionInternal
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.SuspendingFlag
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import org.junit.Test

class QuerySubscriptionIntegrationTest : DataConnectIntegrationTestBase() {

  private val schema by lazy { PersonSchema(dataConnectFactory) }

  @Test
  fun lastResult_should_be_null_on_new_instance() {
    val querySubscription =
      schema.getPerson(id = "42").subscribe()
        as QuerySubscriptionInternal<GetPersonQuery.Data, GetPersonQuery.Variables>
    querySubscription.lastResult.shouldBeNull()
  }

  @Test
  fun lastResult_should_be_equal_to_the_last_collected_result() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "Name1").execute()
    val querySubscription =
      schema.getPerson(id = personId).subscribe()
        as QuerySubscriptionInternal<GetPersonQuery.Data, GetPersonQuery.Variables>

    querySubscription.flow.test { withClue("result1A") { awaitPersonWithName("Name1") } }

    schema.updatePerson(id = personId, name = "Name2", age = 2).execute()

    querySubscription.flow.distinctUntilChanged().test {
      val result1B = awaitItem()
      withClue("result1B") { result1B shouldBe querySubscription.lastResult }
      withClue("result2") { awaitPersonWithName("Name2") }
    }
  }

  @Test
  fun reload_should_notify_collecting_flows() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "Name1").execute()
    val queryRef = schema.getPerson(id = personId)
    val querySubscription = schema.getPerson(id = personId).subscribe()

    querySubscription.flow.distinctUntilChanged().test {
      withClue("result1") { awaitPersonWithName("Name1") }

      schema.updatePerson(id = personId, name = "Name2").execute()
      queryRef.execute()

      withClue("result2") { awaitPersonWithName("Name2") }
    }
  }

  @Test
  fun flow_collect_should_get_immediately_invoked_with_last_result() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "TestName").execute()
    val querySubscription = schema.getPerson(id = personId).subscribe()

    withClue("result1") { querySubscription.flow.first().shouldHavePersonWithName("TestName") }

    schema.updatePerson(id = personId, name = "Name2").execute()

    withClue("result2") { querySubscription.flow.first().shouldHavePersonWithName("TestName") }
  }

  @Test
  fun flow_collect_should_get_immediately_invoked_with_last_result_from_other_subscribers() =
    runTest {
      val personId = Arb.alphanumericString(prefix = "personId").next()
      schema.createPerson(id = personId, name = "TestName").execute()
      val querySubscription1 = schema.getPerson(id = personId).subscribe()
      val querySubscription2 = schema.getPerson(id = personId).subscribe()

      // Start collecting on `querySubscription1` and wait for it to get its first event.
      val subscription1ResultReceived = SuspendingFlag()
      backgroundScope.launch {
        querySubscription1.flow.collect { subscription1ResultReceived.set() }
      }
      subscription1ResultReceived.await()

      // With `querySubscription1` still alive, start collecting on `querySubscription2`. Expect it
      // to initially get the cached result from `querySubscription1`, followed by an updated
      // result.
      schema.updatePerson(id = personId, name = "NewTestName").execute()

      querySubscription2.flow.distinctUntilChanged().test {
        withClue("result1") { awaitPersonWithName("TestName") }
        withClue("result2") { awaitPersonWithName("NewTestName") }
      }
    }

  @Test
  fun slow_flows_do_not_block_fast_flows() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "Name0").execute()
    val queryRef = schema.getPerson(id = personId)
    val querySubscription = queryRef.subscribe()

    turbineScope {
      val fastFlow = querySubscription.flow.distinctUntilChanged().testIn(backgroundScope)
      withClue("fastFlowResult1") { fastFlow.awaitPersonWithName("Name0") }

      val slowFlowStarted = SuspendingFlag()
      val slowFlowEnabled = SuspendingFlag()
      val slowFlow =
        querySubscription.flow
          .distinctUntilChanged()
          .onEach {
            slowFlowStarted.set()
            slowFlowEnabled.await()
          }
          .testIn(backgroundScope)
      slowFlowStarted.await()

      repeat(3) {
        schema.updatePerson(id = personId, name = "NewName$it").execute()
        queryRef.execute()
      }

      withClue("fastFlowResult2") { fastFlow.awaitPersonWithName("NewName0") }
      withClue("fastFlowResult3") { fastFlow.awaitPersonWithName("NewName1") }
      withClue("fastFlowResult4") { fastFlow.awaitPersonWithName("NewName2") }

      slowFlowEnabled.set()
      withClue("slowFlowResult1") { slowFlow.awaitPersonWithName("Name0") }
      withClue("slowFlowResult2") { slowFlow.awaitPersonWithName("NewName0") }
      withClue("slowFlowResult3") { slowFlow.awaitPersonWithName("NewName1") }
      withClue("slowFlowResult4") { slowFlow.awaitPersonWithName("NewName2") }
    }
  }

  @Test
  fun reload_delivers_result_to_all_registered_flows_on_all_QuerySubscriptions() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "OriginalName").execute()
    val querySubscription1 = schema.getPerson(id = personId).subscribe()
    val querySubscription2 = schema.getPerson(id = personId).subscribe()

    turbineScope {
      val flow1aStarted = SuspendingFlag()
      val flow1a =
        querySubscription1.flow
          .onEach { flow1aStarted.set() }
          .distinctUntilChanged()
          .testIn(backgroundScope)
      val flow1bStarted = SuspendingFlag()
      val flow1b =
        querySubscription1.flow
          .onEach { flow1bStarted.set() }
          .distinctUntilChanged()
          .testIn(backgroundScope)
      val flow2Started = SuspendingFlag()
      val flow2 =
        querySubscription2.flow
          .onEach { flow2Started.set() }
          .distinctUntilChanged()
          .testIn(backgroundScope)
      flow1aStarted.await()
      flow1bStarted.await()
      flow2Started.await()

      schema.updatePerson(id = personId, name = "NewName").execute()
      (querySubscription1 as QuerySubscriptionInternal<*, *>).reload()

      withClue("flow1a-1") { flow1a.awaitPersonWithName("OriginalName") }
      withClue("flow1a-2") { flow1a.awaitPersonWithName("NewName") }
      withClue("flow1b-1") { flow1b.awaitPersonWithName("OriginalName") }
      withClue("flow1b-2") { flow1b.awaitPersonWithName("NewName") }
      withClue("flow2-1") { flow2.awaitPersonWithName("OriginalName") }
      withClue("flow2-2") { flow2.awaitPersonWithName("NewName") }
    }
  }

  @Test
  fun queryref_execute_delivers_result_to_QuerySubscriptions() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "OriginalName").execute()
    val querySubscription1 = schema.getPerson(id = personId).subscribe()
    val querySubscription2 = schema.getPerson(id = personId).subscribe()

    turbineScope {
      val flow1aStarted = SuspendingFlag()
      val flow1a =
        querySubscription1.flow
          .onEach { flow1aStarted.set() }
          .distinctUntilChanged()
          .testIn(backgroundScope)
      val flow1bStarted = SuspendingFlag()
      val flow1b =
        querySubscription1.flow
          .onEach { flow1bStarted.set() }
          .distinctUntilChanged()
          .testIn(backgroundScope)
      val flow2Started = SuspendingFlag()
      val flow2 =
        querySubscription2.flow
          .onEach { flow2Started.set() }
          .distinctUntilChanged()
          .testIn(backgroundScope)
      flow1aStarted.await()
      flow1bStarted.await()
      flow2Started.await()

      schema.updatePerson(id = personId, name = "NewName").execute()
      schema.getPerson(id = personId).execute()

      withClue("flow1a-1") { flow1a.awaitPersonWithName("OriginalName") }
      withClue("flow1a-2") { flow1a.awaitPersonWithName("NewName") }
      withClue("flow1b-1") { flow1b.awaitPersonWithName("OriginalName") }
      withClue("flow1b-2") { flow1b.awaitPersonWithName("NewName") }
      withClue("flow2-1") { flow2.awaitPersonWithName("OriginalName") }
      withClue("flow2-2") { flow2.awaitPersonWithName("NewName") }
    }
  }

  @Test
  fun reload_concurrent_invocations_get_conflated() =
    runTest(timeout = 60.seconds) {
      val personId = Arb.alphanumericString(prefix = "personId").next()
      schema.createPerson(id = personId, name = "OriginalName").execute()
      val query = schema.getPerson(id = personId)
      val querySubscription = query.subscribe()

      querySubscription.flow.test {
        awaitPersonWithName("OriginalName")
        schema.updatePerson(id = personId, name = "NewName").execute()

        buildList {
            repeat(10_000) {
              // Run on Dispatchers.Default to ensure some level of concurrency.
              add(backgroundScope.async(Dispatchers.Default) { query.execute() })
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

        assertSoftly {
          withClue("results.size") { results.size shouldBeInRange 1..5000 }
          results.forEachIndexed { i, result ->
            withClue("results[$i]") { result.shouldHavePersonWithName("NewName") }
          }
        }
      }
    }

  @Test
  fun update_changes_variables_and_triggers_reload() = runTest {
    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    val person3Id = Arb.alphanumericString(prefix = "person3Id").next()
    schema.createPerson(id = person1Id, name = "Name1").execute()
    schema.createPerson(id = person2Id, name = "Name2").execute()
    schema.createPerson(id = person3Id, name = "Name3").execute()
    val query = schema.getPerson(id = person1Id)
    val querySubscription =
      query.subscribe() as QuerySubscriptionInternal<GetPersonQuery.Data, GetPersonQuery.Variables>

    querySubscription.flow.distinctUntilChanged().test {
      withClue("result1") {
        val result1 = awaitPersonWithName("Name1")
        result1.query shouldBeSameInstanceAs query
        result1.result.getOrThrow().ref shouldBeSameInstanceAs query
      }

      val variables2 = GetPersonQuery.Variables(person2Id)
      querySubscription.update(variables2)

      withClue("result2") {
        val result2 = awaitPersonWithName("Name2")
        result2.query.variables shouldBe variables2
        result2.result.getOrThrow().ref shouldBeSameInstanceAs result2.query
      }

      val variables3 = GetPersonQuery.Variables(person3Id)
      querySubscription.update(variables3)

      withClue("result3") {
        val result3 = awaitPersonWithName("Name3")
        result3.query.variables shouldBe variables3
        result3.result.getOrThrow().ref shouldBeSameInstanceAs result3.query
      }
    }
  }

  @Test
  fun reload_updates_last_result_even_if_no_active_collectors() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "Name1").execute()
    val query = schema.getPerson(id = personId)
    val querySubscription =
      query.subscribe() as QuerySubscriptionInternal<GetPersonQuery.Data, GetPersonQuery.Variables>

    querySubscription.reload()

    withClue("lastResult") {
      querySubscription.lastResult.shouldNotBeNull().shouldHavePersonWithName("Name1")
    }

    schema.updatePerson(id = personId, name = "Name2").execute()

    querySubscription.flow.distinctUntilChanged().test {
      // Ensure that the first result comes from cache, followed by the updated result received from
      // the server when a reload was triggered by the flow's collection.
      awaitPersonWithName("Name1")
      awaitPersonWithName("Name2")
    }
  }

  @Test
  fun update_updates_last_result_even_if_no_active_collectors() = runTest {
    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    schema.createPerson(id = person1Id, name = "Name1").execute()
    schema.createPerson(id = person2Id, name = "Name2").execute()
    val querySubscription =
      schema.getPerson(id = person1Id).subscribe()
        as QuerySubscriptionInternal<GetPersonQuery.Data, GetPersonQuery.Variables>

    val newVariables = GetPersonQuery.Variables(person2Id)
    querySubscription.update(newVariables)

    withClue("lastResult") {
      val lastResult = querySubscription.lastResult.shouldNotBeNull()
      lastResult.shouldHavePersonWithName("Name2")
      lastResult.query.variables shouldBe newVariables
      lastResult.result.getOrThrow().ref shouldBeSameInstanceAs lastResult.query
    }

    schema.updatePerson(id = person2Id, name = "NewName2").execute()

    querySubscription.flow.distinctUntilChanged().test {
      // Ensure that the first result comes from cache, followed by the updated result received from
      // the server when a reload was triggered by the flow's collection.
      awaitPersonWithName("Name2")
      awaitPersonWithName("NewName2")
    }
  }

  @Test
  fun collect_gets_an_update_on_error() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "Name1").execute()

    val noName2Query =
      schema.getPerson(personId).withDataDeserializer(serializer<GetPersonDataNoName2>())
    val querySubscription = noName2Query.subscribe()

    turbineScope {
      val flow = querySubscription.flow.distinctUntilChanged().testIn(backgroundScope)
      withClue("result1") { flow.awaitPersonWithName("Name1") }

      schema.updatePerson(id = personId, name = "Name2").execute()
      val execute2Result = runCatching { noName2Query.execute() }
      withClue("execute2Result") {
        withClue("execute2Result.getOrNull()") { execute2Result.getOrNull().shouldBeNull() }
        withClue("execute2Result.isFailure") { execute2Result.isFailure shouldBe true }
      }
      withClue("result2") {
        val result2 = flow.awaitItem().result
        withClue("result2.getOrNull()") { result2.getOrNull().shouldBeNull() }
        withClue("result2.isFailure") { result2.isFailure shouldBe true }
      }

      schema.updatePerson(id = personId, name = "Name3").execute()
      noName2Query.execute()
      withClue("result3") { flow.awaitPersonWithName("Name3") }
    }
  }

  @Test
  fun collect_gets_notified_of_per_data_deserializer_successes() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "Name0").execute()

    val noName1Query =
      schema.getPerson(personId).withDataDeserializer(serializer<GetPersonDataNoName1>())
    val noName2Query =
      schema.getPerson(personId).withDataDeserializer(serializer<GetPersonDataNoName2>())

    turbineScope {
      val noName1Flow =
        noName1Query
          .subscribe()
          .flow
          .distinctUntilChanged(::areEquivalentQuerySubscriptionResults)
          .testIn(backgroundScope)
      val noName2Flow =
        noName2Query
          .subscribe()
          .flow
          .distinctUntilChanged(::areEquivalentQuerySubscriptionResults)
          .testIn(backgroundScope)
      withClue("noName1Flow-0") { noName1Flow.awaitPersonWithName("Name0") }
      withClue("noName2Flow-0") { noName2Flow.awaitPersonWithName("Name0") }

      schema.updatePerson(id = personId, name = "Name1").execute()
      schema.getPerson(personId).execute()

      withClue("noName1Flow-1") {
        noName1Flow.awaitItem().result.exceptionOrNull().shouldNotBeNull()
      }
      withClue("noName2Flow-1") { noName2Flow.awaitPersonWithName("Name1") }

      schema.updatePerson(id = personId, name = "Name2").execute()
      schema.getPerson(personId).execute()

      withClue("noName1Flow-2") { noName1Flow.awaitPersonWithName("Name2") }
      withClue("noName2Flow-2") {
        noName2Flow.awaitItem().result.exceptionOrNull().shouldNotBeNull()
      }

      schema.updatePerson(id = personId, name = "Name3").execute()
      schema.getPerson(personId).execute()

      withClue("noName1Flow-3") { noName1Flow.awaitPersonWithName("Name3") }
      withClue("noName2Flow-3") { noName2Flow.awaitPersonWithName("Name3") }
    }
  }

  @Test
  fun collect_gets_notified_of_previous_cached_success_even_if_most_recent_fails() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "OriginalName").execute()

    val noName1Query =
      schema.getPerson(personId).withDataDeserializer(serializer<GetPersonDataNoName1>())

    keepCacheAlive(noName1Query)

    schema.updatePerson(id = personId, name = "Name1").execute()

    noName1Query.subscribe().flow.distinctUntilChanged().test {
      withClue("result1") { awaitPersonWithName("OriginalName") }
      withClue("result2") { awaitItem().result.exceptionOrNull().shouldNotBeNull() }

      schema.updatePerson(id = personId, name = "UltimateName").execute()
      schema.getPerson(personId).execute()

      withClue("result3") { awaitPersonWithName("UltimateName") }
    }
  }

  @Test
  fun collect_gets_cached_result_even_if_new_data_deserializer() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()
    schema.createPerson(id = personId, name = "OriginalName").execute()
    keepCacheAlive(schema.getPerson(personId).withDataDeserializer(DataConnectUntypedData))

    schema.updatePerson(id = personId, name = "UltimateName").execute()

    schema.getPerson(personId).subscribe().flow.distinctUntilChanged().test {
      awaitPersonWithName("OriginalName")
      awaitPersonWithName("UltimateName")
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
  private suspend fun TestScope.keepCacheAlive(query: QueryRef<*, *>) {
    val cachePrimed = SuspendingFlag()
    backgroundScope.launch { query.subscribe().flow.collect { cachePrimed.set() } }
    cachePrimed.await()
  }

  private companion object {
    @JvmName("awaitPersonWithNameGetPersonQueryData")
    suspend fun ReceiveTurbine<
      QuerySubscriptionResult<GetPersonQuery.Data, GetPersonQuery.Variables>
    >
      .awaitPersonWithName(
      name: String
    ): QuerySubscriptionResult<GetPersonQuery.Data, GetPersonQuery.Variables> {
      val item = awaitItem()
      item.shouldHavePersonWithName(name)
      return item
    }

    @JvmName("shouldHavePersonWithNameGetPersonQueryData")
    fun QuerySubscriptionResult<GetPersonQuery.Data, GetPersonQuery.Variables>
      .shouldHavePersonWithName(name: String) {
      withClue("result.exceptionOrNull()") { result.exceptionOrNull().shouldBeNull() }
      val data = withClue("result.getOrThrow()") { result.getOrThrow().data }
      val person = withClue("data.person") { data.person.shouldNotBeNull() }
      withClue("person.name") { person.name shouldBe name }
    }

    @JvmName("awaitPersonWithNameGetPersonDataNoName1")
    suspend fun ReceiveTurbine<
      QuerySubscriptionResult<GetPersonDataNoName1, GetPersonQuery.Variables>
    >
      .awaitPersonWithName(
      name: String
    ): QuerySubscriptionResult<GetPersonDataNoName1, GetPersonQuery.Variables> {
      val item = awaitItem()
      item.shouldHavePersonWithName(name)
      return item
    }

    @JvmName("shouldHavePersonWithNameGetPersonDataNoName1")
    fun QuerySubscriptionResult<GetPersonDataNoName1, GetPersonQuery.Variables>
      .shouldHavePersonWithName(name: String) {
      withClue("result.exceptionOrNull()") { result.exceptionOrNull().shouldBeNull() }
      val data = withClue("result.getOrThrow()") { result.getOrThrow().data }
      val person = withClue("data.person") { data.person.shouldNotBeNull() }
      withClue("person.name") { person.name shouldBe name }
    }

    @JvmName("awaitPersonWithNameGetPersonDataNoName2")
    suspend fun ReceiveTurbine<
      QuerySubscriptionResult<GetPersonDataNoName2, GetPersonQuery.Variables>
    >
      .awaitPersonWithName(
      name: String
    ): QuerySubscriptionResult<GetPersonDataNoName2, GetPersonQuery.Variables> {
      val item = awaitItem()
      item.shouldHavePersonWithName(name)
      return item
    }

    @JvmName("shouldHavePersonWithNameGetPersonDataNoName2")
    fun QuerySubscriptionResult<GetPersonDataNoName2, GetPersonQuery.Variables>
      .shouldHavePersonWithName(name: String) {
      withClue("result.exceptionOrNull()") { result.exceptionOrNull().shouldBeNull() }
      val data = withClue("result.getOrThrow()") { result.getOrThrow().data }
      val person = withClue("data.person") { data.person.shouldNotBeNull() }
      withClue("person.name") { person.name shouldBe name }
    }

    /**
     * Returns `true` if, and only if, the receiver is a non-null instance of
     * [DataConnectOperationException] that indicates that the failure is due to decoding of the
     * server response failed.
     */
    fun Throwable?.isDecodingServerResponseFailed(): Boolean =
      this is DataConnectOperationException && this.response.errors.isEmpty()

    /**
     * Returns `true` if, and only if, the receiver's result is a failure that indicates that
     * decoding of the server response failed.
     */
    fun QuerySubscriptionResult<*, *>.isDecodingServerResponseFailed(): Boolean =
      result.exceptionOrNull().isDecodingServerResponseFailed()

    /**
     * Checks if two [QuerySubscriptionResult] instances are "equivalent"; that is, they are both
     * equal when compared using the `==` operator, or they are both failures due to decoding of the
     * server response failed.
     *
     * This is useful when testing flows because the same decoding failure can happen more than once
     * in a row based on other asynchronous operations but testing for "distinctness" will consider
     * those two failures as "distinct" when the test wants them to be treated as "equal".
     *
     * See https://github.com/firebase/firebase-android-sdk/pull/7210 for a full explanation.
     */
    fun areEquivalentQuerySubscriptionResults(
      old: QuerySubscriptionResult<*, *>,
      new: QuerySubscriptionResult<*, *>
    ): Boolean =
      (old == new) || (old.isDecodingServerResponseFailed() && new.isDecodingServerResponseFailed())
  }
}
