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

@file:OptIn(FlowPreview::class)

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Subject
import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.*
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuerySubscriptionTest {

  @JvmField @Rule val dataConnectFactory = TestDataConnectFactory()
  @JvmField @Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  private lateinit var schema: PersonSchema

  @Before
  fun initializePersonSchema() {
    schema = PersonSchema(dataConnectFactory.newInstance())
    runBlocking { schema.installEmulatorSchema() }
  }

  @Test
  fun lastResult_should_be_null_on_new_instance() {
    val querySubscription = schema.getPerson.subscribe(id = "42")
    assertThat(querySubscription.lastResult).isNull()
  }

  @Test
  fun lastResult_should_be_equal_to_the_last_collected_result() = runBlocking {
    schema.createPerson.execute(id = "TestId", name = "TestPerson", age = 42)
    val querySubscription = schema.getPerson.subscribe(id = "42")
    val result = querySubscription.flow.timeout(2000.seconds).first()
    assertThat(querySubscription.lastResult).isEqualTo(result)
  }

  @Test
  fun flow_collect_should_trigger_reload() = runBlocking {
    schema.createPerson.execute(id = "TestId12345", name = "Name0", age = 10000)
    val querySubscription = schema.getPerson.subscribe(id = "TestId12345")

    withTimeout(2.seconds) {
      val resultsChannel =
        Channel<PersonSchema.GetPersonQueryRef.Result?>(capacity = Channel.UNLIMITED)
      val collectJob = launch {
        querySubscription.flow.collect { resultsChannel.send(it.getOrThrow()) }
      }

      val result1 = resultsChannel.receive()
      assertThat(result1).isEqualToGetPersonQueryResult(name = "Name0", age = 10000)

      schema.updatePerson.execute(id = "TestId12345", name = "Name1", age = 10001)

      querySubscription.reload()
      val result2 = resultsChannel.receive()
      assertThat(result2).isEqualToGetPersonQueryResult(name = "Name1", age = 10001)

      collectJob.cancel()
    }
  }

  @Test
  fun calling_reload_many_times_concurrently_does_not_cause_issues() = runBlocking {
    schema.createPerson.execute(id = "TestId12345", name = "Name", age = 10000)
    val querySubscription = schema.getPerson.subscribe(id = "TestId12345")

    withTimeout(5.seconds) {
      val resultsChannel =
        Channel<Result<PersonSchema.GetPersonQueryRef.Result?>>(capacity = Channel.UNLIMITED)
      val collectJob = launch { querySubscription.flow.collect(resultsChannel::send) }

      val maxHardwareConcurrency = Math.max(2, Runtime.getRuntime().availableProcessors())
      val multiThreadExecutor = Executors.newFixedThreadPool(maxHardwareConcurrency)
      try {
        repeat(100000) { multiThreadExecutor.execute(querySubscription::reload) }
      } finally {
        multiThreadExecutor.shutdown()
      }

      var resultCount = 0
      while (true) {
        resultCount++
        val result = withTimeoutOrNull(1.seconds) { resultsChannel.receive() } ?: break
        assertThat(result.getOrThrow()).isEqualToGetPersonQueryResult(name = "Name", age = 10000)
      }
      assertThat(resultCount).isGreaterThan(0)

      collectJob.cancel()
    }
  }
}

fun Subject.isEqualToGetPersonQueryResult(name: String, age: Int?) =
  isEqualTo(PersonSchema.GetPersonQueryRef.Result(name = name, age = age))
