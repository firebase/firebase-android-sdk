// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.network;

import androidx.annotation.Keep;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.util.URLWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import javax.net.ssl.HttpsURLConnection;

/**
 * These are the functions that are bytecode instrumented into the apk and methods to collect
 * information for the NetworkRequestMetric for UrlConnection.
 */
public class FirebasePerfUrlConnection {

  private FirebasePerfUrlConnection() {}

  // Bytecode instrumented functions
  /**
   * Instrumented function for UrlConnection.getInputStream()
   *
   * @return InputStream the input stream from the url connection
   * @throws IOException if there is a problem with opening the connection or using the connection
   *     to open a stream
   */
  @Keep
  public static InputStream openStream(final URL url) throws IOException {
    return openStream(new URLWrapper(url), TransportManager.getInstance(), new Timer());
  }

  // Functions to collect information for the Network Request Metric

  /**
   * Send network request metric for UrlConnection.getInputStream()
   *
   * @return InputStream the input stream from the url connection
   * @throws IOException if there is a problem with opening the connection or using the connection
   *     to open a stream
   */
  static InputStream openStream(URLWrapper wrapper, TransportManager transportManager, Timer timer)
      throws IOException {
    if (!TransportManager.getInstance().isInitialized()) {
      return wrapper.openConnection().getInputStream();
    }
    timer.reset();
    long startTime = timer.getMicros();
    NetworkRequestMetricBuilder builder = NetworkRequestMetricBuilder.builder(transportManager);
    try {
      URLConnection connection = wrapper.openConnection();
      if (connection instanceof HttpsURLConnection) {
        return new InstrHttpsURLConnection((HttpsURLConnection) connection, timer, builder)
            .getInputStream();
      } else if (connection instanceof HttpURLConnection) {
        return new InstrHttpURLConnection((HttpURLConnection) connection, timer, builder)
            .getInputStream();
      }
      return connection.getInputStream();
    } catch (IOException e) {
      builder.setRequestStartTimeMicros(startTime);
      builder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      builder.setUrl(wrapper.toString());
      NetworkRequestMetricBuilderUtil.logError(builder);
      throw e;
    }
  }

  /**
   * Instrumented function for UrlConnection.getContent()
   *
   * @return the object fetched
   * @throws IOException if there is a problem with opening the connection or using the connection
   *     to get the content
   */
  @Keep
  public static Object getContent(final URL url) throws IOException {
    return getContent(new URLWrapper(url), TransportManager.getInstance(), new Timer());
  }

  /**
   * Instrumented function for UrlConnection.getContent(classes)
   *
   * @return the object fetched
   * @throws IOException if there is a problem with opening the connection or using the connection
   *     to get the content
   */
  @Keep
  public static Object getContent(final URL url, @SuppressWarnings("rawtypes") final Class[] types)
      throws IOException {
    return getContent(new URLWrapper(url), types, TransportManager.getInstance(), new Timer());
  }

  /**
   * Send network request metric for UrlConnection.getContent()
   *
   * @return the object fetched
   * @throws IOException if there is a problem with opening the connection or using the connection
   *     to get the content
   */
  static Object getContent(final URLWrapper wrapper, TransportManager transportManager, Timer timer)
      throws IOException {
    timer.reset();
    long startTime = timer.getMicros();
    NetworkRequestMetricBuilder builder = NetworkRequestMetricBuilder.builder(transportManager);
    try {
      URLConnection connection = wrapper.openConnection();
      if (connection instanceof HttpsURLConnection) {
        return new InstrHttpsURLConnection((HttpsURLConnection) connection, timer, builder)
            .getContent();
      } else if (connection instanceof HttpURLConnection) {
        return new InstrHttpURLConnection((HttpURLConnection) connection, timer, builder)
            .getContent();
      }
      return connection.getContent();
    } catch (IOException e) {
      builder.setRequestStartTimeMicros(startTime);
      builder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      builder.setUrl(wrapper.toString());
      NetworkRequestMetricBuilderUtil.logError(builder);
      throw e;
    }
  }

  /**
   * Send network request metric for UrlConnection.getContent(classes)
   *
   * @return the object fetched
   * @throws IOException if there is a problem with opening the connection or using the connection
   *     to get the content
   */
  static Object getContent(
      final URLWrapper wrapper,
      @SuppressWarnings("rawtypes") final Class[] types,
      TransportManager transportManager,
      Timer timer)
      throws IOException {
    timer.reset();
    long startTime = timer.getMicros();
    NetworkRequestMetricBuilder builder = NetworkRequestMetricBuilder.builder(transportManager);
    try {
      URLConnection connection = wrapper.openConnection();
      if (connection instanceof HttpsURLConnection) {
        return new InstrHttpsURLConnection((HttpsURLConnection) connection, timer, builder)
            .getContent(types);
      } else if (connection instanceof HttpURLConnection) {
        return new InstrHttpURLConnection((HttpURLConnection) connection, timer, builder)
            .getContent(types);
      }
      return connection.getContent(types);
    } catch (IOException e) {
      builder.setRequestStartTimeMicros(startTime);
      builder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      builder.setUrl(wrapper.toString());
      NetworkRequestMetricBuilderUtil.logError(builder);
      throw e;
    }
  }

  /**
   * Instrumented function for UrlConnection.getContent(classes)
   *
   * @return the instrumented urlConnection object
   * @throws IOException if there is a problem with calling methods on a URLConnection
   */
  @Keep
  public static Object instrument(Object connection) throws IOException {
    if (connection instanceof HttpsURLConnection) {
      return new InstrHttpsURLConnection(
          (HttpsURLConnection) connection,
          new Timer(),
          NetworkRequestMetricBuilder.builder(TransportManager.getInstance()));
    } else if (connection instanceof HttpURLConnection) {
      return new InstrHttpURLConnection(
          (HttpURLConnection) connection,
          new Timer(),
          NetworkRequestMetricBuilder.builder(TransportManager.getInstance()));
    }
    return connection;
  }
}
