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

import android.os.Build;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.util.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.security.Permission;
import java.util.List;
import java.util.Map;

/**
 * These methods are the common instrumented methods between HttpURLConnection and
 * HttpsURLConnection.
 */
class InstrURLConnectionBase {

  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private static final String USER_AGENT_PROPERTY = "User-Agent";

  private final HttpURLConnection httpUrlConnection;
  private final NetworkRequestMetricBuilder networkMetricBuilder;

  private long timeRequestedInMicros = -1;
  private long timeToResponseInitiatedInMicros = -1;

  private final Timer timer;

  /**
   * Instrumented HttpURLConnectionBase object
   *
   * @param connection - HttpsURLConnection is a subclass of HttpURLConnection
   * @param timer
   */
  public InstrURLConnectionBase(
      HttpURLConnection connection, Timer timer, NetworkRequestMetricBuilder builder) {
    httpUrlConnection = connection;
    networkMetricBuilder = builder;
    this.timer = timer;
    networkMetricBuilder.setUrl(httpUrlConnection.getURL().toString());
  }

  public void connect() throws IOException {
    if (timeRequestedInMicros == -1) {
      timer.reset();
      timeRequestedInMicros = timer.getMicros();
      networkMetricBuilder.setRequestStartTimeMicros(timeRequestedInMicros);
    }
    try {
      httpUrlConnection.connect();
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @SuppressWarnings("UrlConnectionChecker")
  public void disconnect() {
    networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
    networkMetricBuilder.build();
    httpUrlConnection.disconnect();
  }

  public Object getContent() throws IOException {
    updateRequestInfo();
    networkMetricBuilder.setHttpResponseCode(httpUrlConnection.getResponseCode());

    Object content;
    try {
      content = httpUrlConnection.getContent();
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }

    if (content instanceof InputStream) {
      networkMetricBuilder.setResponseContentType(httpUrlConnection.getContentType());
      content = new InstrHttpInputStream((InputStream) content, networkMetricBuilder, timer);
    } else {
      networkMetricBuilder.setResponseContentType(httpUrlConnection.getContentType());
      networkMetricBuilder.setResponsePayloadBytes(httpUrlConnection.getContentLength());
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      networkMetricBuilder.build();
    }
    return content;
  }

  @SuppressWarnings("rawtypes")
  public Object getContent(final Class[] classes) throws IOException {
    updateRequestInfo();
    networkMetricBuilder.setHttpResponseCode(httpUrlConnection.getResponseCode());

    Object content;
    try {
      content = httpUrlConnection.getContent(classes);
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }

    if (content instanceof InputStream) {
      networkMetricBuilder.setResponseContentType(httpUrlConnection.getContentType());
      content = new InstrHttpInputStream((InputStream) content, networkMetricBuilder, timer);
    } else {
      networkMetricBuilder.setResponseContentType(httpUrlConnection.getContentType());
      networkMetricBuilder.setResponsePayloadBytes(httpUrlConnection.getContentLength());
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      networkMetricBuilder.build();
    }

    return content;
  }

  public InputStream getInputStream() throws IOException {
    updateRequestInfo();
    networkMetricBuilder.setHttpResponseCode(httpUrlConnection.getResponseCode());
    networkMetricBuilder.setResponseContentType(httpUrlConnection.getContentType());

    try {
      final InputStream inputStream = httpUrlConnection.getInputStream();
      // Make sure we don't pass in a null into InstrHttpInputStream, since InstrHttpInputStream is
      // not null-safe.
      if (inputStream != null) {
        return new InstrHttpInputStream(inputStream, networkMetricBuilder, timer);
      }
      // Only reached when inputStream is null
      return inputStream;
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  public long getLastModified() {
    updateRequestInfo();
    final long value = httpUrlConnection.getLastModified();
    return value;
  }

  public OutputStream getOutputStream() throws IOException {
    try {
      final OutputStream outputStream = httpUrlConnection.getOutputStream();
      // Make sure we don't pass in a null into InstrHttpOutputStream, since InstrHttpOutputStream
      // is not null-safe.
      if (outputStream != null) {
        return new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer);
      }
      // Only reached when outputStream is null
      return outputStream;
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  public Permission getPermission() throws IOException {
    try {
      return httpUrlConnection.getPermission();
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  public int getResponseCode() throws IOException {
    updateRequestInfo();
    if (timeToResponseInitiatedInMicros == -1) {
      timeToResponseInitiatedInMicros = timer.getDurationMicros();
      networkMetricBuilder.setTimeToResponseInitiatedMicros(timeToResponseInitiatedInMicros);
    }
    try {
      final int code = httpUrlConnection.getResponseCode();
      networkMetricBuilder.setHttpResponseCode(code);
      return code;
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  public String getResponseMessage() throws IOException {
    updateRequestInfo();
    if (timeToResponseInitiatedInMicros == -1) {
      timeToResponseInitiatedInMicros = timer.getDurationMicros();
      networkMetricBuilder.setTimeToResponseInitiatedMicros(timeToResponseInitiatedInMicros);
    }
    try {
      final String message = httpUrlConnection.getResponseMessage();
      networkMetricBuilder.setHttpResponseCode(httpUrlConnection.getResponseCode());
      return message;
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  public long getExpiration() {
    updateRequestInfo();
    final long exp = httpUrlConnection.getExpiration();
    return exp;
  }

  public String getHeaderField(final int n) {
    updateRequestInfo();
    final String value = httpUrlConnection.getHeaderField(n);
    return value;
  }

  public String getHeaderField(final String name) {
    updateRequestInfo();
    final String value = httpUrlConnection.getHeaderField(name);
    return value;
  }

  public long getHeaderFieldDate(final String name, final long defaultDate) {
    updateRequestInfo();
    final long value = httpUrlConnection.getHeaderFieldDate(name, defaultDate);
    return value;
  }

  public int getHeaderFieldInt(final String name, final int defaultInt) {
    updateRequestInfo();
    final int value = httpUrlConnection.getHeaderFieldInt(name, defaultInt);
    return value;
  }

  public long getHeaderFieldLong(final String name, final long defaultLong) {
    updateRequestInfo();
    long value = 0;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
      value = httpUrlConnection.getHeaderFieldLong(name, defaultLong);
    }
    return value;
  }

  public String getHeaderFieldKey(final int n) {
    updateRequestInfo();
    final String value = httpUrlConnection.getHeaderFieldKey(n);
    return value;
  }

  public Map<String, List<String>> getHeaderFields() {
    updateRequestInfo();
    final Map<String, List<String>> value = httpUrlConnection.getHeaderFields();
    return value;
  }

  public String getContentEncoding() {
    updateRequestInfo();
    return httpUrlConnection.getContentEncoding();
  }

  public int getContentLength() {
    updateRequestInfo();
    return httpUrlConnection.getContentLength();
  }

  public long getContentLengthLong() {
    updateRequestInfo();
    long contentLength = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      contentLength = httpUrlConnection.getContentLengthLong();
    }

    return contentLength;
  }

  public String getContentType() {
    updateRequestInfo();
    return httpUrlConnection.getContentType();
  }

  public long getDate() {
    updateRequestInfo();
    return httpUrlConnection.getDate();
  }

  public void addRequestProperty(final String key, final String value) {
    httpUrlConnection.addRequestProperty(key, value);
  }

  @Override
  public boolean equals(final Object obj) {
    return httpUrlConnection.equals(obj);
  }

  public boolean getAllowUserInteraction() {
    return httpUrlConnection.getAllowUserInteraction();
  }

  public int getConnectTimeout() {
    return httpUrlConnection.getConnectTimeout();
  }

  public boolean getDefaultUseCaches() {
    return httpUrlConnection.getDefaultUseCaches();
  }

  public boolean getDoInput() {
    return httpUrlConnection.getDoInput();
  }

  public boolean getDoOutput() {
    return httpUrlConnection.getDoOutput();
  }

  public InputStream getErrorStream() {
    updateRequestInfo();
    try {
      networkMetricBuilder.setHttpResponseCode(httpUrlConnection.getResponseCode());
    } catch (IOException e) {
      logger.debug("IOException thrown trying to obtain the response code");
    }
    InputStream errorStream = httpUrlConnection.getErrorStream();
    if (errorStream != null) {
      return new InstrHttpInputStream(errorStream, networkMetricBuilder, timer);
    }
    return errorStream;
  }

  public long getIfModifiedSince() {
    return httpUrlConnection.getIfModifiedSince();
  }

  public boolean getInstanceFollowRedirects() {
    return httpUrlConnection.getInstanceFollowRedirects();
  }

  public int getReadTimeout() {
    return httpUrlConnection.getReadTimeout();
  }

  public String getRequestMethod() {
    return httpUrlConnection.getRequestMethod();
  }

  public Map<String, List<String>> getRequestProperties() {
    return httpUrlConnection.getRequestProperties();
  }

  public String getRequestProperty(final String key) {
    return httpUrlConnection.getRequestProperty(key);
  }

  public URL getURL() {
    return httpUrlConnection.getURL();
  }

  public boolean getUseCaches() {
    return httpUrlConnection.getUseCaches();
  }

  @Override
  public int hashCode() {
    return httpUrlConnection.hashCode();
  }

  public void setAllowUserInteraction(final boolean allowuserinteraction) {
    httpUrlConnection.setAllowUserInteraction(allowuserinteraction);
  }

  public void setChunkedStreamingMode(final int chunklen) {
    httpUrlConnection.setChunkedStreamingMode(chunklen);
  }

  public void setConnectTimeout(final int timeout) {
    httpUrlConnection.setConnectTimeout(timeout);
  }

  public void setDefaultUseCaches(final boolean defaultusecaches) {
    httpUrlConnection.setDefaultUseCaches(defaultusecaches);
  }

  public void setDoInput(final boolean doinput) {
    httpUrlConnection.setDoInput(doinput);
  }

  public void setDoOutput(final boolean dooutput) {
    httpUrlConnection.setDoOutput(dooutput);
  }

  public void setFixedLengthStreamingMode(final int contentLength) {
    httpUrlConnection.setFixedLengthStreamingMode(contentLength);
  }

  public void setFixedLengthStreamingMode(final long contentLength) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      httpUrlConnection.setFixedLengthStreamingMode(contentLength);
    }
  }

  public void setIfModifiedSince(final long ifmodifiedsince) {
    httpUrlConnection.setIfModifiedSince(ifmodifiedsince);
  }

  public void setInstanceFollowRedirects(final boolean followRedirects) {
    httpUrlConnection.setInstanceFollowRedirects(followRedirects);
  }

  public void setReadTimeout(final int timeout) {
    httpUrlConnection.setReadTimeout(timeout);
  }

  public void setRequestMethod(final String method) throws ProtocolException {
    httpUrlConnection.setRequestMethod(method);
  }

  public void setRequestProperty(final String key, final String value) {
    if (USER_AGENT_PROPERTY.equalsIgnoreCase(key)) {
      networkMetricBuilder.setUserAgent(value);
    }

    httpUrlConnection.setRequestProperty(key, value);
  }

  public void setUseCaches(final boolean usecaches) {
    httpUrlConnection.setUseCaches(usecaches);
  }

  @Override
  public String toString() {
    return httpUrlConnection.toString();
  }

  public boolean usingProxy() {
    return httpUrlConnection.usingProxy();
  }

  private void updateRequestInfo() {
    if (timeRequestedInMicros == -1) {
      timer.reset();
      timeRequestedInMicros = timer.getMicros();
      networkMetricBuilder.setRequestStartTimeMicros(timeRequestedInMicros);
    }
    final String method = getRequestMethod();
    if (method != null) {
      // TODO(b/177945490): Check special case if you send a post but nothing in the output
      networkMetricBuilder.setHttpMethod(method);
    } else {
      // Default POST if getDoOutput, GET otherwise.
      if (getDoOutput()) {
        networkMetricBuilder.setHttpMethod("POST");
      } else {
        networkMetricBuilder.setHttpMethod("GET");
      }
    }
  }
}
