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

import app.cash.turbine.test
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.DataConnectTestAppCheckProviderFactory
import com.google.firebase.dataconnect.testutil.InvalidInstrumentationArgumentException
import com.google.firebase.dataconnect.testutil.getInstrumentationArgument
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class AppCheckIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }

  private val appCheck: FirebaseAppCheck
    get() = FirebaseAppCheck.getInstance(personSchema.dataConnect.app)

  private val appId: String
    get() = personSchema.dataConnect.app.options.applicationId

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
    appCheck.installAppCheckProviderFactory(DataConnectTestAppCheckProviderFactory(appId))

    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    val person3Id = Arb.alphanumericString(prefix = "person3Id").next()

    personSchema.createPerson(id = person1Id, name = "TestName1", age = 42).execute()
    personSchema.createPerson(id = person2Id, name = "TestName2", age = 43).execute()
    personSchema.createPerson(id = person3Id, name = "TestName3", age = 44).execute()
    val queryResult = personSchema.getPerson(id = person2Id).execute()

    queryResult.asClue {
      it.data.person shouldBe PersonSchema.GetPersonQuery.Data.Person("TestName2", 43)
    }
  }

  @Test
  fun queryShouldFailWhenAppCheckTokenIsThePlaceholder() = runTest {
    // TODO: Add an integration test where the AppCheck dependency is absent, and ensure that no
    // appcheck token is sent at all.
    val personId = Arb.alphanumericString(prefix = "personId").next()
    val queryRef = personSchema.getPerson(id = personId)

    val thrownException = shouldThrow<StatusException> { queryRef.execute() }

    thrownException.asClue { it.status.code shouldBe Status.UNAUTHENTICATED.code }
  }

  @Test
  fun mutationShouldFailWhenAppCheckTokenIsThePlaceholder() = runTest {
    // TODO: Add an integration test where the AppCheck dependency is absent, and ensure that no
    // appcheck token is sent at all.
    val personId = Arb.alphanumericString(prefix = "personId").next()
    val personName = Arb.alphanumericString(prefix = "personName").next()
    val mutationRef = personSchema.createPerson(id = personId, name = personName)

    val thrownException = shouldThrow<StatusException> { mutationRef.execute() }

    thrownException.asClue { it.status.code shouldBe Status.UNAUTHENTICATED.code }
  }

  @Test
  fun queryShouldRetryIfAppCheckTokenIsExpired() = runTest {
    val expiredToken = getInstrumentationArgument(APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG)
    println("$APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG instrumentation argument: $expiredToken")
    assumeNotNull(
      "This test can only be run if an expired token is provided." +
        " To get an expired token, set the $APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG" +
        " instrumentation argument to \"collect\", which will cause this test to simply get" +
        " and print an App Check token in the logcat. Then, wait until that token expires," +
        " which is typically 1 hour, and re-run this test, instead setting the" +
        " $APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG instrumentation argument to the token" +
        " printed when \"collect\" was specified, which should now be expired" +
        " (error code rqbahvqjk8)",
      expiredToken
    )

    if (expiredToken == "collect") {
      val appCheckProviderFactory = DataConnectTestAppCheckProviderFactory(appId)
      val appCheckProvider = appCheckProviderFactory.create(firebaseAppFactory.newInstance())
      val token = appCheckProvider.getToken().await().token
      println("43nyfb9epw Here is the App Check token (without the quotes): \"$token\"")
      return@runTest
    }

    // Install an App Check provider that will initially produce the expired token, and will fetch
    // a new, valid token on subsequent requests.
    val appCheckProviderFactory =
      DataConnectTestAppCheckProviderFactory(appId, initialToken = expiredToken)
    appCheck.installAppCheckProviderFactory(appCheckProviderFactory)

    // Make sure that the App Check doesn't refresh the expired token for us, as it races with
    // the Data Connect SDKs logic to refresh the token.
    appCheck.setTokenAutoRefreshEnabled(false)

    // Send an ExecuteQuery request that should be retired because the first request is sent with
    // the expired token, which should fail with UNAUTHORIZED, triggering a token refresh and
    // request retry.
    val personId = Arb.alphanumericString(prefix = "personId").next()
    personSchema.getPerson(id = personId).execute()

    appCheckProviderFactory.tokens.test {
      withClue("token1") {
        val token = awaitItem()
        token.token shouldBe expiredToken
      }
      withClue("token2") {
        val token = awaitItem()
        token.token shouldNotBe expiredToken
      }
    }
  }

  @Test
  fun mutationShouldRetryIfAppCheckTokenIsExpired() = runTest {
    val expiredToken = getInstrumentationArgument(APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG)
    println("$APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG instrumentation argument: $expiredToken")
    assumeNotNull(
      "This test can only be run if an expired token is provided." +
        " To get an expired token, set the $APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG" +
        " instrumentation argument to \"collect\", which will cause this test to simply get" +
        " and print an App Check token in the logcat. Then, wait until that token expires," +
        " which is typically 1 hour, and re-run this test, instead setting the" +
        " $APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG instrumentation argument to the token" +
        " printed when \"collect\" was specified, which should now be expired" +
        " (error code frsdh5dpxp)",
      expiredToken
    )

    if (expiredToken == "collect") {
      val appCheckProviderFactory = DataConnectTestAppCheckProviderFactory(appId)
      val appCheckProvider = appCheckProviderFactory.create(firebaseAppFactory.newInstance())
      val token = appCheckProvider.getToken().await().token
      println("5xtk6tg4pe Here is the App Check token (without the quotes): \"$token\"")
      return@runTest
    }

    // Install an App Check provider that will initially produce the expired token, and will fetch
    // a new, valid token on subsequent requests.
    val appCheckProviderFactory =
      DataConnectTestAppCheckProviderFactory(appId, initialToken = expiredToken)
    appCheck.installAppCheckProviderFactory(appCheckProviderFactory)

    // Make sure that the App Check doesn't refresh the expired token for us, as it races with
    // the Data Connect SDKs logic to refresh the token.
    appCheck.setTokenAutoRefreshEnabled(false)

    // Send an ExecuteMutation request that should be retired because the first request is sent with
    // the expired token, which should fail with UNAUTHORIZED, triggering a token refresh and
    // request retry.
    val personId = Arb.alphanumericString(prefix = "personId").next()
    val personName = Arb.alphanumericString(prefix = "personName").next()
    personSchema.createPerson(id = personId, name = personName).execute()

    appCheckProviderFactory.tokens.test {
      withClue("token1") {
        val token = awaitItem()
        token.token shouldBe expiredToken
      }
      withClue("token2") {
        val token = awaitItem()
        token.token shouldNotBe expiredToken
      }
    }
  }

  private companion object {
    const val APP_CHECK_ENFORCING_INSTRUMENTATION_ARG = "DATA_CONNECT_APP_CHECK_ENFORCING"
    const val APP_CHECK_EXPIRED_TOKEN_INSTRUMENTATION_ARG = "DATA_CONNECT_APP_CHECK_EXPIRED_TOKEN"

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
