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
import com.google.firebase.perf.impl.NetworkRequestMetricBuilder;
import com.google.firebase.perf.logging.AndroidLogger;
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

  private final HttpURLConnection mHttpUrlConnection;
  private final NetworkRequestMetricBuilder mBuilder;
  private long mTimeRequested = -1;
  private long mTimeToResponseInitiated = -1;
  private final Timer mTimer;

  /**
   * Instrumented HttpURLConnectionBase object
   *
   * @param connection - HttpsURLConnection is a subclass of HttpURLConnection
   * @param timer
   */
  public InstrURLConnectionBase(
      HttpURLConnection connection, Timer timer, NetworkRequestMetricBuilder builder) {
    mHttpUrlConnection = connection;
    mBuilder = builder;
    mTimer = timer;
    mBuilder.setUrl(mHttpUrlConnection.getURL().toString());
  }

  public void connect() throws IOException {
    if (mTimeRequested == -1) {
      mTimer.reset();
      mTimeRequested = mTimer.getMicros();
      mBuilder.setRequestStartTimeMicros(mTimeRequested);
    }
    try {
      mHttpUrlConnection.connect();
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @SuppressWarnings("UrlConnectionChecker")
  public void disconnect() {
    mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
    mBuilder.build();
    mHttpUrlConnection.disconnect();
  }

  public Object getContent() throws IOException {
    updateRequestInfo();
    mBuilder.setHttpResponseCode(mHttpUrlConnection.getResponseCode());

    Object content;
    try {
      content = mHttpUrlConnection.getContent();
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }

    if (content instanceof InputStream) {
      mBuilder.setResponseContentType(mHttpUrlConnection.getContentType());
      content = new InstrHttpInputStream((InputStream) content, mBuilder, mTimer);
    } else {
      mBuilder.setResponseContentType(mHttpUrlConnection.getContentType());
      mBuilder.setResponsePayloadBytes(mHttpUrlConnection.getContentLength());
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      mBuilder.build();
    }
    return content;
  }

  @SuppressWarnings("rawtypes")
  public Object getContent(final Class[] classes) throws IOException {
    updateRequestInfo();
    mBuilder.setHttpResponseCode(mHttpUrlConnection.getResponseCode());

    Object content;
    try {
      content = mHttpUrlConnection.getContent(classes);
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }

    if (content instanceof InputStream) {
      mBuilder.setResponseContentType(mHttpUrlConnection.getContentType());
      content = new InstrHttpInputStream((InputStream) content, mBuilder, mTimer);
    } else {
      mBuilder.setResponseContentType(mHttpUrlConnection.getContentType());
      mBuilder.setResponsePayloadBytes(mHttpUrlConnection.getContentLength());
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      mBuilder.build();
    }

    return content;
  }

  public InputStream getInputStream() throws IOException {
    updateRequestInfo();
    mBuilder.setHttpResponseCode(mHttpUrlConnection.getResponseCode());
    mBuilder.setResponseContentType(mHttpUrlConnection.getContentType());

    try {
      final InputStream inputStream = mHttpUrlConnection.getInputStream();
      return new InstrHttpInputStream(inputStream, mBuilder, mTimer);
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  public long getLastModified() {
    updateRequestInfo();
    final long value = mHttpUrlConnection.getLastModified();
    return value;
  }

  public OutputStream getOutputStream() throws IOException {
    try {
      return new InstrHttpOutputStream(mHttpUrlConnection.getOutputStream(), mBuilder, mTimer);
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  public Permission getPermission() throws IOException {
    try {
      return mHttpUrlConnection.getPermission();
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  public int getResponseCode() throws IOException {
    updateRequestInfo();
    if (mTimeToResponseInitiated == -1) {
      mTimeToResponseInitiated = mTimer.getDurationMicros();
      mBuilder.setTimeToResponseInitiatedMicros(mTimeToResponseInitiated);
    }
    try {
      final int code = mHttpUrlConnection.getResponseCode();
      mBuilder.setHttpResponseCode(code);
      return code;
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  public String getResponseMessage() throws IOException {
    updateRequestInfo();
    if (mTimeToResponseInitiated == -1) {
      mTimeToResponseInitiated = mTimer.getDurationMicros();
      mBuilder.setTimeToResponseInitiatedMicros(mTimeToResponseInitiated);
    }
    try {
      final String message = mHttpUrlConnection.getResponseMessage();
      mBuilder.setHttpResponseCode(mHttpUrlConnection.getResponseCode());
      return message;
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  public long getExpiration() {
    updateRequestInfo();
    final long exp = mHttpUrlConnection.getExpiration();
    return exp;
  }

  public String getHeaderField(final int n) {
    updateRequestInfo();
    final String value = mHttpUrlConnection.getHeaderField(n);
    return value;
  }

  public String getHeaderField(final String name) {
    updateRequestInfo();
    final String value = mHttpUrlConnection.getHeaderField(name);
    return value;
  }

  public long getHeaderFieldDate(final String name, final long defaultDate) {
    updateRequestInfo();
    final long value = mHttpUrlConnection.getHeaderFieldDate(name, defaultDate);
    return value;
  }

  public int getHeaderFieldInt(final String name, final int defaultInt) {
    updateRequestInfo();
    final int value = mHttpUrlConnection.getHeaderFieldInt(name, defaultInt);
    return value;
  }

  public long getHeaderFieldLong(final String name, final long defaultLong) {
    updateRequestInfo();
    long value = 0;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
      value = mHttpUrlConnection.getHeaderFieldLong(name, defaultLong);
    }
    return value;
  }

  public String getHeaderFieldKey(final int n) {
    updateRequestInfo();
    final String value = mHttpUrlConnection.getHeaderFieldKey(n);
    return value;
  }

  public Map<String, List<String>> getHeaderFields() {
    updateRequestInfo();
    final Map<String, List<String>> value = mHttpUrlConnection.getHeaderFields();
    return value;
  }

  public String getContentEncoding() {
    updateRequestInfo();
    return mHttpUrlConnection.getContentEncoding();
  }

  public int getContentLength() {
    updateRequestInfo();
    return mHttpUrlConnection.getContentLength();
  }

  public long getContentLengthLong() {
    updateRequestInfo();
    long contentLength = 0;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      contentLength = mHttpUrlConnection.getContentLengthLong();
    }

    return contentLength;
  }

  public String getContentType() {
    updateRequestInfo();
    return mHttpUrlConnection.getContentType();
  }

  public long getDate() {
    updateRequestInfo();
    return mHttpUrlConnection.getDate();
  }

  public void addRequestProperty(final String key, final String value) {
    mHttpUrlConnection.addRequestProperty(key, value);
  }

  @Override
  public boolean equals(final Object obj) {
    return mHttpUrlConnection.equals(obj);
  }

  public boolean getAllowUserInteraction() {
    return mHttpUrlConnection.getAllowUserInteraction();
  }

  public int getConnectTimeout() {
    return mHttpUrlConnection.getConnectTimeout();
  }

  public boolean getDefaultUseCaches() {
    return mHttpUrlConnection.getDefaultUseCaches();
  }

  public boolean getDoInput() {
    return mHttpUrlConnection.getDoInput();
  }

  public boolean getDoOutput() {
    return mHttpUrlConnection.getDoOutput();
  }

  public InputStream getErrorStream() {
    updateRequestInfo();
    try {
      mBuilder.setHttpResponseCode(mHttpUrlConnection.getResponseCode());
    } catch (IOException e) {
      logger.debug("IOException thrown trying to obtain the response code");
    }
    InputStream errorStream = mHttpUrlConnection.getErrorStream();
    if (errorStream != null) {
      return new InstrHttpInputStream(errorStream, mBuilder, mTimer);
    }
    return errorStream;
  }

  public long getIfModifiedSince() {
    return mHttpUrlConnection.getIfModifiedSince();
  }

  public boolean getInstanceFollowRedirects() {
    return mHttpUrlConnection.getInstanceFollowRedirects();
  }

  public int getReadTimeout() {
    return mHttpUrlConnection.getReadTimeout();
  }

  public String getRequestMethod() {
    return mHttpUrlConnection.getRequestMethod();
  }

  public Map<String, List<String>> getRequestProperties() {
    return mHttpUrlConnection.getRequestProperties();
  }

  public String getRequestProperty(final String key) {
    return mHttpUrlConnection.getRequestProperty(key);
  }

  public URL getURL() {
    return mHttpUrlConnection.getURL();
  }

  public boolean getUseCaches() {
    return mHttpUrlConnection.getUseCaches();
  }

  @Override
  public int hashCode() {
    return mHttpUrlConnection.hashCode();
  }

  public void setAllowUserInteraction(final boolean allowuserinteraction) {
    mHttpUrlConnection.setAllowUserInteraction(allowuserinteraction);
  }

  public void setChunkedStreamingMode(final int chunklen) {
    mHttpUrlConnection.setChunkedStreamingMode(chunklen);
  }

  public void setConnectTimeout(final int timeout) {
    mHttpUrlConnection.setConnectTimeout(timeout);
  }

  public void setDefaultUseCaches(final boolean defaultusecaches) {
    mHttpUrlConnection.setDefaultUseCaches(defaultusecaches);
  }

  public void setDoInput(final boolean doinput) {
    mHttpUrlConnection.setDoInput(doinput);
  }

  public void setDoOutput(final boolean dooutput) {
    mHttpUrlConnection.setDoOutput(dooutput);
  }

  public void setFixedLengthStreamingMode(final int contentLength) {
    mHttpUrlConnection.setFixedLengthStreamingMode(contentLength);
  }

  public void setFixedLengthStreamingMode(final long contentLength) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      mHttpUrlConnection.setFixedLengthStreamingMode(contentLength);
    }
  }

  public void setIfModifiedSince(final long ifmodifiedsince) {
    mHttpUrlConnection.setIfModifiedSince(ifmodifiedsince);
  }

  public void setInstanceFollowRedirects(final boolean followRedirects) {
    mHttpUrlConnection.setInstanceFollowRedirects(followRedirects);
  }

  public void setReadTimeout(final int timeout) {
    mHttpUrlConnection.setReadTimeout(timeout);
  }

  public void setRequestMethod(final String method) throws ProtocolException {
    mHttpUrlConnection.setRequestMethod(method);
  }

  public void setRequestProperty(final String key, final String value) {
    if (USER_AGENT_PROPERTY.equalsIgnoreCase(key)) {
      mBuilder.setUserAgent(value);
    }

    mHttpUrlConnection.setRequestProperty(key, value);
  }

  public void setUseCaches(final boolean usecaches) {
    mHttpUrlConnection.setUseCaches(usecaches);
  }

  @Override
  public String toString() {
    return mHttpUrlConnection.toString();
  }

  public boolean usingProxy() {
    return mHttpUrlConnection.usingProxy();
  }

  private void updateRequestInfo() {
    if (mTimeRequested == -1) {
      mTimer.reset();
      mTimeRequested = mTimer.getMicros();
      mBuilder.setRequestStartTimeMicros(mTimeRequested);
    }
    final String method = getRequestMethod();
    if (method != null) {
      // TODO(b/177945490): Check special case if you send a post but nothing in the output
      mBuilder.setHttpMethod(method);
    } else {
      // Default POST if getDoOutput, GET otherwise.
      if (getDoOutput()) {
        mBuilder.setHttpMethod("POST");
      } else {
        mBuilder.setHttpMethod("GET");
      }
    }
  }
}
