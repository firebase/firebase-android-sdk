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

import com.google.firebase.dataconnect.testutil.DataConnectBackend.Companion.fromInstrumentationArgument
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.net.URI
import java.net.URL
import org.junit.Test

class DataConnectBackendUnitTest {

  @Test
  fun `fromInstrumentationArgument(null) should return null`() {
    fromInstrumentationArgument(null).shouldBeNull()
  }

  @Test
  fun `fromInstrumentationArgument('prod') should return Production`() {
    fromInstrumentationArgument("prod") shouldBeSameInstanceAs DataConnectBackend.Production
  }

  @Test
  fun `fromInstrumentationArgument('staging') should return Staging`() {
    fromInstrumentationArgument("staging") shouldBeSameInstanceAs DataConnectBackend.Staging
  }

  @Test
  fun `fromInstrumentationArgument('autopush') should return Autopush`() {
    fromInstrumentationArgument("autopush") shouldBeSameInstanceAs DataConnectBackend.Autopush
  }

  @Test
  fun `fromInstrumentationArgument('emulator') should return Emulator()`() {
    fromInstrumentationArgument("emulator") shouldBe DataConnectBackend.Emulator()
  }

  @Test
  fun `fromInstrumentationArgument(emulator with host) should return Emulator() with the host`() {
    fromInstrumentationArgument("emulator:a.b.c") shouldBe
      DataConnectBackend.Emulator(host = "a.b.c")
  }

  @Test
  fun `fromInstrumentationArgument(emulator with port) should return Emulator() with the port`() {
    fromInstrumentationArgument("emulator::9987") shouldBe DataConnectBackend.Emulator(port = 9987)
  }

  @Test
  fun `fromInstrumentationArgument(emulator with host and port) should return Emulator() with the host and port`() {
    fromInstrumentationArgument("emulator:a.b.c:9987") shouldBe
      DataConnectBackend.Emulator(host = "a.b.c", port = 9987)
  }

  @Test
  fun `fromInstrumentationArgument(http url with host) should return Custom()`() {
    fromInstrumentationArgument("http://a.b.c") shouldBe DataConnectBackend.Custom("a.b.c", false)
  }

  @Test
  fun `fromInstrumentationArgument(http url with host and port) should return Custom()`() {
    fromInstrumentationArgument("http://a.b.c:9987") shouldBe
      DataConnectBackend.Custom("a.b.c:9987", false)
  }

  @Test
  fun `fromInstrumentationArgument(https url with host) should return Custom()`() {
    fromInstrumentationArgument("https://a.b.c") shouldBe DataConnectBackend.Custom("a.b.c", true)
  }

  @Test
  fun `fromInstrumentationArgument(https url with host and port) should return Custom()`() {
    fromInstrumentationArgument("https://a.b.c:9987") shouldBe
      DataConnectBackend.Custom("a.b.c:9987", true)
  }

  @Test
  fun `fromInstrumentationArgument('foo') should throw an exception`() {
    val exception =
      shouldThrow<InvalidInstrumentationArgumentException> { fromInstrumentationArgument("foo") }

    val urlParseErrorMessage = runCatching { URL("foo") }.exceptionOrNull()!!.message!!
    assertSoftly {
      exception.message shouldContainWithNonAbuttingText "foo"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "invalid"
      exception.message shouldContainWithNonAbuttingText "DATA_CONNECT_BACKEND"
      exception.message shouldContainWithNonAbuttingText urlParseErrorMessage
    }
  }

  @Test
  fun `fromInstrumentationArgument(invalid URI) should throw an exception`() {
    val exception =
      shouldThrow<InvalidInstrumentationArgumentException> { fromInstrumentationArgument("..:") }

    val uriParseErrorMessage = runCatching { URI("..:") }.exceptionOrNull()!!.message!!
    assertSoftly {
      exception.message shouldContainWithNonAbuttingText "..:"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "invalid"
      exception.message shouldContainWithNonAbuttingText "DATA_CONNECT_BACKEND"
      exception.message shouldContainWithNonAbuttingText uriParseErrorMessage
    }
  }

  @Test
  fun `fromInstrumentationArgument(invalid emulator URI) should throw an exception`() {
    val exception =
      shouldThrow<InvalidInstrumentationArgumentException> {
        fromInstrumentationArgument("emulator:::::")
      }

    val urlParseErrorMessage = runCatching { URL("https://::::") }.exceptionOrNull()!!.message!!
    assertSoftly {
      exception.message shouldContainWithNonAbuttingText "emulator:::::"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "invalid"
      exception.message shouldContainWithNonAbuttingText "DATA_CONNECT_BACKEND"
      exception.message shouldContainWithNonAbuttingText urlParseErrorMessage
    }
  }
}
