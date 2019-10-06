// Copyright 2018 Google LLC
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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Base64;
import androidx.annotation.NonNull;
import com.google.firebase.storage.network.connection.HttpURLConnectionFactory;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import org.junit.Assert;
import org.junit.ComparisonFailure;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.exceptions.base.MockitoAssertionError;

public class MockConnectionFactory implements HttpURLConnectionFactory {
  private final boolean binaryBody;
  private HttpURLConnection oldMock;
  private List<String> verifications = new ArrayList<>();
  private final BufferedReader br;
  private final Semaphore pauseSemaphore = new Semaphore(0);
  private int lineCount = 0;
  private int pauseRecord = Integer.MAX_VALUE;
  private int currentRecord = 0;;

  public MockConnectionFactory(String testName, boolean binaryBody) {
    this.binaryBody = binaryBody;
    InputStream file = getResFile(testName + "_network.txt");
    try {
      br = new BufferedReader(new InputStreamReader(file, "UTF-8"));
      String line;
      // skip to first <new>
      while ((line = br.readLine()) != null) {
        if ("<new>".equalsIgnoreCase(line)) {
          break;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setPauseRecord(int pauseRecord) {
    this.pauseRecord = pauseRecord;
  }

  public Semaphore getSemaphore() {
    return pauseSemaphore;
  }

  private static InputStream getResFile(String fileName) {
    ClassLoader classLoader = MockConnectionFactory.class.getClassLoader();
    return classLoader.getResourceAsStream("activitylogs/" + fileName);
  }

  @Override
  public synchronized HttpURLConnection createInstance(@NonNull URL url) throws IOException {
    verifyOldMock();
    HttpURLConnection mock = Mockito.mock(HttpURLConnection.class);
    final Map<String, List<String>> headers = new HashMap<>();

    String line;
    try {
      while ((line = br.readLine()) != null) {
        lineCount++;

        if ("<new>".equalsIgnoreCase(line)) {
          break;
        }
        int colon = line.indexOf(':');
        if (colon == -1) {
          break;
        }
        String key = line.substring(0, colon);
        String value = line.substring(colon + 1);

        if ("Url".equalsIgnoreCase(key)) {
          Assert.assertEquals(value, url.toString());
        } else if ("getOutputStream".equalsIgnoreCase(key)) {
          MockOutputStreamHelper outputStream =
              new MockOutputStreamHelper(new ByteArrayOutputStream());
          if ("Exception".equals(value)) {
            outputStream.injectExceptionAt(1024); // We allow the header to be written.
          }
          when(mock.getOutputStream()).thenReturn(outputStream);
        } else if ("getResponseCode".equalsIgnoreCase(key)) {
          when(mock.getResponseCode()).thenReturn(Integer.valueOf(value));
        } else if ("getHeaderFields".equalsIgnoreCase(key)) {
          when(mock.getHeaderFields()).thenReturn(headers);
        } else if ("getInputStream".equalsIgnoreCase(key)) {
          byte[] responseData;
          boolean injectException = false;

          if (value.endsWith(":Exception")) {
            value = value.substring(0, value.lastIndexOf(":Exception"));
            injectException = true;
          }

          if (binaryBody) {
            responseData = Base64.decode(value.getBytes("UTF-8"), Base64.NO_WRAP);
          } else {
            responseData = value.getBytes("UTF-8");
          }

          System.out.println("Returning byte mCount:" + responseData.length);
          MockInputStreamHelper inputStream = new MockInputStreamHelper(responseData);
          if (injectException) {
            inputStream.injectExceptionAt(responseData.length);
          }
          when(mock.getInputStream()).thenReturn(inputStream);
        } else if (key.charAt(0) == ' ') {
          key = key.substring(1);
          value = value.substring(1, value.length() - 1);
          if (Objects.equals(key, "Content-Length")) {
            int integerLength = Integer.parseInt(value);
            when(mock.getContentLength()).thenReturn(integerLength);
            long longLength = Long.parseLong(value);
            when(mock.getContentLengthLong()).thenReturn(longLength);
          } else if (Objects.equals(key, "Content-Type")) {
            when(mock.getContentType()).thenReturn(value);
          } else if (Objects.equals(key, "Content-Encoding")) {
            when(mock.getContentEncoding()).thenReturn(value);
          }
          headers.put(key, Collections.singletonList(value));
        } else {
          verifications.add(line);
        }
      }
    } catch (ComparisonFailure | IllegalArgumentException e) {
      System.out.println("**** Error at line:" + lineCount);
      throw e;
    }
    currentRecord++;
    if (currentRecord == pauseRecord) {
      Mockito.doAnswer(
              invocation -> {
                try {
                  pauseSemaphore.acquire();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return null;
              })
          .when(mock)
          .disconnect();
    }

    oldMock = mock;
    return oldMock;
  }

  public synchronized void verifyOldMock() {
    if (oldMock == null || verifications.isEmpty()) {
      return;
    }
    List<String> requestPropertyKeys = new ArrayList<>();
    List<String> requestPropertyValues = new ArrayList<>();

    try {
      for (String line : verifications) {
        int colon = line.indexOf(':');
        if (colon == -1) {
          break;
        }
        String key = line.substring(0, colon);
        String value = line.substring(colon + 1);

        if (key.equalsIgnoreCase("SetRequestMethod")) {
          try {
            verify(oldMock).setRequestMethod(value);
          } catch (ProtocolException e) {
            throw new RuntimeException(e);
          }
        } else if (key.equalsIgnoreCase("setRequestProperty")) {
          int comma = value.indexOf(',');
          if (comma != -1) {
            key = value.substring(0, comma);
            value = value.substring(comma + 1);
            requestPropertyKeys.add(key);
            requestPropertyValues.add(value);
          }
        } else if (key.equalsIgnoreCase("setDoOutput")) {
          verify(oldMock).setDoOutput("true".equals(value));
        } else if (key.equalsIgnoreCase("setUseCaches")) {
          verify(oldMock).setUseCaches("true".equals(value));
        } else if (key.equalsIgnoreCase("setDoInput")) {
          verify(oldMock).setDoInput("true".equals(value));
        } else if (key.equalsIgnoreCase("disconnect")) {
          verify(oldMock).disconnect();
        }
      }
      ArgumentCaptor<String> keyCapture = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> valueCapture = ArgumentCaptor.forClass(String.class);

      verify(oldMock, times(requestPropertyKeys.size()))
          .setRequestProperty(keyCapture.capture(), valueCapture.capture());

      for (int i = 0; i < requestPropertyKeys.size(); i++) {
        int keyIndex = keyCapture.getAllValues().indexOf(requestPropertyKeys.get(i));
        Assert.assertTrue(keyIndex != -1);
        Assert.assertEquals(
            "Request property differs for:" + requestPropertyKeys.get(i),
            requestPropertyValues.get(i),
            valueCapture.getAllValues().get(keyIndex));
      }
    } catch (MockitoAssertionError | ComparisonFailure e) {
      System.out.println("**********Error in network Line: " + lineCount);
      throw e;
    }

    oldMock = null;
    verifications.clear();
  }
}
