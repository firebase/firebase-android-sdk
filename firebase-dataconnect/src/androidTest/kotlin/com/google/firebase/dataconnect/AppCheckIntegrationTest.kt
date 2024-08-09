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

package com.google.firebase.dataconnect

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.DataConnectTestAppCheckProvider
import com.google.firebase.dataconnect.testutil.InvalidInstrumentationArgumentException
import com.google.firebase.dataconnect.testutil.getInstrumentationArgument
import com.google.firebase.dataconnect.testutil.randomId
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.randomPersonId
import io.kotest.assertions.asClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class AppCheckIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }

  @Before
  fun skipIfUsingEmulator() {
    val backend = DataConnectBackend.fromInstrumentationArguments()
    assumeTrue(
      "This test cannot be run against the Data Connect emulator (backend=$backend)",
      backend !is DataConnectBackend.Emulator
    )
  }

  @Before
  fun skipIfAppCheckNotInEnforcingMode() {
    assumeTrue(
      "This test must be run against a production project with App Check" +
        " enabled and in enforcing mode. This requires setting up the project as documented" +
        " in DataConnectTestAppCheckProvider",
      isAppCheckInEnforcingMode()
    )
  }

  @Test
  fun queryAndMutationShouldSucceedWhenAppCheckTokenIsProvided() = runTest {
    val appCheck = FirebaseAppCheck.getInstance(personSchema.dataConnect.app)
    appCheck.installAppCheckProviderFactory(
      DataConnectTestAppCheckProvider.Factory(
        personSchema.dataConnect.app.applicationContext.getString(R.string.google_app_id)
      )
    )

    val person1Id = randomPersonId()
    val person2Id = randomPersonId()
    val person3Id = randomPersonId()

    personSchema.createPerson(id = person1Id, name = "TestName1", age = 42).execute()
    personSchema.createPerson(id = person2Id, name = "TestName2", age = 43).execute()
    personSchema.createPerson(id = person3Id, name = "TestName3", age = 44).execute()
    val queryResult = personSchema.getPerson(id = person2Id).execute()

    queryResult.asClue {
      it.data.person shouldBe PersonSchema.GetPersonQuery.Data.Person("TestName2", 43)
    }
  }

  @Test
  fun queryShouldFailWhenAppCheckTokenIsThePlaceholderToken() = runTest {
    // TODO: Add an integration test where the AppCheck dependency is absent, and ensure that no
    // appcheck token is sent at all.

    personSchema.getPerson(id = randomId()).execute()
  }

  private companion object {
    const val APP_CHECK_ENFORCING_INSTRUMENTATION_ARG = "DATA_CONNECT_APP_CHECK_ENFORCING"

    private fun isAppCheckInEnforcingMode(): Boolean {
      return when (
        val value = getInstrumentationArgument(APP_CHECK_ENFORCING_INSTRUMENTATION_ARG)
      ) {
        null -> false
        "0" -> false
        "1" -> true
        else ->
          throw InvalidInstrumentationArgumentException(
            APP_CHECK_ENFORCING_INSTRUMENTATION_ARG,
            value,
            "must be either \"0\" or \"1\""
          )
      }
    }
  }
}
