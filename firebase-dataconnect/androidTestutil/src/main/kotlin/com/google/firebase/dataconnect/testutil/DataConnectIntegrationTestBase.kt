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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.util.nextAlphanumericString
import kotlin.random.Random
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
abstract class DataConnectIntegrationTestBase {

  @get:Rule val testNameRule = TestName()

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @get:Rule val firebaseAppFactory = TestFirebaseAppFactory()

  @get:Rule val dataConnectFactory = TestDataConnectFactory(firebaseAppFactory)

  companion object {
    val testConnectorConfig: ConnectorConfig
      get() =
        ConnectorConfig(
          connector = "demo", // TODO: change to "ctrgqyawcfbm4" once it's ready
          location = "us-central1",
          serviceId = "sid2ehn9ct8te",
        )
  }
}

/** The name of the currently-running test, in the form "ClassName.MethodName". */
val DataConnectIntegrationTestBase.testName
  get() = this::class.qualifiedName + "." + testNameRule.methodName

/**
 * Generates and returns a string containing random alphanumeric characters, including the name of
 * the currently-running test as returned from [testName].
 *
 * @param prefix A prefix to include in the returned string; if null (the default) then no prefix
 * will be included.
 * @param numRandomChars The number of random characters to include in the returned string; if null
 * (the default) then a default number will be used. At the time of writing, the default number of
 * characters is 20 (but this may change in the future).
 * @return a string containing random characters and incorporating the other information identified
 * above.
 */
fun DataConnectIntegrationTestBase.randomAlphanumericString(
  prefix: String? = null,
  numRandomChars: Int? = null
): String = buildString {
  if (prefix != null) {
    append(prefix)
    append("_")
  }
  append(testName)
  append("_")
  append(Random.nextAlphanumericString(length = numRandomChars ?: 20))
}
