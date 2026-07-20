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
import com.google.firebase.dataconnect.QueryRef.FetchPolicy.SERVER_ONLY
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.DataConnectTestAppCheckProviderFactory
import com.google.firebase.dataconnect.testutil.InvalidInstrumentationArgumentException
import com.google.firebase.dataconnect.testutil.awaitStatusException
import com.google.firebase.dataconnect.testutil.awaitUntilItem
import com.google.firebase.dataconnect.testutil.getInstrumentationArgument
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.schemas.RealtimeConnector
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class AppCheckIntegrationTest : DataConnectIntegrationTestBase() {

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
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val appCheck = getAppCheck(dataConnect)
    val appId = getAppId(dataConnect)
    appCheck.installAppCheckProviderFactory(DataConnectTestAppCheckProviderFactory(appId))
    val connector = RealtimeConnector.getInstance(dataConnect)
    val name = "name_" + Arb.dataConnect.alphabeticString(length = 20).sample()

    val key = connector.insertString(name)
    val queryResult = connector.getStringByKey.queryRef(key).execute(SERVER_ONLY)

    queryResult.asClue { it.data.item.shouldNotBeNull().name shouldBe name }
  }

  @Test
  fun queryShouldFailWhenAppCheckTokenIsThePlaceholder() = runTest {
    // TODO: Add an integration test where the AppCheck dependency is absent, and ensure that no
    // appcheck token is sent at all.
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef = connector.getStringByKey.queryRef("51E1EB46-A833-46C2-8B97-AA767A457F7A")

    val thrownException = shouldThrow<StatusException> { queryRef.execute() }

    thrownException.asClue { it.status.code shouldBe Status.UNAUTHENTICATED.code }
  }

  @Test
  fun mutationShouldFailWhenAppCheckTokenIsThePlaceholder() = runTest {
    // TODO: Add an integration test where the AppCheck dependency is absent, and ensure that no
    // appcheck token is sent at all.
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val name = "name_" + Arb.dataConnect.alphabeticString(length = 20).sample()
    val mutationRef = connector.insertString.mutationRef(name)

    val thrownException = shouldThrow<StatusException> { mutationRef.execute() }

    thrownException.asClue { it.status.code shouldBe Status.UNAUTHENTICATED.code }
  }

  @Test
  fun queryShouldRetryIfAppCheckTokenIsExpired() = testRetryIfAppCheckTokenIsExpired {
    // Send an ExecuteQuery request that should be retired because the first request is sent with
    // the expired token, which should fail with UNAUTHORIZED, triggering a token refresh and
    // request retry.
    val queryRef = connector.getStringByKey.queryRef("DAE78A79-F2AB-4800-8683-FA20AF0391C3")
    queryRef.execute(SERVER_ONLY)
  }

  @Test
  fun mutationShouldRetryIfAppCheckTokenIsExpired() = testRetryIfAppCheckTokenIsExpired {
    // Send an ExecuteMutation request that should be retired because the first request is sent with
    // the expired token, which should fail with UNAUTHORIZED, triggering a token refresh and
    // request retry.
    val name = "name_" + Arb.dataConnect.alphabeticString(length = 20).sample()
    val mutationRef = connector.insertString.mutationRef(name)
    mutationRef.execute()
  }

  @Test
  fun realtimeQuerySubscriptionShouldSucceedWhenAppCheckTokenIsProvided() = runTest {
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val appCheck = getAppCheck(dataConnect)
    val appId = getAppId(dataConnect)
    appCheck.installAppCheckProviderFactory(DataConnectTestAppCheckProviderFactory(appId))
    val connector = RealtimeConnector.getInstance(dataConnect)
    val (name1, name2) = Arb.dataConnect.alphabeticString(length = 20).pair().sample()
    val key = connector.insertString(name1)
    val querySubscription = connector.getStringByKey.queryRef(key).subscribe()

    querySubscription.flow.test {
      awaitItem().result.shouldBeSuccess().data.item.shouldNotBeNull().name shouldBe name1
      connector.updateString(key, name2)
      awaitUntilItem("name2") {
        it.result.shouldBeSuccess().data.item.shouldNotBeNull().name == name2
      }
    }
  }

  @Test
  fun realtimeQuerySubscriptionShouldFailWhenAppCheckTokenIsThePlaceholder() = runTest {
    // TODO: Add an integration test where the AppCheck dependency is absent, and ensure that no
    // appcheck token is sent at all.
    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val connector = RealtimeConnector.getInstance(dataConnect)
    val queryRef = connector.getStringByKey.queryRef("60685DEE-98E9-4E8E-93E4-D756772D21D3")
    val querySubscription = queryRef.subscribe()

    querySubscription.flow.test { awaitStatusException(Status.Code.UNAUTHENTICATED) }
  }

  @Test
  fun realtimeQuerySubscriptionShouldRetryIfAppCheckTokenIsExpired() =
    testRetryIfAppCheckTokenIsExpired {
      // Subscribe to a query where the Connect RPC should be retired because the first request is
      // sent with the expired token, which should fail with UNAUTHORIZED, triggering a token
      // refresh and request retry.
      val queryRef = connector.getStringByKey.queryRef("BD6DBD34-6870-4956-89CA-FBE462EAB2DF")
      val querySubscription = queryRef.subscribe()

      querySubscription.flow.test { awaitItem() }
    }

  class TestRetryIfAppCheckTokenIsExpiredContext(val connector: RealtimeConnector)

  private fun testRetryIfAppCheckTokenIsExpired(
    block: suspend TestRetryIfAppCheckTokenIsExpiredContext.() -> Unit
  ) = runTest {
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

    val dataConnect = dataConnectFactory.newInstance(RealtimeConnector.config)
    val appId = getAppId(dataConnect)

    if (expiredToken == "collect") {
      val appCheckProviderFactory = DataConnectTestAppCheckProviderFactory(appId)
      val appCheckProvider = appCheckProviderFactory.create(firebaseAppFactory.newInstance())
      val token = appCheckProvider.getToken().await().token
      println("5xtk6tg4pe Here is the App Check token (without the quotes): \"$token\"")
      return@runTest
    }

    val appCheck = getAppCheck(dataConnect)
    appCheck.installAppCheckProviderFactory(DataConnectTestAppCheckProviderFactory(appId))
    val connector = RealtimeConnector.getInstance(dataConnect)

    // Install an App Check provider that will initially produce the expired token, and will fetch
    // a new, valid token on subsequent requests.
    val appCheckProviderFactory =
      DataConnectTestAppCheckProviderFactory(appId, initialToken = expiredToken)
    appCheck.installAppCheckProviderFactory(appCheckProviderFactory)

    // Make sure that the App Check doesn't refresh the expired token for us, as it races with
    // the Data Connect SDKs logic to refresh the token.
    appCheck.setTokenAutoRefreshEnabled(false)

    // Run the test-specific block of code.
    with(TestRetryIfAppCheckTokenIsExpiredContext(connector)) { block() }

    // Verify that the App Check tokens are requested from the provider.
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

private fun getAppCheck(dataConnect: FirebaseDataConnect): FirebaseAppCheck =
  FirebaseAppCheck.getInstance(dataConnect.app)

private fun getAppId(dataConnect: FirebaseDataConnect): String =
  dataConnect.app.options.applicationId
