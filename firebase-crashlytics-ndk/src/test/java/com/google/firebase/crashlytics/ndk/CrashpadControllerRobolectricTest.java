// Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.ndk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.ApplicationExitInfo;
import android.os.Build.VERSION_CODES;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests that the trace stream taken from an {@link ApplicationExitInfo} is always released.
 *
 * <p>The stream returned by {@link ApplicationExitInfo#getTraceInputStream()} wraps a {@code
 * ParcelFileDescriptor}, so failing to close it leaks a file descriptor until finalization and trips
 * StrictMode's {@code detectLeakedClosableObjects()}. See
 * https://github.com/firebase/firebase-android-sdk/issues/8510.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = VERSION_CODES.TIRAMISU)
public class CrashpadControllerRobolectricTest {

  private static final String TRACE = "----- pid 1234 -----\nnative crash trace\n----- end -----\n";

  @Test
  public void getTraceFileFromApplicationExitInfo_closesTraceInputStream() throws IOException {
    CloseTrackingInputStream traceInputStream =
        new CloseTrackingInputStream(
            new ByteArrayInputStream(TRACE.getBytes(StandardCharsets.UTF_8)));
    ApplicationExitInfo applicationExitInfo = mock(ApplicationExitInfo.class);
    when(applicationExitInfo.getTraceInputStream()).thenReturn(traceInputStream);

    String traceFile = CrashpadController.getTraceFileFromApplicationExitInfo(applicationExitInfo);

    assertTrue("The trace input stream must be closed.", traceInputStream.isClosed());
    assertEquals(TRACE, gunzipAndDecode(traceFile));
  }

  @Test
  public void getTraceFileFromApplicationExitInfo_closesTraceInputStreamWhenReadFails()
      throws IOException {
    ThrowingInputStream traceInputStream = new ThrowingInputStream();
    ApplicationExitInfo applicationExitInfo = mock(ApplicationExitInfo.class);
    when(applicationExitInfo.getTraceInputStream()).thenReturn(traceInputStream);

    String traceFile = CrashpadController.getTraceFileFromApplicationExitInfo(applicationExitInfo);

    assertNull(traceFile);
    assertTrue(
        "The trace input stream must be closed even when reading it fails.",
        traceInputStream.isClosed());
  }

  @Test
  public void getTraceFileFromApplicationExitInfo_nullTraceInputStream_returnsNull()
      throws IOException {
    ApplicationExitInfo applicationExitInfo = mock(ApplicationExitInfo.class);
    when(applicationExitInfo.getTraceInputStream()).thenReturn(null);

    assertNull(CrashpadController.getTraceFileFromApplicationExitInfo(applicationExitInfo));
  }

  @Test
  public void getTraceFileFromApplicationExitInfo_traceInputStreamThrows_returnsNull()
      throws IOException {
    ApplicationExitInfo applicationExitInfo = mock(ApplicationExitInfo.class);
    when(applicationExitInfo.getTraceInputStream()).thenThrow(new IOException("no trace"));

    assertNull(CrashpadController.getTraceFileFromApplicationExitInfo(applicationExitInfo));
  }

  /** The caller owns the stream, so this helper must keep leaving it open. */
  @Test
  public void convertInputStreamToString_roundTripsWithoutClosingTheStream() throws IOException {
    CloseTrackingInputStream inputStream =
        new CloseTrackingInputStream(
            new ByteArrayInputStream(TRACE.getBytes(StandardCharsets.UTF_8)));

    String converted = CrashpadController.convertInputStreamToString(inputStream);

    assertEquals(TRACE, gunzipAndDecode(converted));
    assertFalse(inputStream.isClosed());
  }

  @Test
  public void convertInputStreamToString_nullInputStream_returnsNull() throws IOException {
    assertNull(CrashpadController.convertInputStreamToString(null));
  }

  /** Reverses {@code CrashpadController}'s gzip + base64 encoding of the trace file. */
  private static String gunzipAndDecode(String traceFile) throws IOException {
    byte[] gzipped = Base64.getDecoder().decode(traceFile);
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipped));
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] bytes = new byte[8192];
      int length;
      while ((length = gzip.read(bytes)) != -1) {
        out.write(bytes, 0, length);
      }
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  private static final class CloseTrackingInputStream extends FilterInputStream {
    private boolean closed = false;

    CloseTrackingInputStream(InputStream inputStream) {
      super(inputStream);
    }

    boolean isClosed() {
      return closed;
    }

    @Override
    public void close() throws IOException {
      closed = true;
      super.close();
    }
  }

  private static final class ThrowingInputStream extends InputStream {
    private boolean closed = false;

    boolean isClosed() {
      return closed;
    }

    @Override
    public int read() throws IOException {
      throw new IOException("Failed to read the trace");
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      throw new IOException("Failed to read the trace");
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
