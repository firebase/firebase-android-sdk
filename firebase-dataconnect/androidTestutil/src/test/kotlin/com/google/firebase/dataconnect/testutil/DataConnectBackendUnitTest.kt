package com.google.firebase.dataconnect.testutil

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.DataConnectBackend.Companion.fromInstrumentationArgument
import com.google.firebase.dataconnect.testutil.DataConnectBackend.InvalidInstrumentationArgument
import java.net.URI
import java.net.URL
import org.junit.Test

class DataConnectBackendUnitTest {

  @Test
  fun `fromInstrumentationArgument(null) should return Production`() {
    assertThat(fromInstrumentationArgument(null)).isNull()
  }

  @Test
  fun `fromInstrumentationArgument('prod') should return Production`() {
    assertThat(fromInstrumentationArgument("prod")).isSameInstanceAs(DataConnectBackend.Production)
  }

  @Test
  fun `fromInstrumentationArgument('staging') should return Staging`() {
    assertThat(fromInstrumentationArgument("staging")).isSameInstanceAs(DataConnectBackend.Staging)
  }

  @Test
  fun `fromInstrumentationArgument('autopush') should return Autopush`() {
    assertThat(fromInstrumentationArgument("autopush"))
      .isSameInstanceAs(DataConnectBackend.Autopush)
  }

  @Test
  fun `fromInstrumentationArgument('emulator') should return Emulator()`() {
    assertThat(fromInstrumentationArgument("emulator")).isEqualTo(DataConnectBackend.Emulator())
  }

  @Test
  fun `fromInstrumentationArgument(emulator with host) should return Emulator() with the host`() {
    assertThat(fromInstrumentationArgument("emulator:a.b.c"))
      .isEqualTo(DataConnectBackend.Emulator(host = "a.b.c"))
  }

  @Test
  fun `fromInstrumentationArgument(emulator with port) should return Emulator() with the port`() {
    assertThat(fromInstrumentationArgument("emulator::9987"))
      .isEqualTo(DataConnectBackend.Emulator(port = 9987))
  }

  @Test
  fun `fromInstrumentationArgument(emulator with host and port) should return Emulator() with the host and port`() {
    assertThat(fromInstrumentationArgument("emulator:a.b.c:9987"))
      .isEqualTo(DataConnectBackend.Emulator(host = "a.b.c", port = 9987))
  }

  @Test
  fun `fromInstrumentationArgument(http url with host) should return Custom()`() {
    assertThat(fromInstrumentationArgument("http://a.b.c"))
      .isEqualTo(DataConnectBackend.Custom("a.b.c", false))
  }

  @Test
  fun `fromInstrumentationArgument(http url with host and port) should return Custom()`() {
    assertThat(fromInstrumentationArgument("http://a.b.c:9987"))
      .isEqualTo(DataConnectBackend.Custom("a.b.c:9987", false))
  }

  @Test
  fun `fromInstrumentationArgument(https url with host) should return Custom()`() {
    assertThat(fromInstrumentationArgument("https://a.b.c"))
      .isEqualTo(DataConnectBackend.Custom("a.b.c", true))
  }

  @Test
  fun `fromInstrumentationArgument(https url with host and port) should return Custom()`() {
    assertThat(fromInstrumentationArgument("https://a.b.c:9987"))
      .isEqualTo(DataConnectBackend.Custom("a.b.c:9987", true))
  }

  @Test
  fun `fromInstrumentationArgument('foo') should throw an exception`() {
    val exception =
      assertThrows(InvalidInstrumentationArgument::class) { fromInstrumentationArgument("foo") }
    val urlParseErrorMessage = runCatching { URL("foo") }.exceptionOrNull()!!.message!!
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("foo")
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("invalid", ignoreCase = true)
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("DATA_CONNECT_BACKEND")
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText(urlParseErrorMessage)
  }

  @Test
  fun `fromInstrumentationArgument(invalid URI) should throw an exception`() {
    val exception =
      assertThrows(InvalidInstrumentationArgument::class) { fromInstrumentationArgument("..:") }
    val uriParseErrorMessage = runCatching { URI("..:") }.exceptionOrNull()!!.message!!
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("..:")
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("invalid", ignoreCase = true)
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("DATA_CONNECT_BACKEND")
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText(uriParseErrorMessage)
  }

  @Test
  fun `fromInstrumentationArgument(invalid emulator URI) should throw an exception`() {
    val exception =
      assertThrows(InvalidInstrumentationArgument::class) {
        fromInstrumentationArgument("emulator:::::")
      }
    val urlParseErrorMessage = runCatching { URL("https://::::") }.exceptionOrNull()!!.message!!
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("emulator:::::")
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("invalid", ignoreCase = true)
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText("DATA_CONNECT_BACKEND")
    assertThat(exception).hasMessageThat().containsWithNonAdjacentText(urlParseErrorMessage)
  }
}
