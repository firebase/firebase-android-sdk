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

import com.google.firebase.perf.impl.NetworkRequestMetricBuilder;
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
 * Collects network data from HttpURLConnection object. The HttpURLConnection request from the
 * developer's app is wrapped in these functions.
 */
public final class InstrHttpURLConnection extends HttpURLConnection {

  private final InstrURLConnectionBase mDelegate;

  /**
   * Instrumented HttpURLConnection object
   *
   * @param connection
   * @param timer
   */
  InstrHttpURLConnection(
      HttpURLConnection connection, Timer timer, NetworkRequestMetricBuilder builder) {
    super(connection.getURL());
    mDelegate = new InstrURLConnectionBase(connection, timer, builder);
  }

  @Override
  public void connect() throws IOException {
    mDelegate.connect();
  }

  @Override
  public void disconnect() {
    mDelegate.disconnect();
  }

  @Override
  public Object getContent() throws IOException {
    return mDelegate.getContent();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public Object getContent(final Class[] classes) throws IOException {
    return mDelegate.getContent(classes);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return mDelegate.getInputStream();
  }

  @Override
  public long getLastModified() {
    return mDelegate.getLastModified();
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return mDelegate.getOutputStream();
  }

  @Override
  public Permission getPermission() throws IOException {
    return mDelegate.getPermission();
  }

  @Override
  public int getResponseCode() throws IOException {
    return mDelegate.getResponseCode();
  }

  @Override
  public String getResponseMessage() throws IOException {
    return mDelegate.getResponseMessage();
  }

  @Override
  public long getExpiration() {
    return mDelegate.getExpiration();
  }

  @Override
  public String getHeaderField(final int n) {
    return mDelegate.getHeaderField(n);
  }

  @Override
  public String getHeaderField(final String name) {
    return mDelegate.getHeaderField(name);
  }

  @Override
  public long getHeaderFieldDate(final String name, final long defaultDate) {
    return mDelegate.getHeaderFieldDate(name, defaultDate);
  }

  @Override
  public int getHeaderFieldInt(final String name, final int defaultInt) {
    return mDelegate.getHeaderFieldInt(name, defaultInt);
  }

  @Override
  public long getHeaderFieldLong(final String name, final long defaultLong) {
    return mDelegate.getHeaderFieldLong(name, defaultLong);
  }

  @Override
  public String getHeaderFieldKey(final int n) {
    return mDelegate.getHeaderFieldKey(n);
  }

  @Override
  public Map<String, List<String>> getHeaderFields() {
    return mDelegate.getHeaderFields();
  }

  @Override
  public String getContentEncoding() {
    return mDelegate.getContentEncoding();
  }

  @Override
  public int getContentLength() {
    return mDelegate.getContentLength();
  }

  @Override
  public long getContentLengthLong() {
    return mDelegate.getContentLengthLong();
  }

  @Override
  public String getContentType() {
    return mDelegate.getContentType();
  }

  @Override
  public long getDate() {
    return mDelegate.getDate();
  }

  @Override
  public void addRequestProperty(final String key, final String value) {
    mDelegate.addRequestProperty(key, value);
  }

  @Override
  public boolean equals(final Object obj) {
    return mDelegate.equals(obj);
  }

  @Override
  public boolean getAllowUserInteraction() {
    return mDelegate.getAllowUserInteraction();
  }

  @Override
  public int getConnectTimeout() {
    return mDelegate.getConnectTimeout();
  }

  @Override
  public boolean getDefaultUseCaches() {
    return mDelegate.getDefaultUseCaches();
  }

  @Override
  public boolean getDoInput() {
    return mDelegate.getDoInput();
  }

  @Override
  public boolean getDoOutput() {
    return mDelegate.getDoOutput();
  }

  @Override
  public InputStream getErrorStream() {
    return mDelegate.getErrorStream();
  }

  @Override
  public long getIfModifiedSince() {
    return mDelegate.getIfModifiedSince();
  }

  @Override
  public boolean getInstanceFollowRedirects() {
    return mDelegate.getInstanceFollowRedirects();
  }

  @Override
  public int getReadTimeout() {
    return mDelegate.getReadTimeout();
  }

  @Override
  public String getRequestMethod() {
    return mDelegate.getRequestMethod();
  }

  @Override
  public Map<String, List<String>> getRequestProperties() {
    return mDelegate.getRequestProperties();
  }

  @Override
  public String getRequestProperty(final String key) {
    return mDelegate.getRequestProperty(key);
  }

  @Override
  public URL getURL() {
    return mDelegate.getURL();
  }

  @Override
  public boolean getUseCaches() {
    return mDelegate.getUseCaches();
  }

  @Override
  public int hashCode() {
    return mDelegate.hashCode();
  }

  @Override
  public void setAllowUserInteraction(final boolean allowuserinteraction) {
    mDelegate.setAllowUserInteraction(allowuserinteraction);
  }

  @Override
  public void setChunkedStreamingMode(final int chunklen) {
    mDelegate.setChunkedStreamingMode(chunklen);
  }

  @Override
  public void setConnectTimeout(final int timeout) {
    mDelegate.setConnectTimeout(timeout);
  }

  @Override
  public void setDefaultUseCaches(final boolean defaultusecaches) {
    mDelegate.setDefaultUseCaches(defaultusecaches);
  }

  @Override
  public void setDoInput(final boolean doinput) {
    mDelegate.setDoInput(doinput);
  }

  @Override
  public void setDoOutput(final boolean dooutput) {
    mDelegate.setDoOutput(dooutput);
  }

  @Override
  public void setFixedLengthStreamingMode(final int contentLength) {
    mDelegate.setFixedLengthStreamingMode(contentLength);
  }

  @Override
  public void setFixedLengthStreamingMode(final long contentLength) {
    mDelegate.setFixedLengthStreamingMode(contentLength);
  }

  @Override
  public void setIfModifiedSince(final long ifmodifiedsince) {
    mDelegate.setIfModifiedSince(ifmodifiedsince);
  }

  @Override
  public void setInstanceFollowRedirects(final boolean followRedirects) {
    mDelegate.setInstanceFollowRedirects(followRedirects);
  }

  @Override
  public void setReadTimeout(final int timeout) {
    mDelegate.setReadTimeout(timeout);
  }

  @Override
  public void setRequestMethod(final String method) throws ProtocolException {
    mDelegate.setRequestMethod(method);
  }

  @Override
  public void setRequestProperty(final String key, final String value) {
    mDelegate.setRequestProperty(key, value);
  }

  @Override
  public void setUseCaches(final boolean usecaches) {
    mDelegate.setUseCaches(usecaches);
  }

  @Override
  public String toString() {
    return mDelegate.toString();
  }

  @Override
  public boolean usingProxy() {
    return mDelegate.usingProxy();
  }
}
