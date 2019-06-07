// Copyright 2019 Google LLC
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

package com.google.firebase.storage.network;

import android.os.Build;
import android.util.Base64;
import androidx.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * RecordingHttpURLConnection records network communication when running tests manually against
 * staging. These recordings are used later to play back for running unit tests without touching the
 * server. If the client or server changes, the recordings can be regenerated using the test_app
 * (there is a button for each recording which will save to a file on the emulator).
 */
public class RecordingHttpURLConnection extends HttpURLConnection {

  private HttpURLConnection mWrappedConnection;
  private StringBuilder mBuilder;
  private ConnectionInjector injector;
  private int rangeOffset = 0;

  public RecordingHttpURLConnection(
      HttpURLConnection wrappedConnection,
      StringBuilder builder,
      @Nullable ConnectionInjector injector) {
    super(wrappedConnection.getURL());
    mWrappedConnection = wrappedConnection;
    mBuilder = builder;
    mBuilder.append("\n<new>");
    mBuilder.append("\nUrl:").append(wrappedConnection.getURL().toString());
    this.injector = injector;
  }

  @Override
  public void setRequestMethod(String action) throws ProtocolException {
    mBuilder.append("\nsetRequestMethod:").append(action);
    mWrappedConnection.setRequestMethod(action);
  }

  @Override
  public void disconnect() {
    mBuilder.append("\ndisconnect:");
    mWrappedConnection.disconnect();
  }

  @Override
  public void setRequestProperty(String key, String value) {
    mWrappedConnection.setRequestProperty(key, value);
    if ("Authorization".equalsIgnoreCase(key)) {
      System.out.println("not storing auth for unit tests....");
      return;
    } else if ("X-Firebase-Storage-Version".equals(key)) {
      mBuilder
          .append("\nsetRequestProperty:")
          .append(key)
          .append(",")
          .append("Android/[No Gmscore]");
      return;
    } else if ("Range".equals(key)) {
      rangeOffset = Integer.parseInt(value.substring("bytes=".length(), value.indexOf("-")));
    } else if ("X-Goog-Upload-Offset".equals(key)) {
      rangeOffset = Integer.parseInt(value);
    }

    mBuilder.append("\nsetRequestProperty:").append(key).append(",").append(value);
  }

  @Override
  public void setDoOutput(boolean doOutput) {
    mBuilder.append("\nsetDoOutput:").append(doOutput);

    mWrappedConnection.setDoOutput(doOutput);
  }

  @Override
  public void setUseCaches(boolean useCaches) {
    mBuilder.append("\nsetUseCaches:").append(useCaches);

    mWrappedConnection.setUseCaches(useCaches);
  }

  @Override
  public void setDoInput(boolean doInput) {
    mBuilder.append("\nsetDoInput:").append(doInput);

    mWrappedConnection.setDoInput(doInput);
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    final int[] byteCount = new int[] {0};

    mBuilder.append("\ngetOutputStream:");
    return new OutputStream() {
      OutputStream outputStream = mWrappedConnection.getOutputStream();
      ByteArrayOutputStream debugStream = new ByteArrayOutputStream();

      @Override
      public void write(int i) throws IOException {
        write(new byte[] {(byte) i}, 0, 1);
      }

      @Override
      public synchronized void write(byte[] b, int off, int len) throws IOException {
        if (injector != null) {
          try {
            injector.injectOutputStream(
                byteCount[0] + rangeOffset, byteCount[0] + rangeOffset + len); // This might throw.
          } catch (Exception e) {
            mBuilder.append("Exception");
            throw e;
          }
        }
        outputStream.write(b, off, len);
        debugStream.write(b, off, len);
        byteCount[0] += len;
      }

      @Override
      public void flush() throws IOException {
        outputStream.flush();
      }

      @Override
      public void close() throws IOException {
        try {
          mBuilder.append(Base64.encodeToString(debugStream.toByteArray(), Base64.NO_WRAP));
          outputStream.close();
        } catch (Exception ignore) {
          // Ignoring exception from RetryableSink.
        }
      }
    };
  }

  @Override
  public int getResponseCode() throws IOException {
    int result = mWrappedConnection.getResponseCode();
    //        if (rnd.nextInt(100) < 50) {
    //            result = 500;
    //        }
    mBuilder.append("\ngetResponseCode:").append(result);
    return result;
  }

  @Override
  public Map<String, List<String>> getHeaderFields() {
    mBuilder.append("\ngetHeaderFields:");

    Map<String, List<String>> headers = mWrappedConnection.getHeaderFields();
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      mBuilder.append("\n ").append(entry.getKey()).append(":");
      for (String val : entry.getValue()) {
        mBuilder.append("[").append(val).append("]");
      }
    }

    return headers;
  }

  @Override
  public InputStream getInputStream() throws IOException {
    BufferedInputStream input = new BufferedInputStream(mWrappedConnection.getInputStream());

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    int size = 1024;
    int len;
    int offset = 0;
    byte[] buf = new byte[size];
    boolean injectedException = false;
    try {
      while ((len = input.read(buf, 0, size)) != -1) {
        if (injector != null) {
          injector.injectInputStream(
              rangeOffset + offset, rangeOffset + offset + len); // This might throw.
        }
        bos.write(buf, 0, len);
        offset += len;
      }
    } catch (IOException ignore) {
      // Catching injected IOException.
      injectedException = true;
    }
    buf = bos.toByteArray();

    mBuilder.append("\ngetInputStream:").append(Base64.encodeToString(buf, Base64.NO_WRAP));

    MockInputStreamHelper inputStream = new MockInputStreamHelper(buf);
    if (injectedException) {
      mBuilder.append(":Exception");
      inputStream.injectExceptionAt(offset);
    }

    return inputStream;
  }

  @Override
  public InputStream getErrorStream() {
    try {
      InputStream resultStream = mWrappedConnection.getErrorStream();

      StringBuilder sb = new StringBuilder();
      if (resultStream != null) {
        BufferedReader br = new BufferedReader(new InputStreamReader((resultStream)));
        String input;
        while ((input = br.readLine()) != null) {
          sb.append(input);
        }
      }
      String result = sb.toString();
      mBuilder.append("\ngetErrorStream:").append(result);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        return new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8));
      }
      return new ByteArrayInputStream(result.getBytes());
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public boolean usingProxy() {
    return mWrappedConnection.usingProxy();
  }

  @Override
  public void connect() throws IOException {
    mWrappedConnection.connect();
  }

  public static void install(StringBuilder networkBuilder, ConnectionInjector injector) {
    NetworkRequest.connectionFactory =
        url ->
            new RecordingHttpURLConnection(
                (HttpURLConnection) url.openConnection(), networkBuilder, injector);
  }
}
